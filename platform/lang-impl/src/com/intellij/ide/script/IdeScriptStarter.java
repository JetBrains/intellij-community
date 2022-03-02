// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.script;

import com.intellij.ide.CliResult;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ApplicationStarterBase;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * @author gregsh
 */
final class IdeScriptStarter extends ApplicationStarterBase {
  private static final Logger LOG = Logger.getInstance(IdeScriptStarter.class);

  @Override
  public String getCommandName() {
    return "ideScript";
  }

  @Override
  public String getUsageMessage() {
    String scriptName = ApplicationNamesInfo.getInstance().getScriptName();
    return LangBundle.message("ide.script.starter.usage", scriptName, getCommandName());
  }

  @Override
  protected boolean checkArguments(@NotNull List<String> args) {
    return args.size() > 1;
  }

  @NotNull
  @Override
  protected Future<CliResult> processCommand(@NotNull List<String> args,
                                             @Nullable String currentDirectory) throws Exception {
    List<Path> filePaths = ContainerUtil.map(args.subList(1, args.size()), Paths::get);
    Project project = guessProject();
    CompletableFuture<CliResult> future = new CompletableFuture<>();
    AppExecutorUtil.getAppExecutorService().execute(() -> {
      List<Pair<Path, IdeScriptEngine>> result = IdeStartupScripts.prepareScriptsAndEngines(filePaths);
      IdeStartupScripts.runAllScriptsImpl(project, result, project != null ? LOG : redirectStreamsAndGetLogger(result));
      future.complete(CliResult.OK);
    });
    return future;
  }

  @Nullable
  private static Project guessProject() {
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    Window recentFocusedWindow = WindowManagerEx.getInstanceEx().getMostRecentFocusedWindow();
    if (recentFocusedWindow instanceof IdeFrame) return ((IdeFrame)recentFocusedWindow).getProject();
    return ContainerUtil.find(openProjects, o -> o.isInitialized() && !o.isDisposed());
  }

  /** @noinspection UseOfSystemOutOrSystemErr*/
  @NotNull
  private static Logger redirectStreamsAndGetLogger(List<Pair<Path, IdeScriptEngine>> result) {
    for (Pair<Path, IdeScriptEngine> pair : result) {
      pair.second.setStdOut(new OutputStreamWriter(System.out, Charset.defaultCharset()));
      pair.second.setStdErr(new OutputStreamWriter(System.err, Charset.defaultCharset()));
      pair.second.setStdIn(new InputStreamReader(System.in, Charset.defaultCharset()));
    }
    return new DefaultLogger(null) {
      @Override
      public void info(String message) {
        System.out.println("INFO: " + message);
      }

      @Override
      public void info(String message, Throwable t) {
        System.out.println("INFO: " + message);
      }
    };
  }
}
