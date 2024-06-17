// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.startup.multiProcess;

import com.intellij.openapi.application.CustomConfigMigrationOption;
import com.intellij.openapi.application.PathCustomizer;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.impl.P3SupportInstaller;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

import static com.intellij.idea.Main.customTargetDirectoryToImportConfig;
import static com.intellij.idea.Main.isConfigImportNeeded;

/**
 * An implementation of {@link PathCustomizer} which configures separate config, system and log paths for each process started from the IDE
 * distribution.
 * This is needed to allow running multiple processes of the same IDE.
 */
@SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod", "FieldCanBeLocal", "UseOfSystemOutOrSystemErr"})
@ApiStatus.Experimental
public final class PerProcessPathCustomizer implements PathCustomizer {
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
  public CustomPaths customizePaths() {
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
    boolean migratePlugins = customizePluginsPath && !Files.exists(Paths.get(pluginsPath));
    prepareConfig(newConfig, PathManager.getConfigDir(), migratePlugins);

    Path startupScriptDir = isInFrontendMode() ? getStartupScriptDir().resolve("frontend") : getStartupScriptDir();
    P3SupportInstaller.INSTANCE.installPerProcessInstanceSupportImplementation(new ClientP3Support());
    enabled = true;
    return new CustomPaths(newConfig.toString(), newSystem.toString(), pluginsPath, newLog.toString(), startupScriptDir);
  }

  private static @NotNull Path getFolderForPerProcessData() {
    if (isInFrontendMode()) {
      String pathsSelector = PathManager.getPathsSelector();
      if (pathsSelector != null && !pathsSelector.startsWith("JetBrainsClient")) {
        return PathManager.getSystemDir().resolve("frontend");
      }
    }
    return Paths.get(PathManager.getTempPath());
  }

  private static @NotNull Path getBaseLogDir() {
    String baseLogDirPath = PathManager.getLogPath();
    String pathsSelector = PathManager.getPathsSelector();
    if (pathsSelector != null && baseLogDirPath.equals(PathManager.getDefaultLogPathFor(pathsSelector)) && isInFrontendMode() &&
        !pathsSelector.startsWith("JetBrainsClient")) {
      return Paths.get(baseLogDirPath, "frontend");
    }
    return Paths.get(baseLogDirPath);
  }

  private static boolean useCustomPluginsPath(String originalPluginsPath) {
    if (!isInFrontendMode()) return false;
    
    String pathsSelector = PathManager.getPathsSelector();
    if (pathsSelector == null || pathsSelector.startsWith("JetBrainsClient")) return false;

    return originalPluginsPath.equals(PathManager.getDefaultPluginPathFor(pathsSelector));
  }

  //todo move logic specific for frontend processes to a separate class
  private static boolean isInFrontendMode() {
    return "frontend".equals(System.getProperty("intellij.platform.product.mode"));
  }

  public static boolean isEnabled() {
    return enabled;
  }

  public static Path getStartupScriptDir() {
    return PathManager.getSystemDir().resolve("startup-script");
  }

  public static void prepareConfig(Path newConfig, Path oldConfigPath, boolean migratePlugins) {
    try {
      if (isConfigImportNeeded(oldConfigPath)) {
        customTargetDirectoryToImportConfig = oldConfigPath;
      }
      else if (migratePlugins) {
        // The config directory exists, but the plugins for the frontend process weren't migrated,
        // so we trigger importing of config from the local IDE to migrate the plugins.
        customTargetDirectoryToImportConfig = newConfig;
        new CustomConfigMigrationOption.MigrateFromCustomPlace(oldConfigPath).writeConfigMarkerFile(newConfig);
      }
      CustomConfigFiles.prepareConfigDir(newConfig, oldConfigPath);
    }
    catch (IOException e) {
      System.err.println("Failed to prepare config directory " + newConfig);
      //noinspection CallToPrintStackTrace
      e.printStackTrace();
    }
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
