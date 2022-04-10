// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.script;

import com.intellij.ide.extensionResources.ExtensionsRootType;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.Formats;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.PathKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class IdeStartupScripts implements StartupActivity.DumbAware {
  private static final Logger LOG = Logger.getInstance(IdeStartupScripts.class);

  private static final String SCRIPT_DIR = "startup";

  private final AtomicBoolean isActive = new AtomicBoolean(true);

  IdeStartupScripts() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      throw ExtensionNotApplicableException.create();
    }
  }

  @Override
  public void runActivity(@NotNull Project project) {
    if (!isActive.compareAndSet(true, false)) {
      return;
    }

    List<Path> scripts = getScripts();
    LOG.info(scripts.size() + " startup script(s) found");
    if (!scripts.isEmpty()) {
      runAllScriptsImpl(project, prepareScriptsAndEngines(scripts), LOG);
    }
  }

  static @NotNull List<Pair<Path, IdeScriptEngine>> prepareScriptsAndEngines(List<Path> scripts) {
    IdeScriptEngineManager scriptEngineManager = IdeScriptEngineManager.getInstance();
    List<Pair<Path, IdeScriptEngine>> result = new ArrayList<>();
    for (Path script : scripts) {
      String extension = FileUtilRt.getExtension(script.getFileName().toString());
      IdeScriptEngine engine = extension.isEmpty() ? null : scriptEngineManager.getEngineByFileExtension(extension, null);
      if (engine == null) {
        LOG.warn(script + " not supported (no script engine)");
        continue;
      }
      result.add(new Pair<>(script, engine));
    }
    return result;
  }

  private static @NotNull List<Path> getScripts() {
    try {
      Path directory = ExtensionsRootType.getInstance().findResourceDirectory(PluginManagerCore.CORE_ID, SCRIPT_DIR, false);
      try (Stream<Path> stream = Files.list(directory)) {
        return stream
          .filter(ExtensionsRootType.regularFileFilter())
          .sorted((f1, f2) -> {
            String f1Name = f1 == null ? null : f1.getFileName().toString();
            String f2Name = f2 == null ? null : f2.getFileName().toString();
            return StringUtil.compare(f1Name, f2Name, false);
          })
          .collect(Collectors.toList());
      }
    }
    catch (NoSuchFileException ignore) {
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return Collections.emptyList();
  }

  static void runAllScriptsImpl(@Nullable Project project,
                                @NotNull List<Pair<Path, IdeScriptEngine>> result,
                                @NotNull Logger logger) {
    for (Pair<Path, IdeScriptEngine> pair : result) {
      try {
        logger.info(pair.first.toString());

        String scriptText = PathKt.readText(pair.first);
        IdeConsoleScriptBindings.ensureIdeIsBound(project, pair.second);

        long start = System.currentTimeMillis();
        try {
          pair.second.eval(scriptText);
        }
        catch (ProcessCanceledException e) {
          logger.warn("... cancelled");
        }
        finally {
          logger.info("... completed in " + Formats.formatDuration(System.currentTimeMillis() - start));
        }
      }
      catch (Exception e) {
        logger.warn(e);
      }
    }
  }
}
