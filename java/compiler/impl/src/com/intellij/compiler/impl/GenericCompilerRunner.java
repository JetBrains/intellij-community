/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.compiler.impl;

import com.intellij.compiler.impl.generic.GenericCompilerCache;
import com.intellij.compiler.impl.generic.GenericCompilerPersistentData;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.RunResult;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.compiler.generic.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.io.KeyDescriptor;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author nik
 */
public class GenericCompilerRunner {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.GenericCompilerRunner");
  private static final Logger FULL_LOG = Logger.getInstance("#com.intellij.full-generic-compiler-log");
  private CompileContext myContext;
  private final boolean myForceCompile;
  private final boolean myOnlyCheckStatus;
  private final GenericCompiler<?,?,?>[] myCompilers;
  private final Project myProject;

  public GenericCompilerRunner(CompileContext context,
                               boolean forceCompile,
                               boolean onlyCheckStatus, final GenericCompiler[] compilers) {
    myContext = context;
    myForceCompile = forceCompile;
    myOnlyCheckStatus = onlyCheckStatus;
    myCompilers = compilers;
    myProject = myContext.getProject();
  }

  public boolean invokeCompilers(GenericCompiler.CompileOrderPlace place) throws ExitException {
    boolean didSomething = false;
    try {
      for (GenericCompiler<?,?,?> compiler : myCompilers) {
        if (compiler.getOrderPlace().equals(place)) {
          didSomething |= invokeCompiler(compiler);
        }
      }
    }
    catch (IOException e) {
      LOG.info(e);
      myContext.requestRebuildNextTime(e.getMessage());
      throw new ExitException(ExitStatus.ERRORS);
    }
    catch (ExitException e) {
      throw e;
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.info(e);
      myContext.addMessage(CompilerMessageCategory.ERROR, CompilerBundle.message("compiler.error.exception", e.getMessage()), null, -1, -1);
    }
    return didSomething;
  }

  private <Key, SourceState, OutputState> boolean invokeCompiler(GenericCompiler<Key, SourceState, OutputState> compiler) throws IOException,
                                                                                                                                 ExitException {
    return invokeCompiler(compiler, compiler.createInstance(myContext));
  }

  private <T extends BuildTarget, Item extends CompileItem<Key, SourceState, OutputState>, Key, SourceState, OutputState>
  boolean invokeCompiler(GenericCompiler<Key, SourceState, OutputState> compiler, final GenericCompilerInstance<T, Item, Key, SourceState, OutputState> instance) throws IOException,
                                                                                                                                                                         ExitException {
    final GenericCompilerCache<Key, SourceState, OutputState> cache = CompilerCacheManager.getInstance(myProject).getGenericCompilerCache(compiler);
    GenericCompilerPersistentData
      data = new GenericCompilerPersistentData(getGenericCompilerCacheDir(myProject, compiler), compiler.getVersion());
    if (data.isVersionChanged()) {
      LOG.info("Clearing cache for " + compiler.getDescription());
      cache.wipe();
      data.save();
    }

    final Set<String> targetsToRemove = new HashSet<>(data.getAllTargets());
    new ReadAction() {
      protected void run(@NotNull final Result result) {
        for (T target : instance.getAllTargets()) {
          targetsToRemove.remove(target.getId());
        }
      }
    }.execute();

    if (!myOnlyCheckStatus) {
      for (final String target : targetsToRemove) {
        final int id = data.removeId(target);
        if (LOG.isDebugEnabled()) {
          LOG.debug("Removing obsolete target '" + target + "' (id=" + id + ")");
        }

        final List<Key> keys = new ArrayList<>();
        CompilerUtil.runInContext(myContext, "Processing obsolete targets...", () -> {
          cache.processSources(id, new CommonProcessors.CollectProcessor<>(keys));
          List<GenericCompilerCacheState<Key, SourceState, OutputState>> obsoleteSources = new ArrayList<>();
          for (Key key : keys) {
            final GenericCompilerCache.PersistentStateData<SourceState, OutputState> state = cache.getState(id, key);
            obsoleteSources.add(new GenericCompilerCacheState<>(key, state.mySourceState, state.myOutputState));
          }
          instance.processObsoleteTarget(target, obsoleteSources);
        });
        checkForErrorsOrCanceled();
        for (Key key : keys) {
          cache.remove(id, key);
        }
      }
    }

    final List<T> selectedTargets = new ReadAction<List<T>>() {
      protected void run(final Result<List<T>> result) {
        result.setResult(instance.getSelectedTargets());
      }
    }.execute().getResultObject();

    boolean didSomething = false;
    for (T target : selectedTargets) {
      int id = data.getId(target.getId());
      didSomething |= processTarget(target, id, compiler, instance, cache);
    }

    data.save();
    return didSomething;
  }

  private void checkForErrorsOrCanceled() throws ExitException {
    if (myContext.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
      throw new ExitException(ExitStatus.ERRORS);
    }
    if (myContext.getProgressIndicator().isCanceled()) {
      throw new ExitException(ExitStatus.CANCELLED);
    }
  }

  public static File getGenericCompilerCacheDir(Project project, GenericCompiler<?,?,?> compiler) {
    return new File(CompilerPaths.getCacheStoreDirectory(project), compiler.getId());
  }

  private <T extends BuildTarget, Item extends CompileItem<Key, SourceState, OutputState>, Key, SourceState, OutputState>
  boolean processTarget(T target, final int targetId, final GenericCompiler<Key, SourceState, OutputState> compiler, final GenericCompilerInstance<T, Item, Key, SourceState, OutputState> instance,
                        final GenericCompilerCache<Key, SourceState, OutputState> cache) throws IOException, ExitException {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Processing target '" + target + "' (id=" + targetId + ") by " + compiler);
    }
    final List<Item> items = instance.getItems(target);
    checkForErrorsOrCanceled();

