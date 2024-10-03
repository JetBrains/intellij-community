// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.startup.multiProcess;

import com.intellij.openapi.application.PathCustomizer;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.impl.P3SupportInstaller;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.util.UriUtilKt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * An implementation of {@link PathCustomizer} which configures separate config, system and log paths for the frontend variant of the IDE.
 * This is needed to allow running multiple frontend processes for the IDE in parallel with the IDE process.
 */
@SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod", "FieldCanBeLocal", "UseOfSystemOutOrSystemErr"})
@ApiStatus.Experimental
public final class FrontendProcessPathCustomizer implements PathCustomizer {
  /**
   * Name of a file which is created in the plugins directory to indicate the fact that compatible plugins from the full IDE were migrated 
   * to the frontend.
   */
  public static final String PLUGINS_MIGRATED_MARKER = "frontend-plugins-migrated.txt";
  private static final String LOCK_FILE_NAME = "process.lock";

  private static final Set<String> FILES_TO_KEEP = ContainerUtil.newHashSet(
    LOCK_FILE_NAME,
    ".pid", // Required by PerformanceWatcherImpl to report native crashes
    ".appinfo" // Required by PerformanceWatcherImpl to report native crashes
  );

  // Leave the folder locked until we exit. Store reference to keep CleanerFactory from releasing the file channel.
  @SuppressWarnings("unused") private static FileLock ourConfigLock;
  private static volatile boolean enabled;

  @Override
  public CustomPaths customizePaths(@NotNull List<String> args) {
    updatePathSelectorForCommunityEditions(args);
    
    Path newConfig;
    Path basePerProcessDir = getFolderForPerProcessData();

    int directoryCounter = 0;
    while (true) {
      newConfig = basePerProcessDir.resolve("per_process_config_" + directoryCounter);

      FileLock configLock = tryLockDirectory(newConfig);
      if (configLock != null) {
        ourConfigLock = configLock;
        break;
      }

      if (directoryCounter > 1000) {
        System.err.println("Can't lock temp directories in " + basePerProcessDir);
        return null;
      }

      directoryCounter++;
    }

    Path newSystem = basePerProcessDir.resolve("per_process_system_" + directoryCounter);
    Path baseLogDir = getBaseLogDir();
    Path newLog = computeLogDirPath(baseLogDir, directoryCounter);
    if (newLog == null) {
      System.err.println("Can't create log directory in " + baseLogDir);
      return null;
    }
    cleanDirectory(newConfig);
    cleanDirectory(newSystem);

    String originalPluginsPath = PathManager.getPluginsPath();
    boolean customizePluginsPath = useCustomPluginsPath(originalPluginsPath);
    String pluginsPath = customizePluginsPath ? originalPluginsPath + File.separator + "frontend" : originalPluginsPath;
    boolean migratePlugins = customizePluginsPath && !Files.exists(Paths.get(pluginsPath, PLUGINS_MIGRATED_MARKER));
    PerProcessPathCustomization.prepareConfig(newConfig, PathManager.getConfigDir(), migratePlugins);

    Path startupScriptDir = PerProcessPathCustomization.getStartupScriptDir().resolve("frontend");
    P3SupportInstaller.INSTANCE.installPerProcessInstanceSupportImplementation(new ClientP3Support());
    enabled = true;
    return new CustomPaths(newConfig.toString(), newSystem.toString(), pluginsPath, newLog.toString(), startupScriptDir);
  }

