// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.build;

import com.intellij.openapi.util.LowMemoryWatcherManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ParameterizedRunnable;
import com.sampullara.cli.Args;
import com.sampullara.cli.Argument;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.api.BuildType;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.cmdline.*;
import org.jetbrains.jps.incremental.MessageHandler;
import org.jetbrains.jps.incremental.Utils;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuildTargetType;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.service.SharedThreadPool;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;

@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
public final class Standalone {
  @Argument(value = "config", prefix = "--", description = "Path to directory containing global options (idea.config.path)")
  public String configPath;

  @Argument(value = "script", prefix = "--", description = "Path to Groovy script which will be used to initialize global options (deprecated)")
  public String initializationScriptPath;

  @Argument(value = "cache-dir", prefix = "--", description = "Path to directory to store build caches")
  public String cacheDirPath;

  @Argument(value = "modules", prefix = "--", delimiter = ",", description = "Comma-separated list of modules to compile")
  public String[] modules = ArrayUtilRt.EMPTY_STRING_ARRAY;

  @Argument(value = "all-modules", prefix = "--", description = "Compile all modules")
  public boolean allModules;

  @Argument(value = "artifacts", prefix = "--", delimiter = ",", description = "Comma-separated list of artifacts to build")
  public String[] artifacts = ArrayUtilRt.EMPTY_STRING_ARRAY;

  @Argument(value = "all-artifacts", prefix = "--", description = "Build all artifacts")
  public boolean allArtifacts;

  @Argument(value = "i", description = "Build incrementally")
  public boolean incremental;

  static {
    LogSetup.initLoggers();
  }

  public static void main(String[] args) {
    Standalone instance = new Standalone();
    List<String> projectPaths;
    try {
      projectPaths = Args.parse(instance, args);
    }
    catch (Exception e) {
      printUsageAndExit();
      return;
    }

    if (projectPaths.isEmpty()) {
      System.out.println("Path to project is not specified");
      printUsageAndExit();
    }
    if (projectPaths.size() > 1) {
      System.out.println("Only one project can be specified");
      printUsageAndExit();
    }

    final String projectPath = (new File(projectPaths.get(0))).getAbsolutePath();
    int exitCode = instance.loadAndRunBuild(FileUtil.toCanonicalPath(projectPath));
    System.exit(exitCode);
  }

  private static void printUsageAndExit() {
    Args.usage(System.err, new Standalone());
    System.exit(1);
  }