    final List<GenericCompilerProcessingItem<Item, SourceState, OutputState>> toProcess = new ArrayList<>();
    final THashSet<Key> keySet = new THashSet<>(new SourceItemHashingStrategy<>(compiler));
    final Ref<IOException> exception = Ref.create(null);
    final Map<Item, SourceState> sourceStates = new HashMap<>();
    DumbService.getInstance(myProject).runReadActionInSmartMode(() -> {
      try {
        for (Item item : items) {
          final Key key = item.getKey();
          keySet.add(key);
          if (item.isExcluded()) continue;

          final GenericCompilerCache.PersistentStateData<SourceState, OutputState> data = cache.getState(targetId, key);
          SourceState sourceState = data != null ? data.mySourceState : null;
          final OutputState outputState = data != null ? data.myOutputState : null;
          if (myForceCompile || sourceState == null || !item.isSourceUpToDate(sourceState)
                             || outputState == null || !item.isOutputUpToDate(outputState)) {
            sourceStates.put(item, item.computeSourceState());
            toProcess.add(new GenericCompilerProcessingItem<>(item, sourceState, outputState));
          }
        }
      }
      catch (IOException e) {
        exception.set(e);
      }
    });
    if (!exception.isNull()) {
      throw exception.get();
    }

    final List<Key> toRemove = new ArrayList<>();
    cache.processSources(targetId, key -> {
      if (!keySet.contains(key)) {
        toRemove.add(key);
      }
      return true;
    });

    if (LOG.isDebugEnabled()) {
      LOG.debug(toProcess.size() + " items will be processed, " + toRemove.size() + " items will be removed");
      for (int i = 0; i < getItemsCountToShowInLog(toProcess.size()); i++) {
        LOG.debug("to process:" + toProcess.get(i).getItem().getKey());
      }
      for (int i = 0; i < getItemsCountToShowInLog(toRemove.size()); i++) {
        LOG.debug("to delete:" + toRemove.get(i));
      }
    }

    if (toProcess.isEmpty() && toRemove.isEmpty()) {
      return false;
    }

    if (myOnlyCheckStatus) {
      throw new ExitException(ExitStatus.CANCELLED);
    }

    List<GenericCompilerCacheState<Key, SourceState, OutputState>> obsoleteItems = new ArrayList<>();
    for (Key key : toRemove) {
      final GenericCompilerCache.PersistentStateData<SourceState, OutputState> data = cache.getState(targetId, key);
      obsoleteItems.add(new GenericCompilerCacheState<>(key, data.mySourceState, data.myOutputState));
    }

    final List<Item> processedItems = new ArrayList<>();
    final List<File> filesToRefresh = new ArrayList<>();
    final List<File> dirsToRefresh = new ArrayList<>();
    instance.processItems(target, toProcess, obsoleteItems, new GenericCompilerInstance.OutputConsumer<Item>() {
      @Override
      public void addFileToRefresh(@NotNull File file) {
        filesToRefresh.add(file);
      }

      @Override
      public void addDirectoryToRefresh(@NotNull File dir) {
        dirsToRefresh.add(dir);
      }

      @Override
      public void addProcessedItem(@NotNull Item sourceItem) {
        processedItems.add(sourceItem);
      }
    });
    checkForErrorsOrCanceled();

    CompilerUtil.runInContext(myContext, CompilerBundle.message("progress.updating.caches"), () -> {
      for (Key key : toRemove) {
        cache.remove(targetId, key);
      }
      CompilerUtil.refreshIOFiles(filesToRefresh);
      CompilerUtil.refreshIODirectories(dirsToRefresh);
      if (LOG.isDebugEnabled()) {
        LOG.debug("refreshed " + filesToRefresh.size() + " files and " + dirsToRefresh.size() + " dirs");
        for (int i = 0; i < getItemsCountToShowInLog(filesToRefresh.size()); i++) {
          LOG.debug("file: " + filesToRefresh.get(i));
        }
        for (int i = 0; i < getItemsCountToShowInLog(dirsToRefresh.size()); i++) {
          LOG.debug("dir: " + dirsToRefresh.get(i));
        }
      }

      final RunResult runResult = new ReadAction() {
        protected void run(@NotNull final Result result) throws Throwable {
          for (Item item : processedItems) {
            SourceState sourceState = sourceStates.get(item);
            if (sourceState == null) {
              sourceState = item.computeSourceState();
            }
            cache.putState(targetId, item.getKey(), sourceState, item.computeOutputState());
          }
        }
      }.executeSilently();
      Throwable throwable = runResult.getThrowable();
      if (throwable instanceof IOException) throw (IOException) throwable;
      else if (throwable != null) throw new RuntimeException(throwable);
    });

    return true;

  }

  private static int getItemsCountToShowInLog(final int size) {
    if (size > 100 && !FULL_LOG.isDebugEnabled()) {
      return 100;
    }
    return size;
  }

  private class SourceItemHashingStrategy<S> implements TObjectHashingStrategy<S> {
    private KeyDescriptor<S> myKeyDescriptor;

    public SourceItemHashingStrategy(GenericCompiler<S, ?, ?> compiler) {
      myKeyDescriptor = compiler.getItemKeyDescriptor();
    }

    @Override
    public int computeHashCode(S object) {
      return myKeyDescriptor.getHashCode(object);
    }

    @Override
    public boolean equals(S o1, S o2) {
      return myKeyDescriptor.isEqual(o1, o2);
    }
  }
}
