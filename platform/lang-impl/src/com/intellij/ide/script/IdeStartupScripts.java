/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;
import org.jetbrains.ide.script.IdeScriptEngine;
import org.jetbrains.ide.script.IdeScriptEngineManager;
import org.jetbrains.ide.script.IdeScriptException;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

class IdeStartupScripts implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance(IdeStartupScripts.class);

  private static final String SCRIPT_DIR = "startup";

  @Override
  public void initComponent() {
    Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode()) return;
    MessageBusConnection connection = application.getMessageBus().connect();
    connection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      Future<List<Pair<File, IdeScriptEngine>>> future;
      @Override
      public void projectOpened(Project project) {
        if (future == null) {
          future = PooledThreadExecutor.INSTANCE.submit(() -> prepareScriptsAndEngines());
        }
        StartupManager.getInstance(project).runWhenProjectIsInitialized(() -> {
          if (!project.isOpen()) return;
          connection.disconnect();
          runAllScriptsImpl(project, future);
        });
      }
    });
  }

  @NotNull
  private static List<Pair<File, IdeScriptEngine>> prepareScriptsAndEngines() {
    List<File> scripts = getScripts();
    LOG.info(scripts.size() + " startup script(s) found");
    if (scripts.isEmpty()) return Collections.emptyList();

    IdeScriptEngineManager scriptEngineManager = IdeScriptEngineManager.getInstance();
    List<Pair<File, IdeScriptEngine>> result = ContainerUtil.newArrayList();
    for (File script : scripts) {
      String extension = FileUtilRt.getExtension(script.getName());
      IdeScriptEngine engine = extension.isEmpty() ? null : scriptEngineManager.getEngineForFileExtension(extension, null);
      result.add(Pair.create(script, engine));
    }
    return result;
  }

  private static void runImpl(@NotNull Project project,
                              @NotNull File script,
                              @NotNull IdeScriptEngine engine) throws IOException, IdeScriptException {
    String scriptText = FileUtil.loadFile(script);
    IdeConsoleScriptBindings.ensureIdeIsBound(project, engine);

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
  private static List<File> getScripts() {
    File directory = getScriptsRootDirectory();
    List<File> scripts = JBIterable.of(directory == null ? null : directory.listFiles())
      .filter(ExtensionsRootType.regularFileFilter())
      .toList();

    ContainerUtil.sort(scripts, (f1, f2) -> {
      String f1Name = f1 != null ? f1.getName() : null;
      String f2Name = f2 != null ? f2.getName() : null;
      return StringUtil.compare(f1Name, f2Name, false);
    });
    return scripts;
  }

  @Nullable
  private static File getScriptsRootDirectory() {
    PluginId corePlugin = ObjectUtils.assertNotNull(PluginId.findId(PluginManagerCore.CORE_PLUGIN_ID));
    try {
      return ExtensionsRootType.getInstance().findResourceDirectory(corePlugin, SCRIPT_DIR, false);
    }
    catch (IOException ignore) {
    }
    return null;
  }

  private static void runAllScriptsImpl(@NotNull Project project, @NotNull Future<List<Pair<File, IdeScriptEngine>>> future) {
    try {
      for (Pair<File, IdeScriptEngine> pair : future.get()) {
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
}
