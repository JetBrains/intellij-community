/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.HashSetQueue;
import com.intellij.util.containers.Predicate;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;

/**
 * Collection of elements of type V which is
 * - lazy (computes its elements on demand, when the corresponding .iterator().next() method is called)
 * - thread safe (multiple threads can iterate this collection concurrently. Already computed elements are shared between these threads. If more elements need to be computed, these computations happen concurrently; different threads can process different elements helping each other)
 * - uses some other type T internally to store already computed elements, e.g. for reducing memory.
 *
 * When more elements needed, this collection iterates not yet processed elements, calls applicable filter on each, calls generator on applicable elements,
 * adds generated elements back to the collection.
 * Clients must provide:
 *   -- convertor T->V
 *   -- filter on V to use to find the applicable elements in the collection to compute more elements
 *   -- generator which for applicable element:V produces more elements:T
 */
class LazyConcurrentCollection<T,V> implements Iterable<V> {
  // Computes all sub classes of the 'baseClass' transitively by calling DirectClassInheritorsSearch repeatedly.
  // Already computed subclasses are stored in this collection.
  // There are two iterators maintained for this collection:
  // - 'candidatesToFindSubclassesIterator' points to the next element for which direct inheritors haven't been searched yet.
  // - 'subClassIterator' created in AllSubClassesLazyCollection.iterator() maintains state of the AllSubClassesLazyCollection iterator in a lazy fashion.
  //    If more elements requested for this iterator, the processMoreSubclasses() is called which tries to populate 'subClasses' with more inheritors.
  private final HashSetQueue<T> subClasses; // guarded by lock
  private final Object lock = new Object(); // MUST NOT acquire read action inside this lock
  @NotNull private final Function<T, V> myConvertor;
  @NotNull private final MoreElementsGenerator<T,V> myGenerator;
  @NotNull private final Predicate<? super V> myApplicableFilter;
  private final Semaphore currentlyProcessingClasses = new Semaphore();

  private final HashSetQueue.PositionalIterator<T> candidatesToFindSubclassesIterator; // guarded by lock
  // classes for which DirectClassInheritorsSearch is running
  private final Set<T> classesBeingProcessed = new THashSet<>(); // guarded by lock
  // Classes for which DirectClassInheritorsSearch has already run (maybe in the other thread),
  // but candidatesToFindSubclassesIterator hasn't caught them up yet. Elements from this set are removed as the iterator moves.
  private final Set<T> classesProcessed = new THashSet<>(); // guarded by lock

  LazyConcurrentCollection(@NotNull T seedElement,
                           @NotNull Function<T, V> convertor,
                           @NotNull Predicate<? super V> applicableFilter,
                           @NotNull MoreElementsGenerator<T, V> generator) {
    subClasses = new HashSetQueue<>();
    subClasses.add(seedElement);
    myConvertor = convertor;
    myGenerator = generator;
    myApplicableFilter = applicableFilter;
    candidatesToFindSubclassesIterator = subClasses.iterator();
  }

  @FunctionalInterface
  interface MoreElementsGenerator<T,V> {
    void generateMoreElementsFor(@NotNull V element, @NotNull Consumer<T> processor);
  }

  @NotNull
  @Override
  public Iterator<V> iterator() {
    return new Iterator<V>() {
      private final Iterator<T> subClassIterator = subClasses.iterator(); // guarded by lock
      {
        synchronized (lock) {
          subClassIterator.next(); //skip the baseClass which stored in the subClasses first element
        }
      }
      @Override
      public boolean hasNext() {
        synchronized (lock) {
          if (subClassIterator.hasNext()) return true;
        }

        processMoreSubclasses(subClassIterator);

        synchronized (lock) {
          return subClassIterator.hasNext();
        }
      }

      @Override
      public V next() {
        T next;
        synchronized (lock) {
          next = subClassIterator.next();
        }
        return myConvertor.fun(next);
      }
    };
  }

