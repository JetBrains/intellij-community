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

import com.intellij.compiler.impl.newApi.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
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
public class NewCompilerRunner {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.NewCompilerRunner");
  private CompileContext myContext;
  private final boolean myForceCompile;
  private final boolean myOnlyCheckStatus;
  private final NewCompiler<?,?>[] myCompilers;
  private final Project myProject;

  public NewCompilerRunner(CompileContext context, CompilerManager compilerManager, boolean forceCompile, boolean onlyCheckStatus) {
    myContext = context;
    myForceCompile = forceCompile;
    myOnlyCheckStatus = onlyCheckStatus;
    myCompilers = compilerManager.getCompilers(NewCompiler.class);
    myProject = myContext.getProject();
  }

  public boolean invokeCompilers(NewCompiler.CompileOrderPlace place) throws CompileDriver.ExitException {
    boolean didSomething = false;
    try {
      for (NewCompiler<?, ?> compiler : myCompilers) {
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

  private <T extends BuildTarget, Key, State> boolean invokeCompiler(NewCompiler<Key, State> compiler) throws IOException, CompileDriver.ExitException {
    return invokeCompiler(compiler, compiler.createInstance(myContext));
  }

  private <T extends BuildTarget, Item extends CompileItem<Key, State>, Key, State>
  boolean invokeCompiler(NewCompiler<Key, State> compiler, CompilerInstance<T, Item, Key, State> instance) throws IOException, CompileDriver.ExitException {
    NewCompilerCache<Key, State> cache = CompilerCacheManager.getInstance(myProject).getNewCompilerCache(compiler);
    NewCompilerPersistentData data = new NewCompilerPersistentData(getNewCompilerCacheDir(myProject, compiler), compiler.getVersion());
    if (data.isVersionChanged()) {
      LOG.info("Clearing cache for " + compiler.getDescription());
      cache.wipe();
    }

    Set<String> targetsToRemove = new HashSet<String>(data.getAllTargets());
    for (T target : instance.getAllTargets()) {
      targetsToRemove.remove(target.getId());
    }
    if (!myOnlyCheckStatus) {
      for (String target : targetsToRemove) {
        int id = data.removeId(target);
        if (LOG.isDebugEnabled()) {
          LOG.debug("Removing obsolete target '" + target + "' (id=" + id + ")");
        }
        List<Key> keys = new ArrayList<Key>();
        cache.processSources(id, new CommonProcessors.CollectProcessor<Key>(keys));
        List<Pair<Key, State>> obsoleteSources = new ArrayList<Pair<Key, State>>();
        for (Key key : keys) {
          final State state = cache.getState(id, key);
          obsoleteSources.add(Pair.create(key, state));
        }
        instance.processObsoleteTarget(target, obsoleteSources);
        if (myContext.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
          return true;
        }
        for (Key key : keys) {
          cache.remove(id, key);
        }
      }
    }

    boolean didSomething = false;
    for (T target : instance.getSelectedTargets()) {
      int id = data.getId(target.getId());
      didSomething |= processTarget(target, id, compiler, instance, cache);
    }

    data.save();
    return didSomething;
  }

  public static File getNewCompilerCacheDir(Project project, NewCompiler<?, ?> compiler) {
    return new File(CompilerPaths.getCacheStoreDirectory(project), compiler.getId());
  }

  private <T extends BuildTarget, Item extends CompileItem<Key, State>, Key, State>
  boolean processTarget(T target, final int targetId, final NewCompiler<Key, State> compiler, final CompilerInstance<T, Item, Key, State> instance,
                        final NewCompilerCache<Key, State> cache) throws IOException, CompileDriver.ExitException {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Processing target '" + target + "' (id=" + targetId + ")");
    }
    final List<Item> items = instance.getItems(target);
    if (myContext.getMessageCount(CompilerMessageCategory.ERROR) > 0) return true;

    final List<Pair<Item, State>> toProcess = new ArrayList<Pair<Item, State>>();
    final THashSet<Key> keySet = new THashSet<Key>(new SourceItemHashingStrategy<Key>(compiler));
    final Ref<IOException> exception = Ref.create(null);
    DumbService.getInstance(myProject).waitForSmartMode();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        try {
          for (Item item : items) {
            final Key key = item.getKey();
            keySet.add(key);
            State output = cache.getState(targetId, key);
            if (myForceCompile || output == null || !item.isUpToDate(output)) {
              toProcess.add(Pair.create(item, output));
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

    List<Pair<Key, State>> obsoleteItems = new ArrayList<Pair<Key, State>>();
    for (Key key : toRemove) {
      obsoleteItems.add(Pair.create(key, cache.getState(targetId, key)));
    }

    final List<Item> processedItems = new ArrayList<Item>();
    final List<File> toRefresh = new ArrayList<File>();
    instance.processItems(target, toProcess, obsoleteItems, new CompilerInstance.OutputConsumer<Item>() {
      @Override
      public void addFileToRefresh(@NotNull File file) {
        toRefresh.add(file);
      }
      @Override
      public void addProcessedItem(@NotNull Item sourceItem) {
        processedItems.add(sourceItem);
      }
    });
    if (myContext.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
      return true;
    }

    for (Key key : toRemove) {
      cache.remove(targetId, key);
    }
    CompilerUtil.refreshIOFiles(toRefresh);
    for (Item item : processedItems) {
      cache.putOutput(targetId, item.getKey(), item.computeState());
    }

    return true;

  }

  private class SourceItemHashingStrategy<S> implements TObjectHashingStrategy<S> {
    private KeyDescriptor<S> myKeyDescriptor;

    public SourceItemHashingStrategy(NewCompiler<S, ?> compiler) {
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