  public int loadAndRunBuild(final String projectPath) {
    String globalOptionsPath = null;
    if (configPath != null) {
      File optionsDir = new File(configPath, "options");
      if (!optionsDir.isDirectory()) {
        System.err.println("'" + configPath + "' is not valid config path: " + optionsDir.getAbsolutePath() + " not found");
        return 1;
      }
      globalOptionsPath = optionsDir.getAbsolutePath();
    }

    ParameterizedRunnable<JpsModel> initializer = null;
    String scriptPath = initializationScriptPath;
    if (scriptPath != null) {
      System.err.println("--script argument is deprecated, use --config instead or configure options in code and call Standalone.runBuild method");
      File scriptFile = new File(scriptPath);
      if (!scriptFile.isFile()) {
        System.err.println("Script '" + scriptPath + "' not found");
        return 1;
      }
      initializer = new GroovyModelInitializer(scriptFile);
    }

    if (modules.length == 0 && artifacts.length == 0 && !allModules && !allArtifacts) {
      System.err.println("Nothing to compile: at least one of --modules, --artifacts, --all-modules or --all-artifacts parameters must be specified");
      return 1;
    }

    JpsModelLoaderImpl loader = new JpsModelLoaderImpl(projectPath, globalOptionsPath, false, initializer);
    Set<String> modulesSet = Set.of(modules);
    List<String> artifactsList = Arrays.asList(artifacts);
    File dataStorageRoot = cacheDirPath == null ? Utils.getDataStorageRoot(projectPath) : new File(cacheDirPath);

    ConsoleMessageHandler consoleMessageHandler = new ConsoleMessageHandler();
    long start = System.nanoTime();
    try {
      runBuild(loader, dataStorageRoot, !incremental, modulesSet, allModules, artifactsList, allArtifacts, true, consoleMessageHandler);
    }
    catch (Throwable t) {
      System.err.println("Internal error: " + t.getMessage());
      t.printStackTrace();
    }
    System.out.println("Build finished in " + Utils.formatDuration(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)));
    return consoleMessageHandler.hasErrors() ? 1 : 0;
  }

  public static void runBuild(@NotNull JpsModelLoader loader, @NotNull File dataStorageRoot, boolean forceBuild,
                              @NotNull Set<String> modulesSet, boolean allModules,
                              @NotNull List<String> artifactsList, boolean includeTests,
                              @NotNull MessageHandler messageHandler) throws Exception {
    runBuild(loader, dataStorageRoot, forceBuild, modulesSet, allModules, artifactsList, false, includeTests, messageHandler);
  }

  public static void runBuild(@NotNull JpsModelLoader loader,
                              @NotNull File dataStorageRoot,
                              boolean forceBuild,
                              @NotNull Set<String> modulesSet,
                              boolean allModules,
                              @NotNull List<String> artifactsList,
                              boolean allArtifacts,
                              boolean includeTests,
                              @NotNull MessageHandler messageHandler) throws Exception {
    List<TargetTypeBuildScope> scopes = new ArrayList<>();
    for (JavaModuleBuildTargetType type : JavaModuleBuildTargetType.ALL_TYPES) {
      if (includeTests || !type.isTests()) {
        TargetTypeBuildScope.Builder builder = TargetTypeBuildScope.newBuilder().setTypeId(type.getTypeId()).setForceBuild(forceBuild);
        if (allModules) {
          scopes.add(builder.setAllTargets(true).build());
        }
        else if (!modulesSet.isEmpty()) {
          scopes.add(builder.addAllTargetId(modulesSet).build());
        }
      }
    }

    TargetTypeBuildScope.Builder builder = TargetTypeBuildScope.newBuilder()
      .setTypeId(ArtifactBuildTargetType.INSTANCE.getTypeId())
      .setForceBuild(forceBuild);

    if (allArtifacts) {
      scopes.add(builder.setAllTargets(true).build());
    }
    else if (!artifactsList.isEmpty()) {
      scopes.add(builder.addAllTargetId(artifactsList).build());
    }

    runBuild(loader, dataStorageRoot.toPath(), Collections.emptyMap(), messageHandler, scopes, true, CanceledStatus.NULL);
  }

  /**
   * @deprecated Use {@link #runBuild(JpsModelLoader, Path, Map, MessageHandler, List, boolean, CanceledStatus)}
   */
  @Deprecated
  public static void runBuild(@NotNull JpsModelLoader loader,
                              @NotNull File dataStorageRoot,
                              @NotNull MessageHandler messageHandler,
                              @NotNull List<TargetTypeBuildScope> scopes,
                              boolean includeDependenciesToScope) throws Exception {
    runBuild(loader,
             dataStorageRoot.toPath(),
             Collections.emptyMap(),
             messageHandler,
             scopes,
             includeDependenciesToScope,
             CanceledStatus.NULL);
  }

  /**
   * @deprecated Use {@link #runBuild(JpsModelLoader, Path, Map, MessageHandler, List, boolean, CanceledStatus)}
   */
  @Deprecated
  public static void runBuild(@NotNull JpsModelLoader loader, @NotNull File dataStorageRoot,
                              @NotNull Map<String, String> buildParameters,
                              @NotNull MessageHandler messageHandler, @NotNull List<TargetTypeBuildScope> scopes,
                              boolean includeDependenciesToScope) throws Exception {
    runBuild(loader, dataStorageRoot.toPath(), buildParameters, messageHandler, scopes, includeDependenciesToScope, CanceledStatus.NULL);
  }

  /**
   * @deprecated Use {@link #runBuild(JpsModelLoader, Path, Map, MessageHandler, List, boolean, CanceledStatus)}
   */
  @Deprecated
  public static void runBuild(@NotNull JpsModelLoader loader,
                                @NotNull File dataStorageRoot,
                                @NotNull Map<String, String> buildParameters,
                                @NotNull MessageHandler messageHandler,
                                @NotNull List<TargetTypeBuildScope> scopes,
                                boolean includeDependenciesToScope,
                                @NotNull CanceledStatus canceledStatus) throws Exception {
    runBuild(loader, dataStorageRoot.toPath(), buildParameters, messageHandler, scopes, includeDependenciesToScope, canceledStatus);
  }

  public static void runBuild(@NotNull JpsModelLoader loader,
                              @NotNull Path dataStorageRoot,
                              @NotNull Map<String, String> buildParameters,
                              @NotNull MessageHandler messageHandler,
                              @NotNull List<TargetTypeBuildScope> scopes,
                              boolean includeDependenciesToScope,
                              @NotNull CanceledStatus canceledStatus) throws Exception {
    final LowMemoryWatcherManager memWatcher = new LowMemoryWatcherManager(SharedThreadPool.getInstance());
    final BuildRunner buildRunner = new BuildRunner(loader);
    buildRunner.setBuilderParams(buildParameters);
    ProjectDescriptor descriptor = buildRunner.load(messageHandler, dataStorageRoot, new BuildFSState(true));
    try {
      buildRunner.runBuild(descriptor, canceledStatus, messageHandler, BuildType.BUILD, scopes, includeDependenciesToScope);
    }
    finally {
      descriptor.release();
      memWatcher.shutdown();
    }
  }

  private static final class ConsoleMessageHandler implements MessageHandler {
    private boolean hasErrors = false;

    @Override
    public void processMessage(BuildMessage msg) {
      String messageText;
      if (msg instanceof CompilerMessage) {
        CompilerMessage compilerMessage = (CompilerMessage) msg;
        if (compilerMessage.getSourcePath() == null) {
          messageText = msg.getMessageText();
        }
        else if (compilerMessage.getLine() < 0) {
          messageText = compilerMessage.getSourcePath() + ": " +  msg.getMessageText();
        }
        else {
          messageText = compilerMessage.getSourcePath() + "(" + compilerMessage.getLine() + ":" + compilerMessage.getColumn() + "): " +  msg.getMessageText();
        }
      }
      else {
        messageText = msg.getMessageText();
      }
      if (messageText.isEmpty()) return;
      if (msg.getKind() == BuildMessage.Kind.ERROR) {
        System.err.println("Error: " + messageText);
        hasErrors = true;
      }

      else if (msg.getKind() != BuildMessage.Kind.PROGRESS || !messageText.startsWith("Compiled") && !messageText.startsWith("Copying")) {
        System.out.println(messageText);
      }
    }

    public boolean hasErrors() {
      return hasErrors;
    }
  }
}
