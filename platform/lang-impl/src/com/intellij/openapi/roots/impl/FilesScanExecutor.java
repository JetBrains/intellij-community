// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.model.ModelBranchImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.impl.VirtualFileEnumeration;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Processor;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ConcurrentBitSet;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexEx;
import com.intellij.util.indexing.UnindexedFilesUpdater;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin;
import com.intellij.util.indexing.roots.kind.LibraryOrigin;
import com.intellij.util.indexing.roots.kind.SdkOrigin;
import com.intellij.util.indexing.roots.kind.SyntheticLibraryOrigin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@ApiStatus.Internal
public final class FilesScanExecutor {
  private static final int THREAD_COUNT = Math.max(UnindexedFilesUpdater.getNumberOfScanningThreads() - 1, 1);
  private static final ExecutorService ourExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Scanning", THREAD_COUNT);

  private static class StopWorker extends ProcessCanceledException { }

  public static void runOnAllThreads(@NotNull Runnable runnable) {
    ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    List<Future<?>> results = new ArrayList<>();
    for (int i = 0; i < THREAD_COUNT; i++) {
      results.add(ourExecutor.submit(() -> {
        ProgressManager.getInstance().runProcess(runnable, ProgressWrapper.wrap(progress));
      }));
    }
    // put the current thread to work too so the total thread count is `getNumberOfScanningThreads`
    // and avoid thread starvation due to a recursive `runOnAllThreads` invocation
    runnable.run();
    for (Future<?> result : results) {
      // complete the future to avoid waiting for it forever if `ourExecutor` is fully booked
      ((FutureTask<?>)result).run();
      ProgressIndicatorUtils.awaitWithCheckCanceled(result);
    }
  }

  public static <T> boolean processDequeOnAllThreads(@NotNull ConcurrentLinkedDeque<T> deque,
                                                     @NotNull Processor<? super T> processor) {
    ProgressManager.checkCanceled();
    if (deque.isEmpty()) return true;
    AtomicInteger runnersCount = new AtomicInteger();
    AtomicInteger idleCount = new AtomicInteger();
    AtomicReference<Throwable> error = new AtomicReference<>();
    AtomicBoolean stopped = new AtomicBoolean();
    AtomicBoolean exited = new AtomicBoolean();
    runOnAllThreads(() -> {
      runnersCount.incrementAndGet();
      boolean idle = false;
      while (!stopped.get()) {
        ProgressManager.checkCanceled();
        if (deque.peek() == null) {
          if (!idle) {
            idle = true;
            idleCount.incrementAndGet();
          }
        }
        else if (idle) {
          idle = false;
          idleCount.decrementAndGet();
        }
        if (idle) {
          if (idleCount.get() == runnersCount.get()) break;
          TimeoutUtil.sleep(1L);
          continue;
        }

        T item = deque.poll();
        if (item == null) continue;

        try {
          if (!processor.process(item)) {
            stopped.set(true);
          }
          if (exited.get() && !stopped.get()) {
            throw new AssertionError("early exit");
          }
        }
        catch (StopWorker ex) {
          deque.addFirst(item);
          runnersCount.decrementAndGet();
          return;
        }
        catch (ProcessCanceledException ex) {
          deque.addFirst(item);
        }
        catch (Throwable ex) {
          error.compareAndSet(null, ex);
        }
      }
      exited.set(true);
      if (!deque.isEmpty() && !stopped.get()) {
        throw new AssertionError("early exit");
      }
    });
    ExceptionUtil.rethrowAllAsUnchecked(error.get());
    return !stopped.get();
  }

  private static boolean isLibOrigin(@NotNull IndexableSetOrigin origin) {
    return origin instanceof LibraryOrigin ||
           origin instanceof SyntheticLibraryOrigin ||
           origin instanceof SdkOrigin;
  }

  public static boolean processFilesInScope(@NotNull GlobalSearchScope scope,
                                            boolean includingBinary,
                                            @NotNull Processor<? super VirtualFile> processor) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    Project project = scope.getProject();
    if (project == null) return true;
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    boolean searchInLibs = scope.isSearchInLibraries();

    ConcurrentLinkedDeque<Object> deque = new ConcurrentLinkedDeque<>();
    ModelBranchImpl.processModifiedFilesInScope(scope, deque::add);
    if (scope instanceof VirtualFileEnumeration) {
      ContainerUtil.addAll(deque, ((VirtualFileEnumeration)scope).asIterable());
    }
    else {
      deque.addAll(((FileBasedIndexEx)FileBasedIndex.getInstance()).getIndexableFilesProviders(project));
    }
    ConcurrentBitSet visitedFiles = ConcurrentBitSet.create();
    Processor<Object> consumer = obj -> {
      ProgressManager.checkCanceled();
      if (obj instanceof IndexableFilesIterator) {
        IndexableSetOrigin origin = ((IndexableFilesIterator)obj).getOrigin();
        if (!searchInLibs && isLibOrigin(origin)) return true;
        ((IndexableFilesIterator)obj).iterateFiles(project, file -> {
          if (file.isDirectory()) return true;
          deque.add(file);
          return true;
        }, VirtualFileFilter.ALL);
      }
      else if (obj instanceof VirtualFile) {
        VirtualFile file = (VirtualFile)obj;
        if (file instanceof VirtualFileWithId && visitedFiles.set(((VirtualFileWithId)file).getId())) {
          return true;
        }
        if (!file.isValid() ||
            fileIndex.isExcluded(file) ||
            ((VirtualFile)obj).isDirectory() ||
            !scope.contains(file) ||
            !includingBinary && file.getFileType().isBinary()) {
          return true;
        }

        return processor.process(file);
      }
      else {
        throw new AssertionError("unknown item: " + obj);
      }
      return true;
    };
    ApplicationEx application = (ApplicationEx)ApplicationManager.getApplication();
    return processDequeOnAllThreads(deque, o -> {
      if (application.isReadAccessAllowed()) {
        return consumer.process(o);
      }
      Ref<Boolean> result = Ref.create(true);
      if (!application.tryRunReadAction(
        () -> result.set(consumer.process(o)))) {
        throw new StopWorker();
      }
      return result.get();
    });
  }
}
