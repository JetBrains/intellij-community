// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.jlink;

import com.intellij.execution.CommandLineUtil;
import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.artifacts.ArtifactBuildTaskProvider;
import org.jetbrains.jps.incremental.BuildTask;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.library.sdk.JpsSdk;

import java.io.File;
import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class JLinkArtifactBuildTaskProvider extends ArtifactBuildTaskProvider {

  @Override
  public @NotNull List<? extends BuildTask> createArtifactBuildTasks(@NotNull JpsArtifact artifact,
                                                                     @NotNull ArtifactBuildPhase buildPhase) {
    if (buildPhase != ArtifactBuildPhase.POST_PROCESSING || !(artifact.getArtifactType() instanceof JpsJLinkArtifactType)) {
      return Collections.emptyList();
    }
    final JpsElement properties = artifact.getProperties();
    if (!(properties instanceof JpsJLinkProperties)) {
      return Collections.emptyList();
    }
    return Collections.singletonList(new JLinkBuildTask(artifact));
  }

  private static class JLinkBuildTask extends BuildTask {
    private static final Logger LOG = Logger.getInstance(JLinkBuildTask.class);
    private final JpsArtifact myArtifact;

    private JLinkBuildTask(@NotNull JpsArtifact artifact) {
      myArtifact = artifact;
    }

    @Override
    public void build(CompileContext context) throws ProjectBuildException {
      LOG.warn("jlink task was started");

      Set<JpsSdk<?>> sdks = context.getProjectDescriptor().getProjectJavaSdks();
      if (sdks.isEmpty()) {
        return;
      }
      ModuleFinder moduleFinder = ModuleFinder.of(Path.of(myArtifact.getOutputFilePath()));
      String modulesStr = moduleFinder.findAll().stream().map(mr -> mr.descriptor().name()).collect(Collectors.joining(","));
      if (modulesStr.isEmpty()) {
        error(context, "No module has been found");
        return;
      }
      JpsSdk<?> javaSdk = sdks.iterator().next();
      String sdkHomePath = javaSdk.getHomePath();
      String jLinkPath = sdkHomePath + File.separatorChar + "bin" + File.separatorChar + "jlink";
      List<String> commands = new ArrayList<>();
      commands.add(jLinkPath);
      JpsJLinkProperties properties = (JpsJLinkProperties)myArtifact.getProperties();
      if (properties.compressionLevel.hasCompression()) {
        addOption(commands, "--compress", String.valueOf(properties.compressionLevel.myValue));
      }
      if (properties.verbose) {
        commands.add("--verbose");
      }
      commands.add("--module-path");
      commands.add(myArtifact.getOutputFilePath());
      commands.add("--add-modules");
      commands.add(modulesStr);
      String outputPath = myArtifact.getOutputFilePath() + File.separator + "jdk";
      addOption(commands, "--output", outputPath);

      LOG.warn(String.join(" ", commands));

      try {
        FileUtil.delete(Path.of(outputPath));
      }
      catch (IOException e) {
        error(context,"Couldn't delete existing run-time image: " + e.getMessage());
      }

      final int errorCode = startProcess(context, commands, properties);
      if (errorCode != 0) {
        error(context,"jlink task has failed");
      }

      LOG.warn("jlink task was finished");
    }

    private int startProcess(@NotNull CompileContext context, @NotNull List<String> commands, @NotNull JpsJLinkProperties properties) {
      try {
        final AtomicInteger exitCode = new AtomicInteger();
        final @NlsSafe StringBuilder errorOutput = new StringBuilder();
        final List<@NlsSafe String> delayedInfoOutput = new ArrayList<>();

        final Process process = new ProcessBuilder(CommandLineUtil.toCommandLine(commands)).start();
        BaseOSProcessHandler handler = new BaseOSProcessHandler(process, commands.toString(), null);
        handler.addProcessListener(new ProcessAdapter() {
          @Override
          public void startNotified(@NotNull ProcessEvent event) {
            if (properties.verbose) {
              LOG.info("Started " + commands);
            }
          }

          @Override
          public void processTerminated(@NotNull ProcessEvent event) {
            if (properties.verbose) {
              LOG.info("Terminated " + commands + ", exit code: " + event.getExitCode());
            }
            exitCode.set(event.getExitCode());
          }

          @Override
          public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
            String message = StringUtil.trimTrailing(event.getText());
            if (outputType == ProcessOutputTypes.STDERR) {
              LOG.error(message, (Throwable)null);
              errorOutput.append(event.getText());
            }
            else {
              LOG.info(message);
              if (properties.verbose) {
                info(context, message);
              }
              else {
                delayedInfoOutput.add(message);
              }
            }
          }
        });

        handler.startNotify();
        handler.waitFor();

        int result = exitCode.get();
        if (result != 0) {
          final String message = errorOutput.toString();
          if (!StringUtil.isEmptyOrSpaces(message)) {
            error(context, message);
          }
          for (@NlsSafe String info : delayedInfoOutput) {
            error(context, info);
          }
        }
        return result;
      }
      catch (Exception e) {
        error(context, e.getMessage());
        LOG.warn(e);
        return -1;
      }
    }

    private static void addOption(List<String> commands, @NotNull String key, @Nullable String value) {
      if (!StringUtil.isEmpty(value)) {
        commands.add(key);
        commands.add(value);
      }
    }

    private void error(@NotNull CompileContext compileContext, @Nls String message) {
      compileContext.processMessage(new CompilerMessage("jlink", BuildMessage.Kind.ERROR, message));
    }

    private void info(@NotNull CompileContext compileContext, @Nls String message) {
      compileContext.processMessage(new CompilerMessage("jlink", BuildMessage.Kind.INFO, message));
    }
  }
}
