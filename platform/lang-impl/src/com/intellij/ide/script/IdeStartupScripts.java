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
package com.intellij.ide.script;

import com.intellij.ide.extensionResources.ExtensionsRootType;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;
import org.jetbrains.ide.script.IdeScriptEngine;
import org.jetbrains.ide.script.IdeScriptEngineManager;
import org.jetbrains.ide.script.IdeScriptException;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

class IdeStartupScripts extends ApplicationComponent.Adapter {
  @SuppressWarnings("FieldCanBeLocal")
  private static String SCRIPT_DIR_NAME = "startup";

  private static Logger LOG = Logger.getInstance(IdeStartupScripts.class);

  @Override
  public void initComponent() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    List<VirtualFile> scripts = getScripts();
    if (scripts.isEmpty()) {
      LOG.debug("No startup scripts detected");
    }
    else {
      scheduleStartupScriptsExecution(scripts);
    }
  }

  private static void scheduleStartupScriptsExecution(@NotNull List<VirtualFile> scripts) {
    final Future<List<Pair<VirtualFile, IdeScriptEngine>>> scriptsAndEnginesFuture = prepareScriptEnginesAsync(scripts);
    ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerAdapter() {
      private final AtomicBoolean myScriptsExecutionStarted = new AtomicBoolean();

      @Override
      public void projectOpened(final Project project) {
        StartupManager.getInstance(project).runWhenProjectIsInitialized(new Runnable() {
          @Override
          public void run() {
            if (myScriptsExecutionStarted.compareAndSet(false, true)) {
              executeScriptsAndDispose(project);
            }
          }
        });
      }

      private void executeScriptsAndDispose(@NotNull Project project) {
        for (Pair<VirtualFile, IdeScriptEngine> scriptAndEngine : getScriptsAndEngines()) {
          executeScript(project.isDisposed() ? null : project, scriptAndEngine.first, scriptAndEngine.second);
        }
        ProjectManager.getInstance().removeProjectManagerListener(this);
      }

      @NotNull
      private List<Pair<VirtualFile, IdeScriptEngine>> getScriptsAndEngines() {
        try {
          return scriptsAndEnginesFuture.get();
        }
        catch (InterruptedException e) {
          LOG.info("Script engines initialization cancelled");
        }
        catch (ExecutionException e) {
          LOG.error("Failed to initialize script engines", e);
        }
        return ContainerUtil.emptyList();
      }
    });
  }

  @NotNull
  private static Future<List<Pair<VirtualFile, IdeScriptEngine>>> prepareScriptEnginesAsync(@NotNull final List<VirtualFile> scripts) {
    return PooledThreadExecutor.INSTANCE.submit(new Callable<List<Pair<VirtualFile, IdeScriptEngine>>>() {
      @Override
      public List<Pair<VirtualFile, IdeScriptEngine>> call() throws Exception {
        return prepareScriptEngines(scripts);
      }
    });
  }

  @NotNull
  private static List<Pair<VirtualFile, IdeScriptEngine>> prepareScriptEngines(@NotNull final List<VirtualFile> scripts) {
    final IdeScriptEngineManager scriptEngineManager = IdeScriptEngineManager.getInstance();
    return ContainerUtil.map(scripts, new Function<VirtualFile, Pair<VirtualFile, IdeScriptEngine>>() {
      @Override
      public Pair<VirtualFile, IdeScriptEngine> fun(VirtualFile script) {
        String extension = script.getExtension();
        return Pair.create(script, extension != null ? scriptEngineManager.getEngineForFileExtension(extension) : null);
      }
    });
  }

  private static void executeScript(@Nullable Project project, @NotNull VirtualFile script, @Nullable IdeScriptEngine scriptEngine) {
    if (scriptEngine == null) {
      LOG.warn("No script engine found for script: " + script.getPath());
      return;
    }

    String scriptText;
    try {
      scriptText = VfsUtilCore.loadText(script);
    }
    catch (IOException e) {
      LOG.warn("Cannot load script: " + script.getPath(), e);
      return;
    }

    IdeScriptBindings.ensureIdeIsBound(project, scriptEngine);

    long start = System.currentTimeMillis();
    try {
      LOG.info("Running script: " + script.getPath());
      scriptEngine.eval(scriptText);
    }
    catch (IdeScriptException e) {
      LOG.error("Error in script: " + script.getPath(), e);
    }
    finally {
      long end = System.currentTimeMillis();
      LOG.info(script.getPath() + " completed in " + (end - start) + " ms");
    }
  }

  @NotNull
  private static List<VirtualFile> getScripts() {
    VirtualFile root = getScriptsRootDirectory();
    if (root == null) return ContainerUtil.emptyList();

    VfsUtil.markDirtyAndRefresh(false, true, true, root);
    List<VirtualFile> scripts = VfsUtil.collectChildrenRecursively(root);
    scripts = ContainerUtil.filter(scripts, ExtensionsRootType.regularFileFilter());
    ContainerUtil.sort(scripts, new FileNameComparator());
    return scripts;
  }

  @Nullable
  private static VirtualFile getScriptsRootDirectory() {
    try {
      PluginId corePlugin = ObjectUtils.assertNotNull(PluginId.findId(PluginManagerCore.CORE_PLUGIN_ID));
      return ExtensionsRootType.getInstance().findResourceDirectory(corePlugin, SCRIPT_DIR_NAME, false);
    }
    catch (IOException e) {
      LOG.warn("Failed to open/create startup scripts directory", e);
    }
    return null;
  }

  private static class FileNameComparator implements Comparator<VirtualFile> {
    @Override
    public int compare(VirtualFile f1, VirtualFile f2) {
      String f1Name = f1 != null ? f1.getName() : null;
      String f2Name = f2 != null ? f2.getName() : null;
      return StringUtil.compare(f1Name, f2Name, false);
    }
  }
}
