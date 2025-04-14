// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.packaging.jlink;

import com.intellij.execution.CommandLineUtil;
import com.intellij.execution.CommandLineWrapperUtil;
import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.JpsBuildBundle;
import org.jetbrains.jps.builders.artifacts.ArtifactBuildTaskProvider;
import org.jetbrains.jps.incremental.BuildTask;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class JLinkArtifactBuildTaskProvider extends ArtifactBuildTaskProvider {
  public static final String IMAGE_DIR_NAME = "jdk";

  @Override
  public @NotNull List<? extends BuildTask> createArtifactBuildTasks(@NotNull JpsArtifact artifact,
                                                                     @NotNull ArtifactBuildPhase buildPhase) {
    if (buildPhase != ArtifactBuildPhase.POST_PROCESSING || !(artifact.getArtifactType() instanceof JpsJLinkArtifactType)) {
      return Collections.emptyList();
    }
    final JpsElement properties = artifact.getProperties();
    if (!(properties instanceof JpsJLinkProperties)) return Collections.emptyList();
    return Collections.singletonList(new JLinkBuildTask(artifact));
  }

  private static final class JLinkBuildTask extends BuildTask {
    private static final Logger LOG = Logger.getInstance(JLinkBuildTask.class);
    private final JpsArtifact myArtifact;

    private JLinkBuildTask(@NotNull JpsArtifact artifact) {
      myArtifact = artifact;
    }

    @Override
    public void build(@NotNull CompileContext context) {
      LOG.info("jlink task was started");

      JpsSdk<?> javaSdk = findValidSdk(context);
      if (javaSdk == null) {
        error(context, JpsBuildBundle.message("packaging.jlink.build.task.wrong.java.version"));
        return;
      }
      JpsJLinkProperties properties = (JpsJLinkProperties)myArtifact.getProperties();
      String artifactOutputPath = myArtifact.getOutputPath();
      if (artifactOutputPath == null) {
        error(context, JpsBuildBundle.message("packaging.jlink.build.task.unknown.artifact.path"));
        return;
      }
      Path runtimeImagePath = Paths.get(artifactOutputPath, IMAGE_DIR_NAME);
      List<String> commands = buildCommands(context, properties, javaSdk, artifactOutputPath, runtimeImagePath);
      if (commands.isEmpty()) return;
      try {
        FileUtil.delete(runtimeImagePath);
      }
      catch (IOException e) {
        error(context, JpsBuildBundle.message("packaging.jlink.build.task.run.time.image.deletion.failure"));
        return;
      }
      final int errorCode = startProcess(context, commands, properties);
      if (errorCode != 0) {
        error(context, JpsBuildBundle.message("packaging.jlink.build.task.failure"));
        return;
      }

      LOG.info("jlink task was finished");
    }

    private static @Nullable JpsSdk<?> findValidSdk(@NotNull CompileContext context) {
      Set<JpsSdk<?>> sdks = context.getProjectDescriptor().getProjectJavaSdks();
      JpsSdk<?> javaSdk = null;
      for (JpsSdk<?> sdk : sdks) {
        JpsSdkType<? extends JpsElement> sdkType = sdk.getSdkType();
        if (sdkType instanceof JpsJavaSdkType && JpsJavaSdkType.getJavaVersion(sdk) >= 9) {
          javaSdk = sdk;
          break;
        }
      }
      return javaSdk;
    }

    private static @NotNull List<String> buildCommands(@NotNull CompileContext context,
                                                       @NotNull JpsJLinkProperties properties,
                                                       @NotNull JpsSdk<?> javaSdk,
                                                       @NotNull String artifactOutputPath,
                                                       @NotNull Path runtimeImagePath) {
      String modulesSequence = getModulesSequence(artifactOutputPath);
      if (StringUtil.isEmpty(modulesSequence)) {
        error(context, JpsBuildBundle.message("packaging.jlink.build.task.modules.not.found"));
        return Collections.emptyList();
      }
      String sdkHomePath = javaSdk.getHomePath();
      String jLinkPath = Paths.get(sdkHomePath, "bin", "jlink").toString();
      List<String> commands = new ArrayList<>();
      commands.add(jLinkPath);
      if (properties.compressionLevel != null && properties.compressionLevel.hasCompression()) {
        addOption(commands, "--compress", String.valueOf(properties.compressionLevel.myValue));
      }
      if (properties.verbose) {
        commands.add("--verbose");
      }
      commands.add("--module-path");
      commands.add(String.join(File.pathSeparator, artifactOutputPath, Paths.get(sdkHomePath, "jmods").toString()));
      commands.add("--add-modules");
      commands.add(modulesSequence);
      addOption(commands, "--output", runtimeImagePath.toString());
      LOG.info(String.join(" ", commands));
      return commands;
    }

    /**
     * java.lang.module.ModuleFinder API is used here through reflection
     * as idea modules used in the build process were left compatible with Java 8 by intention.
     * Details are here: <a href="https://youtrack.jetbrains.com/issue/IDEA-243693">IDEA-243693</a>
     */
    private static @Nullable String getModulesSequence(@NotNull String artifactOutputPath) {
      final Method of;
      try {
        of = Class.forName("java.lang.module.ModuleFinder").getMethod("of", Path[].class);
      }
      catch (NoSuchMethodException | ClassNotFoundException e) {
        LOG.error("Couldn't get java.lang.module.ModuleFinder#of method");
        return null;
      }
      final Object finder;
      try {
        finder = of.invoke(null, new Object[]{new Path[]{Paths.get(artifactOutputPath)}});
      }
      catch (IllegalAccessException | InvocationTargetException e) {
        LOG.error("Couldn't call java.lang.module.ModuleFinder#of method");
        return null;
      }

      final Method findAll;
      try {
        findAll = Class.forName("java.lang.module.ModuleFinder").getMethod("findAll");
      }
      catch (NoSuchMethodException | ClassNotFoundException e) {
        LOG.error("Couldn't get java.lang.module.ModuleFinder#findAll method");
        return null;
      }
      final Object allRefs;
      try {
        allRefs = findAll.invoke(finder);
      }
      catch (IllegalAccessException | InvocationTargetException e) {
        LOG.error("Couldn't call java.lang.module.ModuleFinder#findAll method");
        return null;
      }

      Set moduleRefs = (Set)allRefs;
      if (moduleRefs.isEmpty()) return null;

      final Method descriptor;
      try {
        descriptor = Class.forName("java.lang.module.ModuleReference").getMethod("descriptor");
      }
      catch (NoSuchMethodException | ClassNotFoundException e) {
        LOG.error("Couldn't get java.lang.module.ModuleReference#descriptor method");
        return null;
      }
      final Method name;
      try {
        name = Class.forName("java.lang.module.ModuleDescriptor").getMethod("name");
      }
      catch (NoSuchMethodException | ClassNotFoundException e) {
        LOG.error("Couldn't get java.lang.module.ModuleDescriptor#name method");
        return null;
      }
      StringJoiner result = new StringJoiner(",");
      try {
        for (Object moduleRef : moduleRefs) {
          String moduleName = ObjectUtils.tryCast(name.invoke(descriptor.invoke(moduleRef)), String.class);
          if (moduleName == null) return null;
          result.add(moduleName);
        }
      }
      catch (IllegalAccessException | InvocationTargetException e) {
        LOG.error("Couldn't call java.lang.module.ModuleReference#descriptor or name method");
        return null;
      }
      return result.toString();
    }

    private static int startProcess(@NotNull CompileContext context,
                                    @NotNull List<String> commands,
                                    @NotNull JpsJLinkProperties properties) {
      File arg_file = null;
      try {
        final AtomicInteger exitCode = new AtomicInteger();
        final @NlsSafe StringBuilder errorOutput = new StringBuilder();
        final List<@NlsSafe String> delayedInfoOutput = new ArrayList<>();
        
        arg_file = FileUtil.createTempFile("jlink_arg_file", null);
        CommandLineWrapperUtil.writeArgumentsFile(arg_file, commands.subList(1, commands.size()), StandardCharsets.UTF_8);

        List<String> newCommands = Arrays.asList(commands.get(0), "@" + arg_file.getCanonicalPath());
        final Process process = new ProcessBuilder(CommandLineUtil.toCommandLine(newCommands)).start();

        BaseOSProcessHandler handler = new BaseOSProcessHandler(process, newCommands.toString(), null);
        handler.addProcessListener(new ProcessListener() {
          @Override
          public void processTerminated(@NotNull ProcessEvent event) {
            exitCode.set(event.getExitCode());
          }

          @Override
          public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
            String message = StringUtil.trimTrailing(event.getText());
            if (outputType == ProcessOutputTypes.STDERR) {
              errorOutput.append(event.getText());
            }
            else {
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
      catch (Throwable e) {
        error(context, e.getMessage());
        LOG.error(e);
        return -1;
      }
      finally {
        if (arg_file != null) {
          FileUtil.delete(arg_file);
        }
      }
    }

    private static void addOption(List<String> commands, @NotNull String key, @Nullable String value) {
      if (!StringUtil.isEmpty(value)) {
        commands.add(key);
        commands.add(value);
      }
    }

    private static void error(@NotNull CompileContext compileContext, @Nls String message) {
      compileContext.processMessage(new CompilerMessage("jlink", BuildMessage.Kind.ERROR, message));
    }

    private static void info(@NotNull CompileContext compileContext, @Nls String message) {
      compileContext.processMessage(new CompilerMessage("jlink", BuildMessage.Kind.INFO, message));
    }
  }
}
