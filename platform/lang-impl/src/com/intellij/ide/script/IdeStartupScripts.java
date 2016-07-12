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
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;
import org.jetbrains.ide.script.IdeScriptEngine;
import org.jetbrains.ide.script.IdeScriptEngineManager;
import org.jetbrains.ide.script.IdeScriptException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

class IdeStartupScripts extends ApplicationComponent.Adapter {
  private static final Logger LOG = Logger.getInstance(IdeStartupScripts.class);

  private static final String SCRIPT_DIR = "startup";

  @Override
  public void initComponent() {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    scheduleStartupScriptsExecution();
  }

  private static void scheduleStartupScriptsExecution() {
    List<VirtualFile> scripts = getScripts();
    LOG.info(scripts.size() + " startup script(s) found");
    if (scripts.isEmpty()) return;

    final Future<List<Pair<VirtualFile, IdeScriptEngine>>> scriptsAndEnginesFuture = prepareScriptEnginesAsync(scripts);
    ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerAdapter() {
      final AtomicBoolean myScriptsExecutionStarted = new AtomicBoolean();

      @Override
      public void projectOpened(final Project project) {
        StartupManager.getInstance(project).runWhenProjectIsInitialized(() -> {
          if (project.isDisposed()) return;
          if (!myScriptsExecutionStarted.compareAndSet(false, true)) return;
          ProjectManager.getInstance().removeProjectManagerListener(this);
          runAllScriptsImpl(project);
        });
      }

      private void runAllScriptsImpl(@NotNull Project project) {
        try {
          for (Pair<VirtualFile, IdeScriptEngine> pair : scriptsAndEnginesFuture.get()) {
            try {
              if (pair.second == null) {
                LOG.warn(pair.first.getPath() + " not supported (no script engine)");
              }
              else {
                runImpl(project, pair.first, pair.second);
              }
            }
            catch (Exception e) {
              LOG.warn(e);
            }
          }
        }
        catch (ProcessCanceledException e) {
          LOG.warn("... cancelled");
        }
        catch (InterruptedException e) {
          LOG.warn("... interrupted");
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    });
  }

  @NotNull
  private static Future<List<Pair<VirtualFile, IdeScriptEngine>>> prepareScriptEnginesAsync(@NotNull final List<VirtualFile> scripts) {
    return PooledThreadExecutor.INSTANCE.submit(() -> prepareScriptEngines(scripts));
  }

  @NotNull
  private static List<Pair<VirtualFile, IdeScriptEngine>> prepareScriptEngines(@NotNull List<VirtualFile> scripts) {
    IdeScriptEngineManager scriptEngineManager = IdeScriptEngineManager.getInstance();
    List<Pair<VirtualFile, IdeScriptEngine>> result = ContainerUtil.newArrayList();
    for (VirtualFile script : scripts) {
      String extension = script.getExtension();
      IdeScriptEngine engine = extension != null ? scriptEngineManager.getEngineForFileExtension(extension, null) : null;
      result.add(Pair.create(script, engine));
    }
    return result;
  }

  private static void runImpl(@NotNull Project project,
                              @NotNull VirtualFile script,
                              @NotNull IdeScriptEngine engine) throws ExecutionException, IOException, IdeScriptException {
    String scriptText = VfsUtilCore.loadText(script);
    IdeScriptBindings.ensureIdeIsBound(project, engine);

    LOG.info(script.getPath());
    long start = System.currentTimeMillis();
    try {
      engine.eval(scriptText);
    }
    finally {
      LOG.info("... completed in " + StringUtil.formatDuration(System.currentTimeMillis() - start));
    }
  }

  @NotNull
  private static List<VirtualFile> getScripts() {
    List<VirtualFile> scripts;
    try {
      VirtualFile scriptDir = getScriptsRootDirectory();
      VirtualFile[] scriptDirChildren = scriptDir != null ? scriptDir.getChildren() : VirtualFile.EMPTY_ARRAY;
      Condition<VirtualFile> regularFileFilter = ExtensionsRootType.regularFileFilter();
      scripts = Arrays.stream(scriptDirChildren).filter(regularFileFilter::value).collect(Collectors.toList());
    }
    catch (IOException ignore) {
      return Collections.emptyList();
    }

    ContainerUtil.sort(scripts, (f1, f2) -> {
      String f1Name = f1 != null ? f1.getName() : null;
      String f2Name = f2 != null ? f2.getName() : null;
      return StringUtil.compare(f1Name, f2Name, false);
    });
    return scripts;
  }

  @Nullable
  private static VirtualFile getScriptsRootDirectory() throws IOException {
    PluginId corePlugin = ObjectUtils.assertNotNull(PluginId.findId(PluginManagerCore.CORE_PLUGIN_ID));
    return ExtensionsRootType.getInstance().findResourceDirectory(corePlugin, SCRIPT_DIR, false);
  }
}
