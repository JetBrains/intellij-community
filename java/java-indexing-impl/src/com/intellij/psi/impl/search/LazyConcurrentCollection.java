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

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressManager;
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
import java.util.concurrent.atomic.AtomicBoolean;

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
  @NotNull private final Function<? super T, ? extends V> myAnchorToValueConvertor;
  @NotNull private final MoreElementsGenerator<? extends T, ? super V> myGenerator;
  @NotNull private final Predicate<? super V> myApplicableForGenerationFilter;
  private final Semaphore currentlyProcessingClasses = new Semaphore();

  private final HashSetQueue.PositionalIterator<T> candidatesToFindSubclassesIterator; // guarded by lock
  // classes for which DirectClassInheritorsSearch is running
  private final Set<T> classesBeingProcessed = new THashSet<>(); // guarded by lock
  // Classes for which DirectClassInheritorsSearch has already run (maybe in the other thread),
  // but candidatesToFindSubclassesIterator hasn't caught them up yet. Elements from this set are removed as the iterator moves.
  private final Set<T> classesProcessed = new THashSet<>(); // guarded by lock

  LazyConcurrentCollection(@NotNull T seedElement,
                           @NotNull Function<? super T, ? extends V> convertor,
                           @NotNull Predicate<? super V> applicableForGenerationFilter,
                           @NotNull MoreElementsGenerator<? extends T, ? super V> generator) {
    subClasses = new HashSetQueue<>();
    subClasses.add(seedElement);
    myAnchorToValueConvertor = convertor;
    myGenerator = generator;
    myApplicableForGenerationFilter = applicableForGenerationFilter;
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
        return myAnchorToValueConvertor.fun(next);
      }
    };
  }

  // polls 'subClasses' for more sub classes and call generator.generateMoreElementsFor() on them
  // adds found classes to "subClasses" queue
  // returns as soon as something was added
  private void processMoreSubclasses(@NotNull Iterator<T> subClassIterator) {
    while (true) {
      ProgressManager.checkCanceled();

      Pair.NonNull<T,V> pair =
        ReadAction.compute(() -> {
          ProgressManager.checkCanceled();
          synchronized (lock) {
            // Find the classes in subClasses collection to operate on
            // (without advancing the candidatesToFindSubclassesIterator iterator - it will be moved after the class successfully handled - to protect against PCE, INRE, etc)
            // The found class will be marked as being analyzed - placed in classesBeingProcessed collection
            HashSetQueue.PositionalIterator.IteratorPosition<T> startPosition = candidatesToFindSubclassesIterator.position().next();
            Pair.NonNull<T,V> next = startPosition == null ? null : findNextClassInQueue(startPosition);
            if (next != null) {
              currentlyProcessingClasses.down();
              classesBeingProcessed.add(next.getFirst());
            }
            return next;
          }
        });
      if (pair == null) {
        // no candidates left in queue, exit
        // but first, wait for other threads to process their candidates
        synchronized (lock) {
          advanceIteratorOnSuccess(); // to skip unsuitable classes like final etc from the queue
          if (subClassIterator.hasNext()) {
            return;
          }
        }

        boolean producedSomething = waitForOtherThreadsToFinishProcessing(subClassIterator);
        if (producedSomething) {
          return;
        }

        // aaaaaaaa! Other threads were unable to produce anything. That can be because:
        // - the whole queue has been processed. => exit, return false
        // - the other thread has been interrupted. => check the queue again to pickup the work it dropped.
        synchronized (lock) {
          advanceIteratorOnSuccess(); // to skip unsuitable classes like final etc from the queue
          if (!candidatesToFindSubclassesIterator.hasNext()) {
            return;
          }
        }

        continue; // check again
      }

      V candidate = pair.getSecond();
      T anchor = pair.getFirst();
      try {
        myGenerator.generateMoreElementsFor(candidate, generatedElement -> {
          ProgressManager.checkCanceled();
          synchronized (lock) {
            subClasses.add(generatedElement);
          }
        });
        synchronized (lock) {
          classesProcessed.add(anchor);
          advanceIteratorOnSuccess();
          if (subClassIterator.hasNext()) {
            // we've added something to subClasses so we can return and the iterator can move forward at least once;
            // more elements will be added on the subsequent call to .next()
            return;
          }
        }
      }
      finally {
        synchronized (lock) {
          classesBeingProcessed.remove(anchor);
          currentlyProcessingClasses.up();
        }
      }
    }
  }

  private boolean waitForOtherThreadsToFinishProcessing(@NotNull final Iterator<T> subClassIterator) {
    // Found nothing, have to wait for other threads because:
    // The first thread comes and takes a class off the queue to search for inheritors,
    // the second thread comes and sees there is no classes in the queue.
    // The second thread should not return nothing, it should wait for the first thread to finish.
    //
    // Wait within managedBlock to signal FJP this thread is locked (to avoid thread starvation and deadlocks)
    AtomicBoolean hasNext = new AtomicBoolean();
    try {
      ForkJoinPool.managedBlock(new ForkJoinPool.ManagedBlocker() {
        @Override
        public boolean block() {
          while (!currentlyProcessingClasses.isUp()) {
            ProgressManager.checkCanceled();
            currentlyProcessingClasses.waitFor(1); // wait until other threads process their classes before giving up
          }
          return isReleasable();
        }

        @Override
        public boolean isReleasable() {
          synchronized (lock) {
            // other thread produced something or all of them reached the end of list
            boolean producedSomething = subClassIterator.hasNext();
            hasNext.set(producedSomething); // store the result to avoid locking again after exit
            return producedSomething || !candidatesToFindSubclassesIterator.hasNext() || classesBeingProcessed.isEmpty();
          }
        }
      });
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    return hasNext.get();
  }

  // under lock
  private Pair.NonNull<T,V> findNextClassInQueue(@NotNull HashSetQueue.PositionalIterator.IteratorPosition<? extends T> position) {
    // find the first class suitable for analyzing inheritors of (not anonymous and not final and retrievable from PsiAnchor) and not already processed or being processed (by other thread)
    // couldn't call iterator.next() until class is processed, so use position.peek()/position.next() which don't advance iterator
    while (position != null) {
      ProgressManager.checkCanceled();
      T anchor = position.peek();
      if (!classesProcessed.contains(anchor) && !classesBeingProcessed.contains(anchor)) {
        V value = myAnchorToValueConvertor.fun(anchor);
        boolean isAccepted = value != null && myApplicableForGenerationFilter.apply(value);
        if (isAccepted) {
          return Pair.createNonNull(anchor, value);
        }
        classesProcessed.add(anchor);
      }
      // the candidate is already being processed in the other thread, try the next one (not advancing iterator!)
      position = position.next();
    }
    return null;
  }

  // under lock
  private void advanceIteratorOnSuccess() {
    while (candidatesToFindSubclassesIterator.hasNext()) {
      ProgressManager.checkCanceled();
      T next = candidatesToFindSubclassesIterator.position().next().peek();
      boolean removed = classesProcessed.remove(next);
      if (removed) {
        candidatesToFindSubclassesIterator.next();
      }
      else {
        break;
      }
    }
  }
}
