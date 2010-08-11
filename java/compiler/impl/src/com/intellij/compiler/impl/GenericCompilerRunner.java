/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.google.common.base.Throwables;
import com.intellij.compiler.impl.generic.GenericCompilerCache;
import com.intellij.compiler.impl.generic.GenericCompilerPersistentData;
import com.intellij.openapi.compiler.generic.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.RunResult;
import com.intellij.openapi.compiler.*;
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
  private CompileContext myContext;
  private final boolean myForceCompile;
  private final boolean myOnlyCheckStatus;
  private final GenericCompiler<?,?,?>[] myCompilers;
  private final Project myProject;

  public GenericCompilerRunner(CompileContext context, CompilerManager compilerManager, boolean forceCompile, boolean onlyCheckStatus) {
    myContext = context;
    myForceCompile = forceCompile;
    myOnlyCheckStatus = onlyCheckStatus;
    myCompilers = compilerManager.getCompilers(GenericCompiler.class);
    myProject = myContext.getProject();
  }

  public boolean invokeCompilers(GenericCompiler.CompileOrderPlace place) throws CompileDriver.ExitException {
    boolean didSomething = false;
    try {
      for (GenericCompiler<?,?,?> compiler : myCompilers) {
        if (compiler.getOrderPlace().equals(place)) {
          didSomething = invokeCompiler(compiler);
        }
      }
    }
    catch (IOException e) {
      LOG.info(e);
      myContext.requestRebuildNextTime(e.getMessage());
      throw new CompileDriver.ExitException(CompileDriver.ExitStatus.ERRORS);
    }
    catch (CompileDriver.ExitException e) {
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

  private <Key, SourceState, OutputState> boolean invokeCompiler(GenericCompiler<Key, SourceState, OutputState> compiler) throws IOException, CompileDriver.ExitException {
    return invokeCompiler(compiler, compiler.createInstance(myContext));
  }

  private <T extends BuildTarget, Item extends CompileItem<Key, SourceState, OutputState>, Key, SourceState, OutputState>
  boolean invokeCompiler(GenericCompiler<Key, SourceState, OutputState> compiler, final GenericCompilerInstance<T, Item, Key, SourceState, OutputState> instance) throws IOException, CompileDriver.ExitException {
    final GenericCompilerCache<Key, SourceState, OutputState> cache = CompilerCacheManager.getInstance(myProject).getGenericCompilerCache(compiler);
    GenericCompilerPersistentData
      data = new GenericCompilerPersistentData(getGenericCompilerCacheDir(myProject, compiler), compiler.getVersion());
    if (data.isVersionChanged()) {
      LOG.info("Clearing cache for " + compiler.getDescription());
      cache.wipe();
      data.save();
    }

    final Set<String> targetsToRemove = new HashSet<String>(data.getAllTargets());
    new ReadAction() {
      protected void run(final Result result) {
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

        final List<Key> keys = new ArrayList<Key>();
        CompilerUtil.runInContext(myContext, "Processing obsolete targets...", new ThrowableRunnable<IOException>() {
          @Override
          public void run() throws IOException {
            cache.processSources(id, new CommonProcessors.CollectProcessor<Key>(keys));
            List<GenericCompilerCacheState<Key, SourceState, OutputState>> obsoleteSources = new ArrayList<GenericCompilerCacheState<Key,SourceState,OutputState>>();
            for (Key key : keys) {
              final GenericCompilerCache.PersistentStateData<SourceState, OutputState> state = cache.getState(id, key);
              obsoleteSources.add(new GenericCompilerCacheState<Key,SourceState,OutputState>(key, state.mySourceState, state.myOutputState));
            }
            instance.processObsoleteTarget(target, obsoleteSources);
          }
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

  private void checkForErrorsOrCanceled() throws CompileDriver.ExitException {
    if (myContext.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
      throw new CompileDriver.ExitException(CompileDriver.ExitStatus.ERRORS);
    }
    if (myContext.getProgressIndicator().isCanceled()) {
      throw new CompileDriver.ExitException(CompileDriver.ExitStatus.CANCELLED);
    }
  }

  public static File getGenericCompilerCacheDir(Project project, GenericCompiler<?,?,?> compiler) {
    return new File(CompilerPaths.getCacheStoreDirectory(project), compiler.getId());
  }

  private <T extends BuildTarget, Item extends CompileItem<Key, SourceState, OutputState>, Key, SourceState, OutputState>
  boolean processTarget(T target, final int targetId, final GenericCompiler<Key, SourceState, OutputState> compiler, final GenericCompilerInstance<T, Item, Key, SourceState, OutputState> instance,
                        final GenericCompilerCache<Key, SourceState, OutputState> cache) throws IOException, CompileDriver.ExitException {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Processing target '" + target + "' (id=" + targetId + ")");
    }
    final List<Item> items = instance.getItems(target);
    checkForErrorsOrCanceled();

    final List<GenericCompilerProcessingItem<Item, SourceState, OutputState>> toProcess = new ArrayList<GenericCompilerProcessingItem<Item,SourceState,OutputState>>();
    final THashSet<Key> keySet = new THashSet<Key>(new SourceItemHashingStrategy<Key>(compiler));
    final Ref<IOException> exception = Ref.create(null);
    DumbService.getInstance(myProject).waitForSmartMode();
    final Map<Item, SourceState> sourceStates = new HashMap<Item,SourceState>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        try {
          for (Item item : items) {
            final Key key = item.getKey();
            keySet.add(key);
            final GenericCompilerCache.PersistentStateData<SourceState, OutputState> data = cache.getState(targetId, key);
            SourceState sourceState = data != null ? data.mySourceState : null;
            final OutputState outputState = data != null ? data.myOutputState : null;
            if (myForceCompile || sourceState == null || !item.isSourceUpToDate(sourceState)
                               || outputState == null || !item.isOutputUpToDate(outputState)) {
              sourceStates.put(item, item.computeSourceState());
              toProcess.add(new GenericCompilerProcessingItem<Item,SourceState,OutputState>(item, sourceState, outputState));
            }
          }
        }
        catch (IOException e) {
          exception.set(e);
        }
      }
    });
    if (!exception.isNull()) {
      throw exception.get();
    }

    final List<Key> toRemove = new ArrayList<Key>();
    cache.processSources(targetId, new Processor<Key>() {
      @Override
      public boolean process(Key key) {
        if (!keySet.contains(key)) {
          toRemove.add(key);
        }
        return true;
      }
    });

    if (LOG.isDebugEnabled()) {
      LOG.debug(toProcess.size() + " items will be processed, " + toRemove.size() + " items will be removed");
    }

    if (toProcess.isEmpty() && toRemove.isEmpty()) {
      return false;
    }

    if (myOnlyCheckStatus) {
      throw new CompileDriver.ExitException(CompileDriver.ExitStatus.CANCELLED);
    }

    List<GenericCompilerCacheState<Key, SourceState, OutputState>> obsoleteItems = new ArrayList<GenericCompilerCacheState<Key,SourceState,OutputState>>();
    for (Key key : toRemove) {
      final GenericCompilerCache.PersistentStateData<SourceState, OutputState> data = cache.getState(targetId, key);
      obsoleteItems.add(new GenericCompilerCacheState<Key,SourceState,OutputState>(key, data.mySourceState, data.myOutputState));
    }

    final List<Item> processedItems = new ArrayList<Item>();
    final List<File> filesToRefresh = new ArrayList<File>();
    final List<File> dirsToRefresh = new ArrayList<File>();
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

    CompilerUtil.runInContext(myContext, CompilerBundle.message("progress.updating.caches"), new ThrowableRunnable<IOException>() {
      @Override
      public void run() throws IOException {
        for (Key key : toRemove) {
          cache.remove(targetId, key);
        }
        CompilerUtil.refreshIOFiles(filesToRefresh);
        CompilerUtil.refreshIODirectories(dirsToRefresh);

        final RunResult runResult = new ReadAction() {
          protected void run(final Result result) throws Throwable {
            for (Item item : processedItems) {
              SourceState sourceState = sourceStates.get(item);
              if (sourceState == null) {
                sourceState = item.computeSourceState();
              }
              cache.putState(targetId, item.getKey(), sourceState, item.computeOutputState());
            }
          }
        }.executeSilently();
        Throwables.propagateIfPossible(runResult.getThrowable(), IOException.class);
      }
    });

    return true;

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