  /**
   * Currently, we use the same frontend distribution for IntelliJ IDEA Community and Ultimate, and for PyCharm Community and Professional.
   * To use the proper settings directory, this method updates the path selector based on the product code specified in the join link in
   * the command line arguments. This won't be needed when we use different frontend distributions for different editions (RDCT-1474).
   */
  private static void updatePathSelectorForCommunityEditions(@NotNull List<String> args) {
    String pathsSelector = PathManager.getPathsSelector();
    if (pathsSelector == null) return;
    
    String ideaUltimateSelector = "IntelliJIdea";
    String pycharmProfessionalSelector = "PyCharm";
    String pycharmCommunitySelector = "PyCharmCE";
    boolean isIdeaUltimateInstallation = pathsSelector.startsWith(ideaUltimateSelector);
    boolean isPyCharmProfessionalInstallation = pathsSelector.startsWith(pycharmProfessionalSelector) && 
                                               !pathsSelector.startsWith(pycharmCommunitySelector);
    if (!isIdeaUltimateInstallation && !isPyCharmProfessionalInstallation || args.size() < 2 || !"thinClient".equals(args.get(0))) {
      return;
    }

    try {
      var uri = new URI(args.get(1));
      var productCode = UriUtilKt.getFragmentParameters(uri).get("p");
      if (productCode.equals("IC") && isIdeaUltimateInstallation) {
        PathManager.setPathSelector("IdeaIC" + pathsSelector.substring(ideaUltimateSelector.length()));
      }
      else if (productCode.equals("PC") && isPyCharmProfessionalInstallation) {
        PathManager.setPathSelector(pycharmCommunitySelector + pathsSelector.substring(pycharmProfessionalSelector.length()));
      }
    }
    catch (URISyntaxException e) {
      System.err.println("Failed to update path selector: " + e);
    }
  }

  private static @NotNull Path getFolderForPerProcessData() {
    String pathsSelector = PathManager.getPathsSelector();
    if (pathsSelector != null && !isGenericJetBrainsClient(pathsSelector)) {
      return PathManager.getSystemDir().resolve("frontend");
    }
    return Paths.get(PathManager.getTempPath());
  }

  private static boolean isGenericJetBrainsClient(String pathsSelector) {
    //this won't be needed as soon as we stop building a 'generic' variant of the frontend (GTW-8851)
    return pathsSelector.startsWith("JetBrainsClient");
  }

  private static @NotNull Path getBaseLogDir() {
    String baseLogDirPath = PathManager.getLogPath();
    String pathsSelector = PathManager.getPathsSelector();
    if (pathsSelector != null && baseLogDirPath.equals(PathManager.getDefaultLogPathFor(pathsSelector)) &&
        !isGenericJetBrainsClient(pathsSelector)) {
      return Paths.get(baseLogDirPath, "frontend");
    }
    return Paths.get(baseLogDirPath);
  }

  private static boolean useCustomPluginsPath(String originalPluginsPath) {
    String pathsSelector = PathManager.getPathsSelector();
    return pathsSelector != null && !isGenericJetBrainsClient(pathsSelector) &&
           originalPluginsPath.equals(PathManager.getDefaultPluginPathFor(pathsSelector));
  }

  public static boolean isEnabled() {
    return enabled;
  }

  private static @Nullable Path computeLogDirPath(Path baseLogDir, int directoryCounter) {
    String namePrefix = DateTimeFormatter.ofPattern("yyyy-MM-dd_'at'_HH-mm-ss").format(LocalDateTime.now());
    String nameSuffix = directoryCounter > 0 ? "_" + directoryCounter : "";
    Path logPath = baseLogDir.resolve(namePrefix + nameSuffix);
    if (!Files.exists(logPath)) {
      return logPath;
    }
    
    /* since this process locks directoryCounter, the log directory with the suggested name may exist only if it was left by the previous 
       process, so let's choose a different name */
    for (int i = 1; i < 1000; i++) {
      Path newLogPath = baseLogDir.resolve(String.format("%s-%03d%s", namePrefix, i, nameSuffix));
      if (!Files.exists(newLogPath)) {
        return newLogPath;
      }
    }
    return null;
  }

  @Nullable
  private static FileLock tryLockDirectory(@NotNull Path directory) {
    try {
      Files.createDirectories(directory);

      Path lockFile = directory.resolve(LOCK_FILE_NAME);

      //noinspection resource
      FileChannel fc = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
      return fc.tryLock();
    }
    catch (IOException ignore) {
      return null;
    }
  }

  private static void cleanDirectory(@NotNull Path directory) {
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
      for (Path path : stream) {
        if (!FILES_TO_KEEP.contains(path.getFileName().toString())) {
          try {
            NioFiles.deleteRecursively(path);
          }
          catch (IOException e) {
            System.err.println("Failed to delete " + path + ": " + e);
          }
        }
      }
    }
    catch (NoSuchFileException ignore) {
    }
    catch (IOException e) {
      System.err.println("Failed to clean directory " + directory + ": " + e);
    }
  }
}