  private Pair.NonNull<T,V> findNextClassInQueue(@NotNull HashSetQueue.PositionalIterator.IteratorPosition<T> position) {
    // find the first class which is fit (not anonymous and not final and retrievable from PsiAnchor) and not already processed (flag PROCESSING_SUBCLASSES_STATUS in class user data)
    // couldn't call iterator.next() until class is processed, so use position.peek()/position.next() which don't advance iterator
    while (position != null) {
      ProgressManager.checkCanceled();
      T anchor = position.peek();
      V value = myConvertor.fun(anchor);
      boolean isAccepted = value != null && myApplicableFilter.apply(value);

      if (isAccepted && !classesProcessed.contains(anchor) && classesBeingProcessed.add(anchor)) {
        return Pair.createNonNull(anchor, value);
      }
      // the candidate is already being processed in the other thread, try the next one (not advancing iterator!)
      position = position.next();
    }
    return null;
  }

  // polls 'subClasses' for more sub classes and call DirectClassInheritorsSearch for them
  private void processMoreSubclasses(@NotNull Iterator<T> subClassIterator) {
    while (true) {
      ProgressManager.checkCanceled();

      Pair.NonNull<T,V> pair =
        ApplicationManager.getApplication().runReadAction(new Computable<Pair.NonNull<T,V>>() {
          @Override
          public Pair.NonNull<T,V> compute() {
            synchronized (lock) {
              // Find the classes in subClasses collection to operate on
              // (without advancing the candidatesToFindSubclassesIterator iterator - it will be moved after the class successfully handled - to protect against PCE, INRE, etc)
              // The found class will be marked as being analyzed - placed in classesBeingProcessed collection
              HashSetQueue.PositionalIterator.IteratorPosition<T> startPosition = candidatesToFindSubclassesIterator.position().next();
              Pair.NonNull<T,V> pair = startPosition == null ? null : findNextClassInQueue(startPosition);
              if (pair != null) {
                currentlyProcessingClasses.down();
              }
              return pair;
            }
          }
        });
      if (pair == null) {
        // no candidates left in queue, exit
        // but first, wait for other threads to process their candidates
        break;
      }

      V candidate = pair.getSecond();
      T anchor = pair.getFirst();
      try {
        myGenerator.generateMoreElementsFor(candidate, generatedElement -> {
          synchronized (lock) {
            subClasses.add(generatedElement);
          }
        });
      }
      finally {
        currentlyProcessingClasses.up();
      }

      synchronized (lock) {
        classesBeingProcessed.remove(anchor);
        classesProcessed.add(anchor);
        advanceIteratorOnSuccess();
        if (subClassIterator.hasNext()) {
          // we've added something to subClasses so we can return and the iterator can move forward at least once;
          // more elements will be added on the subsequent call to .next()
          return;
        }
      }
    }

    // Found nothing, have to wait for other threads because:
    // The first thread comes and takes a class off the queue to search for inheritors,
    // the second thread comes and sees there is no classes in the queue.
    // The second thread should not return nothing, it should wait for the first thread to finish.
    //
    // Wait within managedBlock to signal FJP this thread is locked (to avoid thread starvation and deadlocks)
    try {
      ForkJoinPool.managedBlock(new ForkJoinPool.ManagedBlocker() {
        @Override
        public boolean block() throws InterruptedException {
          currentlyProcessingClasses.waitFor(); // wait until other threads process their classes before giving up
          return isReleasable();
        }

        @Override
        public boolean isReleasable() {
          synchronized (lock) {
            return !currentlyProcessingClasses.isDown() || subClassIterator.hasNext();
          }
        }
      });
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private void advanceIteratorOnSuccess() {
    HashSetQueue.PositionalIterator.IteratorPosition<T> position = candidatesToFindSubclassesIterator.position().next();
    while (position != null) {
      T next = position.peek();
      if (classesProcessed.contains(next)) {
        candidatesToFindSubclassesIterator.next();
        classesProcessed.remove(next);
      }
      else {
        break;
      }
      position = position.next();
    }
  }
}
