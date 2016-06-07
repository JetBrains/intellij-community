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
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Processor;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.HashSetQueue;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ForkJoinPool;

public class JavaClassInheritorsSearcher extends QueryExecutorBase<PsiClass, ClassInheritorsSearch.SearchParameters> {
  @Override
  public void processQuery(@NotNull ClassInheritorsSearch.SearchParameters parameters, @NotNull Processor<PsiClass> consumer) {
    final PsiClass baseClass = parameters.getClassToProcess();
    assert parameters.isCheckDeep();
    assert parameters.isCheckInheritance();

    ProgressIndicator progress = ProgressIndicatorProvider.getGlobalProgressIndicator();
    if (progress != null) {
      progress.pushState();
      String className = ApplicationManager.getApplication().runReadAction((Computable<String>)baseClass::getName);
      progress.setText(className != null ?
                       PsiBundle.message("psi.search.inheritors.of.class.progress", className) :
                       PsiBundle.message("psi.search.inheritors.progress"));
    }

    try {
      processInheritors(parameters, consumer);
    }
    finally {
      if (progress != null) {
        progress.popState();
      }
    }
  }

  private static boolean processInheritors(@NotNull final ClassInheritorsSearch.SearchParameters parameters,
                                           @NotNull final Processor<PsiClass> consumer) {
    @NotNull final PsiClass baseClass = parameters.getClassToProcess();
    if (baseClass instanceof PsiAnonymousClass || isFinal(baseClass)) return true;

    final SearchScope searchScope = parameters.getScope();
    Project project = PsiUtilCore.getProjectInReadAction(baseClass);
    if (isJavaLangObject(baseClass)) {
      return AllClassesSearch.search(searchScope, project, parameters.getNameCondition()).forEach(aClass -> {
        ProgressManager.checkCanceled();
        return isJavaLangObject(aClass) || consumer.process(aClass);
      });
    }
    if (searchScope instanceof LocalSearchScope) {
      return processLocalScope(project, parameters, (LocalSearchScope)searchScope, baseClass, consumer);
    }

    Iterable<PsiClass> cached = getOrComputeSubClasses(project, baseClass);

    for (final PsiClass subClass : cached) {
      ProgressManager.checkCanceled();
      if (subClass instanceof PsiAnonymousClass && !parameters.isIncludeAnonymous()) {
        continue;
      }
      if (ApplicationManager.getApplication().runReadAction((Computable<Boolean>)() ->
        checkCandidate(subClass, parameters) && !consumer.process(subClass))) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  private static Iterable<PsiClass> getOrComputeSubClasses(@NotNull Project project, @NotNull PsiClass baseClass) {
    ConcurrentMap<PsiClass, Iterable<PsiClass>> map = HighlightingCaches.getInstance(project).ALL_SUB_CLASSES;
    Iterable<PsiClass> cached = map.get(baseClass);
    if (cached == null) {
      // returns lazy collection of subclasses. Each call to next() leads to calculation of next batch of subclasses.
      Iterable<PsiClass> computed = new AllSubClassesLazyCollection(project, baseClass); // it's empty now, no big deal
      // for non-physical elements ignore the cache completely because non-physical elements created so often/unpredictably so I can't figure out when to clear caches in this case
      boolean isPhysical = ApplicationManager.getApplication().runReadAction((Computable<Boolean>)baseClass::isPhysical);
      // make sure concurrent calls of this method always return the same collection to avoid expensive duplicate work
      cached = isPhysical ? ConcurrencyUtil.cacheOrGet(map, baseClass, computed) : computed;
    }
    return cached;
  }

  private static boolean processLocalScope(@NotNull final Project project,
                                           @NotNull final ClassInheritorsSearch.SearchParameters parameters,
                                           @NotNull LocalSearchScope searchScope,
                                           @NotNull PsiClass baseClass,
                                           @NotNull Processor<PsiClass> consumer) {
    // optimisation: in case of local scope it's considered cheaper to enumerate all scope files and check if there is an inheritor there,
    // instead of traversing the (potentially huge) class hierarchy and filter out almost everything by scope.
    VirtualFile[] virtualFiles = searchScope.getVirtualFiles();

    final boolean[] success = {true};
    for (VirtualFile virtualFile : virtualFiles) {
      ProgressManager.checkCanceled();
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
          if (psiFile != null) {
            psiFile.accept(new JavaRecursiveElementVisitor() {
              @Override
              public void visitClass(PsiClass candidate) {
                ProgressManager.checkCanceled();
                if (!success[0]) return;
                if (candidate.isInheritor(baseClass, true)
                    && checkCandidate(candidate, parameters)
                    && !consumer.process(candidate)) {
                  success[0] = false;
                  return;
                }
                super.visitClass(candidate);
              }

              @Override
              public void visitCodeBlock(PsiCodeBlock block) {
                ProgressManager.checkCanceled();
                if (!parameters.isIncludeAnonymous()) return;
                super.visitCodeBlock(block);
              }
            });
          }
        }
      });
    }
    return success[0];
  }

  private static boolean checkCandidate(@NotNull PsiClass candidate, @NotNull ClassInheritorsSearch.SearchParameters parameters) {
    SearchScope searchScope = parameters.getScope();
    ProgressManager.checkCanceled();

    if (!PsiSearchScopeUtil.isInScope(searchScope, candidate)) {
      return false;
    }
    if (candidate instanceof PsiAnonymousClass) {
      return true;
    }

    String name = candidate.getName();
    return name != null && parameters.getNameCondition().value(name);
  }

  static boolean isJavaLangObject(@NotNull final PsiClass baseClass) {
    return ApplicationManager.getApplication().runReadAction(
      (Computable<Boolean>)() -> baseClass.isValid() && CommonClassNames.JAVA_LANG_OBJECT.equals(baseClass.getQualifiedName()));
  }

  private static boolean isFinal(@NotNull final PsiClass baseClass) {
    return ApplicationManager.getApplication().runReadAction((Computable<Boolean>)() -> baseClass.hasModifierProperty(PsiModifier.FINAL));
  }

  private static class AllSubClassesLazyCollection implements Iterable<PsiClass> {
    // Computes all sub classes of the 'baseClass' transitively by calling DirectClassInheritorsSearch repeatedly.
    // Already computed subclasses are stored in this collection.
    // There are two iterators maintained for this collection:
    // - 'candidatesToFindSubclassesIterator' points to the next element for which direct inheritors haven't been searched yet.
    // - 'subClassIterator' created in AllSubClassesLazyCollection.iterator() maintains state of the AllSubClassesLazyCollection iterator in a lazy fashion.
    //    If more elements requested for this iterator, the processMoreSubclasses() is called which tries to populate 'subClasses' with more inheritors.
    private final HashSetQueue<PsiAnchor> subClasses = new HashSetQueue<>(); // guarded by lock
    private final Object lock = new Object(); // MUST NOT acquire read action inside this lock
    private final GlobalSearchScope projectScope;
    private final Semaphore currentlyProcessingClasses = new Semaphore();

    private final HashSetQueue.PositionalIterator<PsiAnchor> candidatesToFindSubclassesIterator = subClasses.iterator(); // guarded by lock

    AllSubClassesLazyCollection(@NotNull Project project, @NotNull PsiClass baseClass) {
      projectScope = GlobalSearchScope.allScope(project);
      subClasses.add(ApplicationManager.getApplication().runReadAction((Computable<PsiAnchor>)() -> PsiAnchor.create(baseClass)));
    }

    @NotNull
    @Override
    public Iterator<PsiClass> iterator() {
      return new Iterator<PsiClass>() {
        private final Iterator<PsiAnchor> subClassIterator = subClasses.iterator(); // guarded by lock
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
        public PsiClass next() {
          PsiAnchor next;
          synchronized (lock) {
            next = subClassIterator.next();
          }
          return ApplicationManager.getApplication().runReadAction((Computable<PsiClass>)() -> (PsiClass)next.retrieve());
        }
      };
    }

    private PsiClass findNextClassInQueue(@NotNull HashSetQueue.PositionalIterator.IteratorPosition<PsiAnchor> position) {
      // find the first class which is fit (not anonymous and not final and retrievable from PsiAnchor) and not already processed (flag PROCESSING_SUBCLASSES_STATUS in class user data)
      PsiClass candidate = null;
      boolean foundClassBeingProcessed = false;
      // couldn't call iterator.next() until class is processed, so use position.peek()/position.next() which don't advance iterator
      while (position != null) {
        ProgressManager.checkCanceled();
        PsiAnchor anchor = position.peek();
        candidate = (PsiClass)anchor.retrieve();
        if (candidate instanceof PsiAnonymousClass || candidate != null && candidate.hasModifierProperty(PsiModifier.FINAL)) {
          candidate = null;
        }

        if (candidate != null) {
          ClassProcessingStatus status = candidate.getUserData(PROCESSING_SUBCLASSES_STATUS);
          if (status == null) {
            candidate.putUserData(PROCESSING_SUBCLASSES_STATUS, ClassProcessingStatus.PROCESSING_SUBCLASSES);
            break;
          }
          foundClassBeingProcessed |= status == ClassProcessingStatus.PROCESSING_SUBCLASSES;
        }
        if (!foundClassBeingProcessed) {
          candidatesToFindSubclassesIterator.next(); // this class and all previous are either unfit (anonymous or final or un-retrievable) or already processed, skip iterator to help other threads
          if (candidate != null) {
            candidate.putUserData(PROCESSING_SUBCLASSES_STATUS, null); // this flag isn't needed anymore, free some memory
          }
        }
        // the candidate is already being processed in the other thread, try the next one (not advancing iterator!)
        candidate = null;
        position = position.next();
      }
      return candidate;
    }

    enum ClassProcessingStatus {
      PROCESSING_SUBCLASSES, PROCESSING_FINISHED
    }
    private static final Key<ClassProcessingStatus> PROCESSING_SUBCLASSES_STATUS = Key.create("PROCESSING_SUBCLASSES_STATUS");

    // polls 'subClasses' for more sub classes and call DirectClassInheritorsSearch for them
    private void processMoreSubclasses(@NotNull Iterator<PsiAnchor> subClassIterator) {
      while (true) {
        ProgressManager.checkCanceled();

        PsiClass candidate = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
          @Override
          public PsiClass compute() {
            synchronized (lock) {
              // Find the classes in subClasses collection to operate on
              // (without advancing the candidatesToFindSubclassesIterator iterator - it will be moved after the class successfully handled - to protect against PCE, INRE, etc)
              // The found class will be marked as being analyzed - with PROCESSING_SUBCLASSES_STATUS flag in its user data
              HashSetQueue.PositionalIterator.IteratorPosition<PsiAnchor> startPosition = candidatesToFindSubclassesIterator.position().next();
              PsiClass candidate = startPosition == null ? null : findNextClassInQueue(startPosition);
              if (candidate != null) {
                currentlyProcessingClasses.down();
              }
              return candidate;
            }
          }
        });
        if (candidate == null) {
          // no candidates left in queue, exit
          // but first, wait for other threads to process their candidates
          break;
        }

        try {
          DirectClassInheritorsSearch.search(candidate, projectScope).forEach(subClass -> {
            ProgressManager.checkCanceled();
            PsiAnchor pointer = PsiAnchor.create(subClass);
            // append found result to subClasses as early as possible to allow other waiting threads to continue
            synchronized (lock) {
              subClasses.add(pointer);
            }
            return true;
          });
        }
        finally {
          candidate.putUserData(PROCESSING_SUBCLASSES_STATUS, ClassProcessingStatus.PROCESSING_FINISHED);
          currentlyProcessingClasses.up();
        }

        synchronized (lock) {
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
  }
}
