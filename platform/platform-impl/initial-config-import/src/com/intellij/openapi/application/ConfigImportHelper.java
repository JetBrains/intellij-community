// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.configurationStore.StoreUtilKt;
import com.intellij.diagnostic.VMOptions;
import com.intellij.ide.BootstrapBundle;
import com.intellij.ide.ConfigImportOptions;
import com.intellij.ide.ConfigImportSettings;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.ImportOldConfigsPanel;
import com.intellij.ide.ImportOldConfigsUsagesCollector;
import com.intellij.ide.SpecialConfigFiles;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.ide.plugins.BrokenPluginFileKt;
import com.intellij.ide.plugins.DisabledPluginsState;
import com.intellij.ide.plugins.ExpiredPluginsState;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginDescriptorLoader;
import com.intellij.ide.plugins.PluginDescriptorLoadingResult;
import com.intellij.ide.plugins.PluginInitContextSelectPluginsToLoadKt;
import com.intellij.ide.plugins.PluginInstaller;
import com.intellij.ide.plugins.PluginMainDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.PluginNode;
import com.intellij.ide.plugins.PluginVersionIsSuperseded;
import com.intellij.ide.plugins.ProductPluginInitContext;
import com.intellij.ide.plugins.marketplace.MarketplaceRequests;
import com.intellij.ide.plugins.newui.PluginUiModel;
import com.intellij.ide.startup.StartupActionScriptManager;
import com.intellij.ide.startup.StartupActionScriptManager.ActionCommand;
import com.intellij.ide.ui.laf.LookAndFeelThemeAdapterKt;
import com.intellij.idea.AppMode;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.application.migrations.BigDataToolsMigration253;
import com.intellij.openapi.application.migrations.CwmMigration261;
import com.intellij.openapi.application.migrations.Localization242;
import com.intellij.openapi.application.migrations.NotebooksMigration242;
import com.intellij.openapi.application.migrations.SpaceMigration252;
import com.intellij.openapi.application.migrations.VcsPluginsMigration261;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.keymap.impl.KeymapManagerImpl;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.impl.P3SupportKt;
import com.intellij.openapi.project.impl.shared.P3DynamicPluginSynchronizerKt;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Predicates;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager;
import com.intellij.openapi.util.text.NaturalComparator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.platform.ide.bootstrap.SplashManagerKt;
import com.intellij.platform.ide.bootstrap.StartupErrorReporter;
import com.intellij.ui.AppUIUtilKt;
import com.intellij.util.PlatformUtils;
import com.intellij.util.Restarter;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.Decompressor;
import com.intellij.util.system.OS;
import com.intellij.util.text.VersionComparatorUtil;
import com.intellij.util.ui.IoErrorText;
import kotlin.Unit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.SwingUtilities;
import java.awt.Dialog;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PropertyResourceBundle;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.intellij.ide.plugins.BundledPluginsStateKt.BUNDLED_PLUGINS_FILENAME;

@ApiStatus.Internal
@SuppressWarnings("UseOptimizedEelFunctions")
public final class ConfigImportHelper {
  public static final String IMPORT_FROM_ENV_VAR = "JB_IMPORT_SETTINGS_FROM";
  public static final Pattern SELECTOR_PATTERN = Pattern.compile("\\.?(\\D+)(\\d+(?:\\.\\d+)*)");

  private static final String SHOW_IMPORT_CONFIG_DIALOG_PROPERTY = "idea.initially.ask.config";
  private static final String UPDATE_ONLY_INCOMPATIBLE_PLUGINS_PROPERTY = "idea.config.import.update.incompatible.plugins.only"; // if true, only incompatible will be updated

  private static final String CONFIG = "config";
  private static final String BIN = "bin";
  private static final String CONTENTS = "Contents";
  private static final String PLIST = "Info.plist";
  private static final String PLUGINS = "plugins";
  private static final String SYSTEM = "system";
  private static final Set<String> SESSION_FILES = Set.of(
    SpecialConfigFiles.LOCK_FILE, SpecialConfigFiles.PORT_LOCK_FILE, SpecialConfigFiles.TOKEN_FILE, SpecialConfigFiles.USER_WEB_TOKEN,
    InitialConfigImportState.CUSTOM_MARKER_FILE_NAME);
  private static final String[] OPTIONS = {
    PathManager.OPTIONS_DIRECTORY + '/' + StoragePathMacros.NON_ROAMABLE_FILE,
    PathManager.OPTIONS_DIRECTORY + '/' + GeneralSettings.IDE_GENERAL_XML,
    PathManager.OPTIONS_DIRECTORY + "/options.xml"};

  private static final long PLUGIN_UPDATES_TIMEOUT_MS = 7000L;
  private static final long BROKEN_PLUGINS_TIMEOUT_MS = 3000L;

  private ConfigImportHelper() { }

  public static void importConfigsTo(
    boolean veryFirstStartOnThisComputer,
    @NotNull Path newConfigDir,
    @NotNull List<String> args,
    @NotNull Logger log
  ) {
    log.info("Importing configs to '" + newConfigDir + "'; veryFirstStart=" + veryFirstStartOnThisComputer);
    System.setProperty(InitialConfigImportState.FIRST_SESSION_KEY, Boolean.TRUE.toString());

    var migrationOption = CustomConfigMigrationOption.readCustomConfigMigrationOptionAndRemoveMarkerFile(newConfigDir);
    log.info("Custom migration option: " + migrationOption);
    var importSettings = findCustomConfigImportSettings();
    log.info("Custom import settings: " + importSettings);

    if (migrationOption instanceof CustomConfigMigrationOption.SetProperties sp) {
      var properties = sp.getProperties();
      log.info("Setting system properties after restart: " + properties);
      for (var property : properties) System.setProperty(property.getFirst(), property.getSecond());
      return;
    }
    else if (migrationOption instanceof CustomConfigMigrationOption.MigratePluginsFromCustomPlace migratePluginsOption) {
      var oldConfigDir = migratePluginsOption.getConfigLocation();
      if (isConfigDirectory(oldConfigDir)) {
        var oldPluginsDir = computeOldPluginsDir(oldConfigDir, null);
        var newPluginsDir = newConfigDir.getFileSystem().getPath(PathManager.getPluginsDir().toString());
        var importOptions = createConfigImportOptions(importSettings, migrationOption, log);
        try {
          migratePlugins(oldPluginsDir, oldConfigDir, newPluginsDir, newConfigDir, importOptions, Predicates.alwaysFalse());
        }
        catch (IOException e) {
          log.warn(e);
        }
      }
      else {
        logRejectedConfigDirectory(log, "Custom plugins location", oldConfigDir);
      }
      return;
    }

    var tempBackup = (Path)null;
    var vmOptionFileChanged = false;

    try {
      var oldConfigDirAndOldIdePath = (Pair<Path, @Nullable Path>)null;
      var vmOptionsLines = (List<String>)null;
      var currentlyDisabledPlugins = (List<String>)null;
      var importScenarioStatistics = (ImportOldConfigsUsagesCollector.InitialImportScenario)null;
      var wizardEnabled = InitialConfigImportState.isStartupWizardEnabled();

      if (
        migrationOption instanceof CustomConfigMigrationOption.MigrateFromCustomPlace ||
        migrationOption instanceof CustomConfigMigrationOption.StartWithCleanConfig
      ) {
        vmOptionFileChanged = doesVmOptionsFileExist(newConfigDir);
        try {
          if (migrationOption instanceof CustomConfigMigrationOption.MigrateFromCustomPlace mcp) {
            oldConfigDirAndOldIdePath = findConfigDirectoryByPath(mcp.getLocation());
            if (oldConfigDirAndOldIdePath == null) {
              logRejectedConfigDirectory(log, "Custom location", mcp.getLocation());
            }
            else {
              if (doesVmOptionsFileExist(newConfigDir) && !vmOptionsRequiresMerge(oldConfigDirAndOldIdePath.first, newConfigDir, log)) {
                //save old lines for the new file
                vmOptionsLines = Files.readAllLines(newConfigDir.resolve(VMOptions.getFileName()), VMOptions.getFileCharset());
                vmOptionFileChanged = false;
              }
              var disabledPluginsFileName = P3SupportKt.processPerProjectSupport().getDisabledPluginsFileName();
              if (Files.isRegularFile(newConfigDir.resolve(disabledPluginsFileName))) {
                currentlyDisabledPlugins = Files.readAllLines(newConfigDir.resolve(disabledPluginsFileName));
              }
              else if (
                !DisabledPluginsState.DISABLED_PLUGINS_FILENAME.equals(disabledPluginsFileName) &&
                Files.isRegularFile(newConfigDir.resolve(DisabledPluginsState.DISABLED_PLUGINS_FILENAME))
              ) {
                currentlyDisabledPlugins = Files.readAllLines(newConfigDir.resolve(DisabledPluginsState.DISABLED_PLUGINS_FILENAME));
              }
            }
            tempBackup = backupAndDeleteCurrentConfig(newConfigDir, log, importSettings);
            importScenarioStatistics = ImportOldConfigsUsagesCollector.InitialImportScenario.IMPORT_SETTINGS_ACTION;
          }
          else {
            tempBackup = backupAndDeleteCurrentConfig(newConfigDir, log, importSettings);
            importScenarioStatistics = ImportOldConfigsUsagesCollector.InitialImportScenario.RESTORE_DEFAULT_ACTION;
          }
        }
        catch (IOException e) {
          log.error("Couldn't backup current config or delete current config directory", e);
        }
      }
      else if (wizardEnabled && (!PlatformUtils.isJetBrainsClient() && System.getProperty(PathManager.PROPERTY_CONFIG_PATH) != null || PluginManagerCore.isRunningFromSources())) {
        log.info("skipping import because of non-standard config directory");
      }
      else {
        var candidateDirectories = findInheritedDirectory(newConfigDir, System.getenv(IMPORT_FROM_ENV_VAR), importSettings, args, log);
        if (candidateDirectories == null) {
          candidateDirectories = findConfigDirectories(newConfigDir, importSettings, args);
          log.info("candidates: " + candidateDirectories.directories);
        }
        var bestCandidate = candidateDirectories.directories.isEmpty() ? null : candidateDirectories.directories.getFirst();
        var showImportDialog = System.getProperty(SHOW_IMPORT_CONFIG_DIALOG_PROPERTY);

        if (Boolean.parseBoolean(showImportDialog) && !wizardEnabled) {
          log.info("import dialog requested explicitly");
          oldConfigDirAndOldIdePath = showDialogAndGetOldConfigPath(candidateDirectories.getPaths());
          importScenarioStatistics = ImportOldConfigsUsagesCollector.InitialImportScenario.SHOW_DIALOG_REQUESTED_BY_PROPERTY;
        }
        else if (bestCandidate != null && !isConfigOld(bestCandidate.second)) {
          oldConfigDirAndOldIdePath = new Pair<>(bestCandidate.first, null);
          log.info("auto-import");
        }
        else {
          log.info("no suitable configs found");
          if (!(veryFirstStartOnThisComputer || wizardEnabled || "never".equals(showImportDialog) || AppMode.isRemoteDevHost())) {
            oldConfigDirAndOldIdePath = showDialogAndGetOldConfigPath(candidateDirectories.getPaths());
            importScenarioStatistics = ImportOldConfigsUsagesCollector.InitialImportScenario.SHOW_DIALOG_NO_CONFIGS_FOUND;
          }
        }
      }

      if (oldConfigDirAndOldIdePath != null) {
        var oldConfigDir = oldConfigDirAndOldIdePath.first;
        var oldIdeHome = oldConfigDirAndOldIdePath.second;
        var configImportOptions = createConfigImportOptions(importSettings, migrationOption, log);

        if (importScenarioStatistics == null) {
          importScenarioStatistics = ImportOldConfigsUsagesCollector.InitialImportScenario.IMPORTED_FROM_PREVIOUS_VERSION;
        }

        System.setProperty(InitialConfigImportState.CONFIG_IMPORTED_FROM_PATH, oldConfigDir.toString());

        doImport(oldConfigDir, newConfigDir, oldIdeHome, configImportOptions);

        System.setProperty(InitialConfigImportState.CONFIG_IMPORTED_IN_CURRENT_SESSION_KEY, Boolean.TRUE.toString());

        if (currentlyDisabledPlugins != null) {
          try {
            var newDisablePluginsFile = newConfigDir.resolve(P3SupportKt.processPerProjectSupport().getDisabledPluginsFileName());
            var newDisabledPlugins = new LinkedHashSet<String>();
            if (Files.isRegularFile(newDisablePluginsFile)) {
              newDisabledPlugins.addAll(Files.readAllLines(newDisablePluginsFile, CharsetToolkit.getPlatformCharset()));
            }
            newDisabledPlugins.addAll(currentlyDisabledPlugins);
            Files.write(newDisablePluginsFile, newDisabledPlugins, CharsetToolkit.getPlatformCharset());
            log.info("Disabled plugins file updated with " + newDisabledPlugins.size() + " plugins");
          }
          catch (IOException e) {
            log.warn("Couldn't write disabled plugins file", e);
          }
        }
      }
      else {
        log.info("No configs imported, starting with clean configs at " + newConfigDir);
        if (importScenarioStatistics == null) {
          importScenarioStatistics = ImportOldConfigsUsagesCollector.InitialImportScenario.CLEAN_CONFIGS;
        }
      }

      if (importSettings != null) {
        var oldConfigDir = oldConfigDirAndOldIdePath != null ? oldConfigDirAndOldIdePath.first : null;
        importSettings.importFinished(newConfigDir, oldConfigDir);
      }

      ImportOldConfigsUsagesCollector.INSTANCE.reportImportScenario(importScenarioStatistics);

      if (vmOptionsLines != null) {
        var vmOptionsFile = newConfigDir.resolve(VMOptions.getFileName());
        try {
          Files.write(vmOptionsFile, vmOptionsLines, VMOptions.getFileCharset());
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      else {
        vmOptionFileChanged |= doesVmOptionsFileExist(newConfigDir);
      }
    }
    finally {
      if (tempBackup != null) {
        try {
          new ConfigBackup(newConfigDir).moveToBackup(tempBackup);
        }
        catch (IOException e) {
          log.warn(String.format("Couldn't move the backup of current config from temp dir [%s] to backup dir", tempBackup), e);
        }
      }
    }

    if (vmOptionFileChanged) {
      if (!AppMode.isRemoteDevHost()) {
        if (importSettings == null || importSettings.shouldRestartAfterVmOptionsChange()) {
          log.info("The vmoptions file has changed, restarting...");
          try {
            InitialConfigImportState.writeOptionsForRestart(newConfigDir);
          }
          catch (IOException e) {
            log.error("cannot write config migration marker file to " + newConfigDir, e);
          }
          restart(args);
        }
        else {
          log.info("The vmoptions file has changed, but restart is switched off by " + importSettings);
        }
      }
      else {
        //todo restore restarting for the backend process after GTW-9531 is fixed
        log.warn("The vmoptions file has changed, but the backend process wasn't restarted; custom vmoptions will be used on the next run only");
      }
    }
  }

  private static ConfigImportOptions createConfigImportOptions(
    @Nullable ConfigImportSettings settings,
    @Nullable CustomConfigMigrationOption customMigrationOption,
    Logger log
  ) {
    var configImportOptions = new ConfigImportOptions(log);
    configImportOptions.importSettings = settings;
    configImportOptions.mergeVmOptions = customMigrationOption instanceof CustomConfigMigrationOption.MergeConfigs;
    configImportOptions.headless = AppMode.isRemoteDevHost();  // in remote dev host mode, the UI cannot be shown before the app is initialized
    return configImportOptions;
  }

  private static void logRejectedConfigDirectory(Logger log, String description, Path path) {
    var builder = new StringBuilder().append(description).append(" was detected but not accepted: ").append(path).append(". Content:\n");
    if (Files.isDirectory(path)) {
      for (var child : NioFiles.list(path)) {
        builder.append(" ").append(child.getFileName()).append("\n");
        for (var grandChild : NioFiles.list(child)) {
          builder.append(" |- ").append(grandChild.getFileName()).append("\n");
        }
      }
    }
    else {
      builder.append("not a directory");
    }
    log.info(builder.toString());
  }

  public static @Nullable ConfigImportSettings findCustomConfigImportSettings() {
    try {
      var customProviderName = "com.intellij.openapi.application." + PlatformUtils.getPlatformPrefix() + "ConfigImportSettings";
      var customProviderClass = Class.forName(customProviderName);
      if (ConfigImportSettings.class.isAssignableFrom(customProviderClass)) {
        var constructor = customProviderClass.getDeclaredConstructor();
        try {
          constructor.setAccessible(true);
        }
        catch (SecurityException ignored) { }
        return (ConfigImportSettings)constructor.newInstance();
      }
    }
    catch (Exception ignored) { }
    return null;
  }

  public static boolean isConfigOld(FileTime time) {
    return ChronoUnit.DAYS.between(time.toInstant(), Instant.now()) >= 180;
  }

  private static boolean doesVmOptionsFileExist(Path configDir) {
    return Files.isRegularFile(configDir.resolve(VMOptions.getFileName()));
  }

  private static void restart(List<String> args) {
    if (Restarter.isSupported()) {
      try {
        Restarter.setMainAppArgs(args);
        Restarter.scheduleRestart(false);
      }
      catch (IOException e) {
        StartupErrorReporter.showError(BootstrapBundle.message("restart.failed.title"), e);
      }
      System.exit(0);
    }
    else {
      var title = BootstrapBundle.message("import.settings.title", ApplicationNamesInfo.getInstance().getFullProductName());
      var message = BootstrapBundle.message("import.settings.restart");
      var yes = BootstrapBundle.message("import.settings.restart.now");
      var no = BootstrapBundle.message("import.settings.restart.later");
      //noinspection TestOnlyProblems
      LookAndFeelThemeAdapterKt.setEarlyUiLaF();
      if (Messages.showYesNoDialog(message, title, yes, no, Messages.getQuestionIcon()) == Messages.YES) {
        System.exit(0);
      }
    }
  }

  private static Path backupAndDeleteCurrentConfig(Path currentConfig, Logger log, @Nullable ConfigImportSettings settings) throws IOException {
    return backupCurrentConfig(currentConfig, log, settings, true);
  }

  public static Path backupCurrentConfig(Path currentConfig, Logger log, @Nullable ConfigImportSettings settings) throws IOException {
    return backupCurrentConfig(currentConfig, log, settings, false);
  }

  private static Path backupCurrentConfig(Path currentConfig, Logger log, @Nullable ConfigImportSettings settings, boolean deleteFiles) throws IOException {
    var tempDir = Files.createDirectories(currentConfig.getFileSystem().getPath(System.getProperty("java.io.tmpdir")));
    var tempBackupDir = Files.createTempDirectory(tempDir, currentConfig.getFileName() + "-backup-" + UUID.randomUUID());
    log.info("Backup config from " + currentConfig + " to " + tempBackupDir);
    NioFiles.copyRecursively(currentConfig, tempBackupDir, file -> !shouldSkipFileDuringImport(file, settings));

    if (deleteFiles) {
      deleteCurrentConfigDir(currentConfig, log);
    }

    var pluginDir = currentConfig.getFileSystem().getPath(PathManager.getPluginsDir().toString());
    if (Files.exists(pluginDir) && !pluginDir.startsWith(currentConfig)) {
      var pluginBackup = tempBackupDir.resolve(PLUGINS);
      log.info("Backup plugins dir separately from " + pluginDir + " to " + pluginBackup);
      NioFiles.createDirectories(pluginBackup);
      NioFiles.copyRecursively(pluginDir, pluginBackup);
      if (deleteFiles) {
        NioFiles.deleteRecursively(pluginDir);
      }
    }

    return tempBackupDir;
  }

  private static void deleteCurrentConfigDir(Path currentConfig, Logger log) throws IOException {
    log.debug("Removing current config directory");

    var removedViaCustomizer = false;
    try {
      for (var customizer : ServiceLoader.load(RestoreDefaultConfigCustomizer.class)) {
        log.debug("Found " + customizer);
        customizer.removeCurrentConfigDir(currentConfig);
        removedViaCustomizer = true;
        break;
      }
    }
    catch (Exception e) {
      log.warn("Couldn't remove current config dir using the customizer", e);
    }

    if (!removedViaCustomizer) {
      log.debug("RestoreDefaultConfigCustomizer not found, removing config directory manually...");
      NioFiles.deleteRecursively(currentConfig);
    }
  }

  private static @Nullable Pair<Path, Path> showDialogAndGetOldConfigPath(List<Path> guessedOldConfigDirs) {
    var app = ApplicationManager.getApplication();
    if (app != null && app.isUnitTestMode()) throw new UnsupportedOperationException("Unit test mode");

    //noinspection TestOnlyProblems
    LookAndFeelThemeAdapterKt.setEarlyUiLaF();

    var dialog = new ImportOldConfigsPanel(guessedOldConfigDirs, ConfigImportHelper::findConfigDirectoryByPath);
    dialog.setModalityType(Dialog.ModalityType.TOOLKIT_MODAL);
    AppUIUtilKt.updateAppWindowIcon(dialog);
    SplashManagerKt.hideSplash();
    dialog.setVisible(true);
    var result = dialog.getSelectedFile();
    dialog.dispose();
    return result;
  }

  public static void setSettingsFilter(@NotNull FileChooserDescriptor descriptor) {
    descriptor
      .withFileFilter(file -> FileTypeRegistry.getInstance().isFileOfType(file, ArchiveFileType.INSTANCE))
      .withExtensionFilter(BootstrapBundle.message("import.settings.filter"), "zip", "jar");
  }

  public static boolean isConfigDirectory(@NotNull Path candidate) {
    for (var t : OPTIONS) {
      if (Files.exists(candidate.resolve(t))) {
        return true;
      }
    }
    return false;
  }

  public static final class ConfigDirsSearchResult {
    private final List<? extends Pair<Path, FileTime>> directories;

    private ConfigDirsSearchResult(List<? extends Pair<Path, FileTime>> directories) {
      this.directories = directories;
    }

    public @Unmodifiable @NotNull List<Path> getPaths() {
      return ContainerUtil.map(directories, it -> it.first);
    }

    public @NotNull @NlsSafe String getNameAndVersion(@NotNull Path config) {
      return getNameWithVersion(config);
    }

    public @NotNull List<Path> findRelatedDirectories(@NotNull Path config, boolean forAutoClean) {
      return getRelatedDirectories(config, forAutoClean);
    }
  }

  public static @Nullable FileTime getConfigLastModifiedTime(@NotNull Path configDir) {
    var max = (FileTime)null;
    for (var name : OPTIONS) {
      try {
        var cur = Files.getLastModifiedTime(configDir.resolve(name));
        if (max == null || cur.compareTo(max) > 0) {
          max = cur;
        }
      }
      catch (IOException ignore) { }
    }
    return max;
  }

  public static @Nullable ConfigDirsSearchResult findInheritedDirectory(
    @NotNull Path newConfigDir,
    @Nullable String inheritedPath,
    @Nullable ConfigImportSettings settings,
    @NotNull List<String> args,
    @NotNull Logger log
  ) {
    log.info(IMPORT_FROM_ENV_VAR + "=" + inheritedPath);

    if (inheritedPath != null) {
      try {
        var configDir = newConfigDir.getFileSystem().getPath(inheritedPath).toAbsolutePath().normalize();
        if (configDir.equals(newConfigDir)) {
          log.warn("  ... points to the current settings directory");
        }
        else if (!Files.isDirectory(configDir)) {
          log.warn("  ... points to a non-existing directory");
        }
        else if (settings == null || settings.shouldBeSeenAsImportCandidate(
          configDir,
          getPrefixFromSelector(getNameWithVersion(configDir)),
          settings.getProductsToImportFrom(args)
        )) {
          var pair = new Pair<>(configDir, FileTime.from(Instant.now()));
          return new ConfigDirsSearchResult(List.of(pair));
        }
        else {
          log.info("  ... rejected by " + settings);
        }
      }
      catch (Exception e) {
        log.warn("  ... is not a valid path", e);
      }
    }

    return null;
  }

  public static @NotNull ConfigDirsSearchResult findConfigDirectories(
    @NotNull Path newConfigDir,
    @Nullable ConfigImportSettings settings,
    @NotNull List<String> args
  ) {
    var otherEditionPrefixes = settings != null ? settings.getEditionsToImportFrom() : List.<String>of();
    var otherProductPrefixes = settings != null ? settings.getProductsToImportFrom(args) : List.<String>of();

    var homes = new HashSet<Path>();  // looking for existing config directories ...
    homes.add(newConfigDir.getParent());  // ... in the vicinity of the new config directory
    homes.add(newConfigDir.getFileSystem().getPath(PathManager.getDefaultConfigPathFor("X")).getParent());  // ... in the default location
    var historic = newConfigDir.getFileSystem().getPath(defaultConfigPath("X2019.3"));
    var historicHome = OS.CURRENT == OS.macOS ? historic.getParent() : historic.getParent().getParent();
    homes.add(historicHome);  // ... in the historic location

    var prefix = getPrefixFromSelector(PathManager.getPathsSelector());
    if (prefix == null) prefix = getPrefixFromSelector(getNameWithVersion(newConfigDir));
    if (prefix == null) {
      var productName = ApplicationNamesInfo.getInstance().getFullProductName();
      if (productName != null) prefix = productName.replace(" ", "");
    }
    if (prefix == null) prefix = PlatformUtils.getPlatformPrefix();

    var exactCandidates = new ArrayList<Path>();
    var otherProductCandidates = new ArrayList<Path>();

    for (var home : homes) {
      if (home == null || !Files.isDirectory(home)) {
        continue;
      }

      var dotted = OS.CURRENT != OS.macOS && home == historicHome;

      try (var stream = Files.newDirectoryStream(home)) {
        for (var path : stream) {
          if (!path.equals(newConfigDir) && Files.isDirectory(path)) {
            var name = path.getFileName().toString();
            var pathPrefix = getPrefixFromSelector(getNameWithVersion(path));
            if (nameMatchesPrefixStrictly(name, prefix, dotted)) {
              if (settings == null || settings.shouldBeSeenAsImportCandidate(path, pathPrefix, otherProductPrefixes)) {
                exactCandidates.add(path);
              }
            }
            else if (ContainerUtil.exists(otherEditionPrefixes, other -> nameMatchesPrefixStrictly(name, other, dotted))) {
              if (settings == null || settings.shouldBeSeenAsImportCandidate(path, pathPrefix, otherProductPrefixes)) {
                exactCandidates.add(path);
              }
            }
            else if (ContainerUtil.exists(otherProductPrefixes, other -> nameMatchesPrefixStrictly(name, other, dotted))) {
              if (settings == null || settings.shouldBeSeenAsImportCandidate(path, pathPrefix, otherProductPrefixes)) {
                otherProductCandidates.add(path);
              }
            }
          }
        }
      }
      catch (IOException ignore) { }
    }

    List<Path> candidates;
    if (!exactCandidates.isEmpty()) {
      candidates = exactCandidates;
    }
    else if (!otherProductCandidates.isEmpty()) {
      candidates = otherProductCandidates;
    }
    else {
      return new ConfigDirsSearchResult(List.of());
    }

    var candidatesSorted = new ArrayList<Pair<Path, FileTime>>();
    for (var child : candidates) {
      var config = child.resolve(CONFIG);
      var candidate = Files.isDirectory(config) ? config : child;
      var max = getConfigLastModifiedTime(candidate);
      candidatesSorted.add(new Pair<>(candidate, max != null ? max : FileTime.fromMillis(0)));
    }
    candidatesSorted.sort((o1, o2) -> {
      var diff = o2.second.compareTo(o1.second);
      if (diff == 0) {
        diff = NaturalComparator.INSTANCE.compare(o2.first.toString(), o1.first.toString());
      }
      return diff;
    });

    return new ConfigDirsSearchResult(candidatesSorted);
  }

  private static boolean nameMatchesPrefixStrictly(String name, String prefix, boolean dotted) {
    var strictPrefix = dotted ? '.' + prefix : prefix;
    return StringUtil.startsWithIgnoreCase(name, strictPrefix) &&
           name.length() > strictPrefix.length() &&
           Character.isDigit(name.charAt(strictPrefix.length()));
  }

  private static String getNameWithVersion(Path configDir) {
    var name = configDir.getFileName().toString();
    if (CONFIG.equals(name)) {
      name = Strings.trimStart(configDir.getParent().getFileName().toString(), ".");
    }
    return name;
  }

  private static @Nullable String parseVersionFromConfig(Path configDir) {
    var nameWithVersion = getNameWithVersion(configDir);
    var m = matchNameWithVersion(nameWithVersion);
    return m.matches() ? m.group(1) : null;
  }

  private static @Nullable String getPrefixFromSelector(@Nullable String nameWithSelector) {
    if (nameWithSelector != null) {
      var m = SELECTOR_PATTERN.matcher(nameWithSelector);
      if (m.matches()) {
        return m.group(1);
      }
    }
    return null;
  }

  /**
   * Tries to map a user selection into a valid config directory.
   * Returns a pair of a config directory and an IDE home (when a user pointed to it; {@code null} otherwise).
   */
  public static @Nullable Pair<@NotNull Path, @Nullable Path> findConfigDirectoryByPath(Path selectedDir) {
    if (isConfigDirectory(selectedDir)) {
      return new Pair<>(selectedDir, null);
    }

    var config = selectedDir.resolve(CONFIG);
    if (isConfigDirectory(config)) {
      return new Pair<>(config, null);
    }

    if (Files.isDirectory(selectedDir.resolve(OS.CURRENT == OS.macOS ? CONTENTS : BIN))) {
      var configDir = getSettingsPath(selectedDir, PathManager.PROPERTY_CONFIG_PATH, ConfigImportHelper::defaultConfigPath);
      if (configDir != null && isConfigDirectory(configDir)) {
        return new Pair<>(configDir, selectedDir);
      }
    }

    return null;
  }

  private static @Nullable Path getSettingsPath(Path ideHome, String propertyName, Function<String, String> pathBySelector) {
    var files = new ArrayList<Path>();
    if (OS.CURRENT == OS.macOS) {
      files.add(ideHome.resolve(CONTENTS + '/' + BIN + '/' + PathManager.PROPERTIES_FILE_NAME));
      files.add(ideHome.resolve(CONTENTS + '/' + PLIST));
    }
    else {
      files.add(ideHome.resolve(BIN + '/' + PathManager.PROPERTIES_FILE_NAME));
      var scriptName = ApplicationNamesInfo.getInstance().getScriptName();
      files.add(ideHome.resolve(BIN + '/' + scriptName + ".bat"));
      files.add(ideHome.resolve(BIN + '/' + scriptName + ".sh"));
    }

    // an explicitly specified directory
    for (var file : files) {
      if (Files.isRegularFile(file)) {
        var candidatePath = PathManager.substituteVars(getPropertyFromFile(file, propertyName), ideHome.toString());
        if (candidatePath != null) {
          var candidate = ideHome.getFileSystem().getPath(candidatePath);
          if (Files.isDirectory(candidate)) {
            return candidate.toAbsolutePath();
          }
        }
      }
    }

    // default directory
    for (var file : files) {
      if (Files.isRegularFile(file)) {
        var selector = getPropertyFromFile(file, PathManager.PROPERTY_PATHS_SELECTOR);
        if (selector != null) {
          var candidate = ideHome.getFileSystem().getPath(pathBySelector.apply(selector));
          if (Files.isDirectory(candidate)) {
            return candidate;
          }
        }
      }
    }

    return null;
  }

  private static @Nullable String getPropertyFromFile(Path file, String propertyName) {
    try {
      var fileContent = Files.readString(file);

      var fileName = file.getFileName().toString();
      if (fileName.endsWith(".properties")) {
        var bundle = new PropertyResourceBundle(new StringReader(fileContent));
        return bundle.containsKey(propertyName) ? bundle.getString(propertyName) : null;
      }

      if (fileName.endsWith(".plist")) {
        var propertyValue = findPListKey(propertyName, fileContent);
        if (!Strings.isEmpty(propertyValue)) {
          return propertyValue;
        }
      }

      var propertyValue = findProperty(propertyName, fileContent);
      if (!Strings.isEmpty(propertyValue)) {
        return propertyValue;
      }
    }
    catch (IOException ignored) { }

    return null;
  }

  private static @Nullable String findPListKey(String propertyName, String fileContent) {
    var key = "<key>" + propertyName + "</key>";
    var idx = fileContent.indexOf(key);
    if (idx > 0) {
      idx = fileContent.indexOf("<string>", idx + key.length());
      if (idx != -1) {
        idx += "<string>".length();
        return fixDirName(fileContent.substring(idx, fileContent.indexOf("</string>", idx)));
      }
    }

    return null;
  }

  private static @Nullable String findProperty(String propertyName, String fileContent) {
    var prefix = propertyName + "=";
    var idx = fileContent.indexOf(prefix);
    if (idx >= 0) {
      var configDir = new StringBuilder();
      idx += prefix.length();
      if (fileContent.length() > idx) {
        var quoted = fileContent.charAt(idx) == '"';
        if (quoted) idx++;
        while (
          fileContent.length() > idx &&
          (quoted ? fileContent.charAt(idx) != '"' : fileContent.charAt(idx) != ' ' && fileContent.charAt(idx) != '\t') &&
          fileContent.charAt(idx) != '\n' &&
          fileContent.charAt(idx) != '\r'
        ) {
          configDir.append(fileContent.charAt(idx));
          idx++;
        }
      }
      if (!configDir.isEmpty()) {
        return Path.of(fixDirName(configDir.toString())).toString();
      }
    }

    return null;
  }

  private static String fixDirName(String dir) {
    return OSAgnosticPathUtil.expandUserHome(StringUtil.unquoteString(dir, '"'));
  }

  private static void doImport(Path oldConfigDir, Path newConfigDir, @Nullable Path oldIdeHome, ConfigImportOptions options) {
    var log = options.log;

    if (oldConfigDir.equals(newConfigDir)) {
      log.info("New config directory is the same as the old one, no import needed.");
      return;
    }

    var oldPluginsDir = computeOldPluginsDir(oldConfigDir, oldIdeHome);
    var newPluginsDir = newConfigDir.getFileSystem().getPath(PathManager.getPluginsDir().toString());

    try {
      log.info(String.format(
        "Importing configs: oldConfigDir=[%s], newConfigDir=[%s], oldIdeHome=[%s], oldPluginsDir=[%s], newPluginsDir=[%s]",
        oldConfigDir, newConfigDir, oldIdeHome, oldPluginsDir, newPluginsDir));
      doImport(oldConfigDir, newConfigDir, oldIdeHome, oldPluginsDir, newPluginsDir, options);
    }
    catch (Exception e) {
      log.warn(e);
      var message = BootstrapBundle.message("import.settings.failed", IoErrorText.message(e));
      StartupErrorReporter.showWarning(BootstrapBundle.message("import.settings.failed.title"), message);
    }
  }

  private static Path computeOldPluginsDir(Path oldConfigDir, @Nullable Path oldIdeHome) {
    var oldPluginsDir = oldConfigDir.resolve(PLUGINS);
    if (!Files.isDirectory(oldPluginsDir)) {
      oldPluginsDir = null;
      if (oldIdeHome != null) {
        oldPluginsDir = getSettingsPath(oldIdeHome, PathManager.PROPERTY_PLUGINS_PATH, ConfigImportHelper::defaultPluginsPath);
      }
      if (oldPluginsDir == null) {
        oldPluginsDir = oldConfigDir.getFileSystem().getPath(defaultPluginsPath(getNameWithVersion(oldConfigDir)));
      }
    }
    return oldPluginsDir;
  }

  @VisibleForTesting
  public static void doImport(
    @NotNull Path oldConfigDir,
    @NotNull Path newConfigDir,
    @Nullable Path oldIdeHome,
    @NotNull Path oldPluginsDir,
    @NotNull Path newPluginsDir,
    @NotNull ConfigImportOptions options
  ) throws IOException {
    var log = options.log;
    if (Files.isRegularFile(oldConfigDir)) {
      new Decompressor.Zip(oldConfigDir).extract(newConfigDir);
      return;
    }

    // Copy everything except plugins.
    // The filter prevents web token reuse and accidental overwrite of files already created by this instance (port/lock/tokens etc.).
    Files.walkFileTree(oldConfigDir, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        var blocked = blockImport(dir, oldConfigDir, newConfigDir, oldPluginsDir, options.importSettings);
        return blocked ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        var target = newConfigDir.resolve(oldConfigDir.relativize(file));
        if (options.mergeVmOptions && file.getFileName().toString().equals(VMOptions.getFileName()) && Files.exists(target)) {
          mergeVmOptions(file, target, options.log);
        }
        else if (!blockImport(file, oldConfigDir, newConfigDir, oldPluginsDir, options.importSettings)) {
          NioFiles.createDirectories(target.getParent());
          Files.copy(file, target, LinkOption.NOFOLLOW_LINKS);
        }
        else if (overwriteOnImport(file)) {
          NioFiles.createDirectories(target.getParent());
          Files.copy(file, target, LinkOption.NOFOLLOW_LINKS, StandardCopyOption.REPLACE_EXISTING);
        }
        return FileVisitResult.CONTINUE;
      }
    });
    var disabledPluginsFileName = P3SupportKt.processPerProjectSupport().getDisabledPluginsFileName();
    if (!disabledPluginsFileName.equals(DisabledPluginsState.DISABLED_PLUGINS_FILENAME) &&
        Files.exists(oldConfigDir.resolve(DisabledPluginsState.DISABLED_PLUGINS_FILENAME)) && !Files.exists(newConfigDir.resolve(disabledPluginsFileName))) {
      Files.copy(oldConfigDir.resolve(DisabledPluginsState.DISABLED_PLUGINS_FILENAME), newConfigDir.resolve(disabledPluginsFileName));
    }

    var actionCommands = loadStartupActionScript(oldConfigDir, oldIdeHome, oldPluginsDir);

    // copying plugins, unless the target directory is not empty (the plugin manager will sort out incompatible ones)
    if (!isEmptyDirectory(newPluginsDir)) {
      log.info("non-empty plugins directory: " + newPluginsDir);
    }
    else {
      var hasPendingUpdate = Files.isDirectory(oldPluginsDir) ?
        collectPendingPluginUpdates(actionCommands, oldPluginsDir.getFileSystem(), options.log) :
        (Predicate<IdeaPluginDescriptor>)(__ -> false);
      migratePlugins(oldPluginsDir, oldConfigDir, newPluginsDir, newConfigDir, options, hasPendingUpdate);
    }

    migrateLocalization(oldConfigDir, oldPluginsDir);

    if (OS.CURRENT == OS.macOS && (PlatformUtils.isIntelliJ() || "AndroidStudio".equals(PlatformUtils.getPlatformPrefix()))) {
      setKeymapIfNeeded(oldConfigDir, newConfigDir, log);
    }

    // applying prepared updates to copied plugins
    StartupActionScriptManager.executeActionScriptCommands(actionCommands, oldPluginsDir, newPluginsDir);

    updateVMOptions(newConfigDir, oldConfigDir, log);
  }

  public static void migrateLocalization(@NotNull Path oldConfigDir, @NotNull Path oldPluginsDir) {
    Localization242.INSTANCE.enableL10nIfPluginInstalled(parseVersionFromConfig(oldConfigDir), oldPluginsDir);
  }

  private static List<ActionCommand> loadStartupActionScript(Path oldConfigDir, @Nullable Path oldIdeHome, Path oldPluginsDir) throws IOException {
    if (Files.isDirectory(oldPluginsDir)) {
      var oldSystemDir = oldConfigDir.getParent().resolve(SYSTEM);
      if (!Files.isDirectory(oldSystemDir)) {
        oldSystemDir = null;
        if (oldIdeHome != null) {
          oldSystemDir = getSettingsPath(oldIdeHome, PathManager.PROPERTY_SYSTEM_PATH, ConfigImportHelper::defaultSystemPath);
        }
        if (oldSystemDir == null) {
          oldSystemDir = oldConfigDir.getFileSystem().getPath(defaultSystemPath(getNameWithVersion(oldConfigDir)));
        }
      }
      var script = oldSystemDir.resolve(PLUGINS + '/' + StartupActionScriptManager.ACTION_SCRIPT_FILE);  // PathManager#getPluginTempPath
      if (Files.isRegularFile(script)) {
        return StartupActionScriptManager.loadActionScript(script);
      }
    }
    return List.of();
  }

  public static void migratePlugins(
    @NotNull Path oldPluginsDir,
    @NotNull Path oldConfigDir,
    @NotNull Path newPluginsDir,
    @NotNull Path newConfigDir,
    @NotNull ConfigImportOptions options,
    @NotNull Predicate<IdeaPluginDescriptor> hasPendingUpdate
  ) throws IOException {
    var log = options.log;

    var pluginsToMigrate = new ArrayList<IdeaPluginDescriptor>();
    var pluginsToDownload = new ArrayList<IdeaPluginDescriptor>();

    var brokenPluginVersions = fetchBrokenPluginsFromMarketplace(options, newConfigDir);
    if (!collectPluginsToMigrate(oldPluginsDir, options, brokenPluginVersions, pluginsToMigrate, pluginsToDownload)) {
      log.info("Error loading list of plugins from old dir, migrating entire plugin directory");
      NioFiles.copyRecursively(oldPluginsDir, newPluginsDir);
      return;
    }

    if (options.importSettings != null) {
      options.importSettings.processPluginsToMigrate(
        newConfigDir, oldConfigDir, oldPluginsDir, options, brokenPluginVersions, pluginsToMigrate, pluginsToDownload
      );
    }

    if (!PlatformUtils.isJetBrainsClient()) {
      /* The plugins for the frontend process are stored in a 'frontend' subdirectory.
         If a new version of a regular IDE is started, we need to store the frontend plugins from the previous version somewhere in
         the new plugin directory.
         When the frontend variant of the new version starts, it migrates these plugins.
         This logic can be removed when IJPL-170369 is fixed. */
      var oldFrontendPlugins = oldPluginsDir.resolve("frontend");
      if (Files.isDirectory(oldFrontendPlugins)) {
        NioFiles.copyRecursively(oldFrontendPlugins, newPluginsDir.resolve(InitialConfigImportState.FRONTEND_PLUGINS_TO_MIGRATE_DIR_NAME));
      }
    }

    migrateGlobalPlugins(newConfigDir, oldConfigDir, pluginsToMigrate, pluginsToDownload, options.log);

    pluginsToMigrate.removeIf(hasPendingUpdate);
    if (!pluginsToMigrate.isEmpty()) {
      migratePlugins(newPluginsDir, pluginsToMigrate, log);
    }

    pluginsToDownload.removeIf(hasPendingUpdate);
    if (!pluginsToDownload.isEmpty()) {
      downloadUpdatesForPlugins(newPluginsDir, options, pluginsToDownload, brokenPluginVersions);

      // migrating plugins for which we weren't able to download updates
      migratePlugins(newPluginsDir, pluginsToDownload, log);
    }
  }

  /**
   * Collects plugins which should be migrated from the previous IDE's version, and stores plugins which should be copied in
   * {@code pluginsToMigrate} and the plugins which should be downloaded from the plugin repository in {@code pluginsToDownload}.
   * @return {@code false} if failed to collect plugins or {@code true} otherwise
   */
  public static boolean collectPluginsToMigrate(
    @NotNull Path oldPluginsDir,
    @NotNull ConfigImportOptions options,
    @Nullable Map<PluginId, Set<String>> brokenPluginVersions,
    @NotNull List<IdeaPluginDescriptor> pluginsToMigrate,
    @NotNull List<IdeaPluginDescriptor> pluginsToDownload
  ) {
    @Nullable PluginDescriptorLoadingResult oldIdePlugins = null;
    try {
      /* FIXME
       * in production, bundledPluginPath from the options is always null, it is set only in tests.
       * in tests, however, the behaviour is different from production, see com.intellij.ide.plugins.PluginDescriptorLoader.loadPluginDescriptorsImpl
       * there is isUnitTestMode check that shortcuts the execution
       * in production, if bundledPluginPath is null, the path from our IDE instance (!) bundled plugin path is used instead
       * so it looks like in production we effectively use bundled plugin path from the current IDE, not from the old one
       */
      oldIdePlugins = PluginDescriptorLoader.loadDescriptorsFromOtherIde(
        oldPluginsDir, options.bundledPluginPath, options.compatibleBuildNumber
      );
    }
    catch (ExecutionException | InterruptedException e) {
      return false;
    }
    catch (IOException e) {
      options.log.info("Non-existing plugins directory: " + oldPluginsDir, e);
    }

    if (oldIdePlugins != null) {
      var initContext = new ProductPluginInitContext(
        options.compatibleBuildNumber, Collections.emptySet(), Collections.emptySet(), brokenPluginVersions
      );
      var nonLoadablePlugins = new HashMap<PluginId, PluginMainDescriptor>();
      var loadablePlugins = PluginInitContextSelectPluginsToLoadKt.selectPluginsToLoad(
        initContext,
        oldIdePlugins.getDiscoveredPlugins(),
        (plugin, reason) -> {
          if (reason instanceof PluginVersionIsSuperseded) {
            return Unit.INSTANCE;
          }
          var previousNonLoadable = nonLoadablePlugins.get(plugin.getPluginId());
          if (previousNonLoadable == null || VersionComparatorUtil.compare(plugin.getVersion(), previousNonLoadable.getVersion()) > 0) {
            nonLoadablePlugins.put(plugin.getPluginId(), plugin);
          }
          return Unit.INSTANCE;
        }
      ).getPlugins();
      // TODO 'plugin is broken' is already applied by 'selectPluginsToLoad'
      if (Boolean.getBoolean(UPDATE_ONLY_INCOMPATIBLE_PLUGINS_PROPERTY)) {
        partitionNonBundled(loadablePlugins, pluginsToDownload, pluginsToMigrate, descriptor -> {
          var brokenVersions = brokenPluginVersions != null ? brokenPluginVersions.get(descriptor.getPluginId()) : null;
          return brokenVersions != null && brokenVersions.contains(descriptor.getVersion());
        });
        partitionNonBundled(nonLoadablePlugins.values(), pluginsToDownload, pluginsToMigrate, __ -> true);
      }
      else {
        // The first partition in the branch above puts only broken plugins to pluginsToDownload.
        // Here we also put there plugins for which updates are available (or they are broken).
        // So the only difference is that here we try to download more plugins.
        var nonBundledPlugins = new ArrayList<IdeaPluginDescriptor>();
        partitionNonBundled(loadablePlugins, nonBundledPlugins, pluginsToMigrate, __ -> true);
        partitionNonBundled(nonLoadablePlugins.values(), nonBundledPlugins, pluginsToMigrate, __ -> true);
        var updates = fetchPluginUpdatesFromMarketplace(options, ContainerUtil.map2Set(nonBundledPlugins, d -> d.getPluginId()));
        partitionNonBundled(loadablePlugins, pluginsToDownload, pluginsToMigrate, d -> {
          if (updates != null && updates.containsKey(d.getPluginId()) && !updates.get(d.getPluginId()).getVersion().equals(d.getVersion())) {
            return true;
          }
          var brokenVersions = brokenPluginVersions != null ? brokenPluginVersions.get(d.getPluginId()) : null;
          return brokenVersions != null && brokenVersions.contains(d.getVersion());
        });
        partitionNonBundled(nonLoadablePlugins.values(), pluginsToDownload, pluginsToMigrate, __ -> true);
      }
    }
    return true;
  }

  private static void performMigrations(PluginMigrationOptions options) {
    // WRITE IN MIGRATIONS HERE
    // Note that migrations are not taken into account for IDE updates through Toolbox
    new NotebooksMigration242().migratePlugins(options);
    new SpaceMigration252().migratePlugins(options);
    new BigDataToolsMigration253().migratePlugins(options);
    new VcsPluginsMigration261().migratePlugins(options);
    new CwmMigration261().migratePlugins(options);
  }

  private static void migrateGlobalPlugins(
    Path newConfigDir, Path oldConfigDir,
    List<IdeaPluginDescriptor> toMigrate, List<IdeaPluginDescriptor> toDownload,
    Logger log
  ) {
    var currentProductVersion = PluginManagerCore.getBuildNumber().asStringWithoutProductCode();
    var previousVersion = parseVersionFromConfig(oldConfigDir);
    var options = new PluginMigrationOptions(previousVersion, currentProductVersion, newConfigDir, oldConfigDir, toMigrate, toDownload, log);
    performMigrations(options);
    var downloadIds = toDownload.stream()
      .map(descriptor -> descriptor.getPluginId().getIdString())
      .collect(Collectors.joining("\n"));
    var resultFile = newConfigDir.resolve(InitialConfigImportState.MIGRATION_INSTALLED_PLUGINS_TXT);
    try {
      Files.writeString(resultFile, downloadIds);
    }
    catch (IOException e) {
      options.getLog().error("Unable to write auto install result", e);
    }
  }

  private static void partitionNonBundled(
    Collection<? extends IdeaPluginDescriptor> descriptors,
    List<IdeaPluginDescriptor> firstAccumulator,
    List<IdeaPluginDescriptor> secondAccumulator,
    Predicate<IdeaPluginDescriptor> predicate
  ) {
    for (var descriptor : descriptors) {
      if (!descriptor.isBundled()) {
        (predicate.test(descriptor) ? firstAccumulator : secondAccumulator).add(descriptor);
      }
    }
  }

  private static Predicate<IdeaPluginDescriptor> collectPendingPluginUpdates(List<ActionCommand> actionCommands, FileSystem fs, Logger log) {
    var result = new LinkedHashSet<PluginId>();
    for (var command : actionCommands) {
      var source = switch (command) {
        case StartupActionScriptManager.CopyCommand cc -> cc.getSource();
        case StartupActionScriptManager.UnzipCommand uzc -> uzc.getSource();
        default -> null;
      };
      if (source == null) continue;

      try {
        var descriptor = PluginDescriptorLoader.loadDescriptorFromArtifact(fs.getPath(source), null);
        if (descriptor != null) {
          result.add(descriptor.getPluginId());
        }
        else {
          log.info("No plugin descriptor in pending update: " + source);
        }
      }
      catch (IOException e) {
        log.info("Failed to load plugin descriptor from pending update: " + source);
      }
    }

    return descriptor -> {
      var pluginId = descriptor.getPluginId();
      if (result.contains(pluginId)) {
        log.info("Plugin '" + pluginId + "' skipped due to a pending update");
        return true;
      }
      else {
        return false;
      }
    };
  }

  public static void migratePlugins(Path newPluginsDir, List<IdeaPluginDescriptor> descriptors, Logger log) throws IOException {
    for (var descriptor : descriptors) {
      var pluginPath = descriptor.getPluginPath();
      var pluginId = descriptor.getPluginId();
      if (pluginPath == null) {
        log.info("Skipping migration of plugin '" + pluginId + "', because it is officially homeless");
        continue;
      }

      log.info("Migrating plugin '" + pluginId + "' version: " + descriptor.getVersion());
      var target = newPluginsDir.resolve(pluginPath.getFileName());
      if (Files.isDirectory(pluginPath)) {
        NioFiles.copyRecursively(pluginPath, target);
      }
      else {
        Files.createDirectories(newPluginsDir);
        Files.copy(pluginPath, target);
      }
    }
  }

  /** @param plugins elements for which updates are successfully processed are _removed_ from the list; broken plugins are removed too */
  private static void downloadUpdatesForPlugins(
    Path newPluginsDir,
    ConfigImportOptions options,
    List<IdeaPluginDescriptor> plugins,
    Map<PluginId, Set<String>> brokenPluginVersions
  ) {
    if (options.headless) {
      runSynchronouslyInBackground(() -> {
        @SuppressWarnings("UsagesOfObsoleteApi") var indicator =
          options.headlessProgressIndicator == null ? new EmptyProgressIndicator(ModalityState.nonModal()) : options.headlessProgressIndicator;
        downloadUpdatesForPlugins(newPluginsDir, options, plugins, brokenPluginVersions, indicator);
      });
    }
    else {
      ThreadingAssertions.assertEventDispatchThread();

      var dialog = new ConfigImportProgressDialog();
      dialog.setModalityType(Dialog.ModalityType.TOOLKIT_MODAL);
      AppUIUtilKt.updateAppWindowIcon(dialog);
      SplashManagerKt.hideSplash();
      runSynchronouslyInBackground(() -> {
        try {
          downloadUpdatesForPlugins(newPluginsDir, options, plugins, brokenPluginVersions, dialog.getIndicator());
        }
        catch (Throwable e) {
          options.log.info("Failed to download updates for plugins", e);
        }
        SwingUtilities.invokeLater(() -> dialog.setVisible(false));
      });
      dialog.setVisible(true);
    }
  }

  /** @param plugins elements for which updates are successfully processed are _removed_ from the list; broken plugins are removed too */
  private static void downloadUpdatesForPlugins(
    Path newPluginsDir,
    ConfigImportOptions options,
    List<IdeaPluginDescriptor> plugins,
    Map<PluginId, Set<String>> brokenPluginVersions,
    ProgressIndicator indicator
  ) {
    ThreadingAssertions.assertBackgroundThread();

    var log = options.log;
    for (var iterator = plugins.iterator(); iterator.hasNext(); ) {
      var descriptor = iterator.next();
      var pluginId = descriptor.getPluginId();

      try {
        var downloader = PluginDownloader.createDownloader(descriptor)
          .withErrorsConsumer(__ -> {})
          .withDownloadService(options.downloadService);

        if (downloader.prepareToInstall(indicator)) {
          PluginInstaller.unpackPlugin(downloader.getFilePath(), newPluginsDir);
          log.info("Downloaded and unpacked compatible version of plugin '" + pluginId + "'");
          iterator.remove();
        }
        else if (isBrokenPlugin(descriptor, brokenPluginVersions)) {
          iterator.remove();
        }
      }
      catch (@SuppressWarnings("IncorrectCancellationExceptionHandling") ProcessCanceledException ignored) {
        log.info("Plugin download cancelled");
        break;
      }
      catch (IOException e) {
        log.info("Failed to download and install compatible version of '" + pluginId + "': " + e.getMessage());
      }
    }
  }

  private static @Nullable Map<PluginId, PluginNode> fetchPluginUpdatesFromMarketplace(ConfigImportOptions options, Set<PluginId> pluginIds) {
    if (testLastCompatiblePluginUpdatesFetcher != null) {
      return testLastCompatiblePluginUpdatesFetcher.apply(pluginIds);
    }

    try {
      var start = System.nanoTime();
      var updates = runSynchronouslyInBackgroundWithTimeout(
        () -> MarketplaceRequests.loadLastCompatiblePluginModels(pluginIds, options.compatibleBuildNumber).stream()
          .map(PluginUiModel::getDescriptor)
          .filter(PluginNode.class::isInstance)
          .toList(),
        PLUGIN_UPDATES_TIMEOUT_MS);
      options.log.info("Fetched " + updates.size() + " latest compatible plugin updates in " + ((System.nanoTime() - start) / 1_000_000) + " ms");
      var updatesMap = new HashMap<PluginId, PluginNode>();
      for (var update : updates) {
        updatesMap.put(update.getPluginId(), (PluginNode)update);
      }
      return updatesMap;
    }
    catch (TimeoutException e) {
      options.log.warn("Failed to fetch updates for plugins: time-out");
      return null;
    }
    catch (Throwable e) {
      options.log.warn("Failed to fetch updates for plugins", e);
      return null;
    }
  }

  private static boolean isBrokenPlugin(IdeaPluginDescriptor descriptor, @Nullable Map<PluginId, Set<String>> brokenPluginVersions) {
    if (brokenPluginVersions == null) {
      return BrokenPluginFileKt.isBrokenPlugin(descriptor);
    }
    var versions = brokenPluginVersions.get(descriptor.getPluginId());
    return versions != null && versions.contains(descriptor.getVersion());
  }

  private static @Nullable Map<PluginId, Set<String>> fetchBrokenPluginsFromMarketplace(ConfigImportOptions options, Path newConfigDir) {
    if (testBrokenPluginsFetcherStub != null) {
      return testBrokenPluginsFetcherStub.apply(newConfigDir);
    }

    try {
      var buildNumber = options.compatibleBuildNumber != null ? options.compatibleBuildNumber : ApplicationInfoImpl.getShadowInstance().getBuild();
      var start = System.nanoTime();
      var brokenPlugins = runSynchronouslyInBackgroundWithTimeout(
        () -> MarketplaceRequests.Companion.getBrokenPlugins(buildNumber),
        BROKEN_PLUGINS_TIMEOUT_MS);
      options.log.info("Fetched broken plugins in " + ((System.nanoTime() - start) / 1_000_000) + " ms");
      if (brokenPlugins != null && !brokenPlugins.isEmpty()) {
        try {
          Files.createDirectories(newConfigDir);
          BrokenPluginFileKt.writeBrokenPlugins(brokenPlugins, newConfigDir);
          BrokenPluginFileKt.dropInMemoryBrokenPluginsCache(); // just in case
        }
        catch (Exception e) {
          options.log.error("Failed to write broken plugins", e);
        }
      }
      return brokenPlugins;
    }
    catch (TimeoutException e) {
      options.log.warn("Failed to fetch broken plugins: timed out");
      return null;
    }
    catch (Throwable e) {
      options.log.warn("Failed to fetch broken plugins", e);
      return null;
    }
  }

  private static void runSynchronouslyInBackground(Runnable runnable) {
    try {
      var thread = new Thread(runnable, "Plugin downloader");
      thread.start();
      thread.join();
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private static <T> T runSynchronouslyInBackgroundWithTimeout(Supplier<T> computation, long timeoutMs) throws TimeoutException {
    try {
      var result = new AtomicReference<T>();
      var thread = new Thread("Plugin downloader") {
        @Override
        public void run() {
          result.set(computation.get());
        }
      };
      thread.start();
      thread.join(timeoutMs);
      if (thread.isAlive()) {
        thread.interrupt();
        throw new TimeoutException();
      }
      return result.get();
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private static boolean isEmptyDirectory(Path newPluginsDir) {
    try (var stream = Files.newDirectoryStream(newPluginsDir)) {
      for (var path : stream) {
        var hidden = OS.CURRENT == OS.Windows ? Files.readAttributes(path, DosFileAttributes.class).isHidden() : path.getFileName().startsWith(".");
        if (!hidden) {
          return false;
        }
      }
    }
    catch (IOException ignored) { }
    return true;
  }

  @VisibleForTesting
  public static void setKeymapIfNeeded(@NotNull Path oldConfigDir, @NotNull Path newConfigDir, @NotNull Logger log) {
    var nameWithVersion = getNameWithVersion(oldConfigDir);
    var m = matchNameWithVersion(nameWithVersion);
    if (m.matches() && VersionComparatorUtil.compare("2019.1", m.group(1)) >= 0) {
      var keymapFileSpec = StoreUtilKt.getDefaultStoragePathSpec(KeymapManagerImpl.class);
      if (keymapFileSpec != null) {
        var keymapOptionFile = newConfigDir.resolve(PathManager.OPTIONS_DIRECTORY).resolve(keymapFileSpec);
        if (!Files.exists(keymapOptionFile)) {
          try {
            Files.createDirectories(keymapOptionFile.getParent());
            Files.writeString(keymapOptionFile, """
              <application>
                <component name="KeymapManager">
                  <active_keymap name="Mac OS X" />
                </component>
              </application>""");
          }
          catch (IOException e) {
            log.error("Cannot set keymap", e);
          }
        }
      }
    }
  }

  private static Matcher matchNameWithVersion(String nameWithVersion) {
    return Pattern.compile("\\.?\\D+(\\d+\\.\\d+)?").matcher(nameWithVersion);
  }

  /*
   * Merging imported VM option file with the one pre-created by an external tool (like the Toolbox app).
   * When both files set the same property, the value from an external tool is supposed to be more actual.
   * When both files set `-Xmx`, a higher value is preferred.
   */
  public static void mergeVmOptions(Path importFile, Path currentFile, Logger log) {
    try {
      var cs = VMOptions.getFileCharset();
      var importLines = Files.readAllLines(importFile, cs);
      var currentLines = Files.readAllLines(currentFile, cs);
      var result = mergeVmOptionsLines(importLines, currentLines);
      Files.write(currentFile, result, cs);
    }
    catch (IOException e) {
      log.warn("Failed to merge VM option files " + importFile + " and " + currentFile, e);
    }
  }

  private static boolean vmOptionsRequiresMerge(@Nullable Path oldConfigDir, Path newConfigDir, Logger log) {
    if (oldConfigDir == null) return false;
    var importFile = oldConfigDir.resolve(VMOptions.getFileName());
    if (!Files.isRegularFile(importFile)) return false;

    if (newConfigDir == null) return true;
    var currentFile = newConfigDir.resolve(VMOptions.getFileName());
    if (!Files.isRegularFile(currentFile)) return true;

    try {
      var cs = VMOptions.getFileCharset();
      var importLines = Files.readAllLines(importFile, cs);
      var currentLines = Files.readAllLines(currentFile, cs);
      currentLines.sort(String::compareTo);
      var result = mergeVmOptionsLines(importLines, currentLines);
      updateVMOptionsLines(newConfigDir, oldConfigDir, result, log);
      result.sort(String::compareTo);
      return !currentLines.equals(result);
    }
    catch (IOException e) {
      log.warn("Failed to merge VM option files " + importFile + " and " + currentFile, e);
      return true;
    }
  }

  private static List<String> mergeVmOptionsLines(List<String> importLines, List<String> currentLines) {
    var result = new ArrayList<String>(importLines.size() + currentLines.size());
    var preferCurrentXmx = false;

    nextLine:
    for (var line : importLines) {
      if (line.startsWith("-D")) {
        var p = line.indexOf('=');
        if (p > 0) {
          var prefix = line.substring(0, p + 1);
          for (var l : currentLines) {
            if (l.startsWith(prefix)) {
              continue nextLine;
            }
          }
        }
      }
      else if (line.startsWith("-Xmx") && isLowerValue("-Xmx", line.substring(4), currentLines)) {
        preferCurrentXmx = true;
        continue nextLine;
      }
      result.add(line);
    }

    for (var line : currentLines) {
      if (preferCurrentXmx || !line.startsWith("-Xmx")) {
        result.add(line);
      }
    }
    return result;
  }

  /* Fix VM options in the custom *.vmoptions file that won't work with the current IDE version or duplicate/undercut platform ones. */
  public static void updateVMOptions(@NotNull Path newConfigDir, @NotNull Path oldConfigDir, @NotNull Logger log) {
    var vmOptionsFile = newConfigDir.resolve(VMOptions.getFileName());
    if (Files.exists(vmOptionsFile)) {
      try {
        var lines = Files.readAllLines(vmOptionsFile, VMOptions.getFileCharset());
        var updated = updateVMOptionsLines(newConfigDir, oldConfigDir, lines, log);
        if (updated) {
          Files.write(vmOptionsFile, lines, VMOptions.getFileCharset());
        }
      }
      catch (IOException e) {
        log.warn("Failed to update custom VM options file " + vmOptionsFile, e);
      }
    }
  }

  private static boolean updateVMOptionsLines(Path newConfigDir, Path oldConfigDir, List<String> lines, Logger log) {
    var platformVmOptionsFile = newConfigDir.getFileSystem().getPath(VMOptions.getPlatformOptionsFile().toString());
    var platformLines = new LinkedHashSet<>(readPlatformOptions(platformVmOptionsFile, log));
    var oldConfigName = oldConfigDir.getFileName().toString();
    @SuppressWarnings("SpellCheckingInspection")
    var fromCE = oldConfigName.startsWith("IdeaIC") || oldConfigName.startsWith("PyCharmCE");
    var updated = false;

    for (var i = lines.listIterator(); i.hasNext(); ) {
      var line = i.next().trim();
      if (line.equals("-XX:MaxJavaStackTraceDepth=-1")) {
        i.set("-XX:MaxJavaStackTraceDepth=10000"); updated = true;
      }
      else if (
        "-XX:+UseConcMarkSweepGC".equals(line) ||
        "-Xverify:none".equals(line) || "-noverify".equals(line) ||
        "-XX:+UseCompressedOops".equals(line) ||
        line.startsWith("-agentlib:yjpagent") ||
        line.startsWith("-agentpath:") && line.contains("yjpagent") ||
        "-Dsun.io.useCanonPrefixCache=false".equals(line) ||
        "-Dfile.encoding=UTF-8".equals(line) && OS.CURRENT == OS.macOS ||
        line.startsWith("-DJETBRAINS_LICENSE_SERVER") && fromCE ||
        line.startsWith("-Dide.do.not.disable.paid.plugins.on.startup") ||
        isDuplicateOrLowerValue(line, platformLines)
      ) {
        i.remove(); updated = true;
      }
    }
    return updated;
  }

  @VisibleForTesting
  static List<String> readPlatformOptions(Path platformVmOptionsFile, Logger log) {
    try {
      return Files.readAllLines(platformVmOptionsFile, VMOptions.getFileCharset());
    }
    catch (IOException e) {
      // exceptions should not prevent a user's VM options file from being processed
      log.warn("Cannot read platform VM options file " + platformVmOptionsFile, e);
      return List.of();
    }
  }

  private static boolean isDuplicateOrLowerValue(String line, Collection<String> platformLines) {
    if (platformLines.isEmpty()) {
      return false;
    }
    if (platformLines.contains(line)) {
      return true;
    }
    if (line.startsWith("-Xms") || line.startsWith("-Xmx") || line.startsWith("-Xss")) {
      return isLowerValue(line.substring(0, 4), line.substring(4), platformLines);
    }
    if (line.startsWith("-XX:")) {
      var p = line.indexOf('=', 4);
      if (p > 0) return isLowerValue(line.substring(0, p + 1), line.substring(p + 1), platformLines);
    }
    return false;
  }

  private static boolean isLowerValue(String prefix, String userValue, Collection<String> platformLines) {
    for (var line : platformLines) {
      if (line.startsWith(prefix)) {
        try {
          return VMOptions.parseMemoryOption(userValue) <= VMOptions.parseMemoryOption(line.substring(prefix.length()));
        }
        catch (IllegalArgumentException ignored) { }
      }
    }
    return false;
  }

  private static boolean blockImport(Path path, Path oldConfig, Path newConfig, Path oldPluginsDir, @Nullable ConfigImportSettings settings) {
    var fileName = path.getFileName();
    var parent = path.getParent();
    if (oldConfig.equals(parent)) {
      return shouldSkipFileDuringImport(path, settings) || Files.exists(newConfig.resolve(fileName)) || path.startsWith(oldPluginsDir);
    }

    if (parent.getFileName().toString().equals(PathManager.OPTIONS_DIRECTORY) &&
        oldConfig.equals(parent.getParent()) &&
        fileName.toString().equals(P3DynamicPluginSynchronizerKt.DYNAMIC_PLUGINS_SYNCHRONIZER_FILE_NAME)) {
      return true;
    }

    if (settings != null && settings.shouldSkipPath(path)) {
      return true; // this check needs to repeat even for non-root paths
    }

    return false;
  }

  private static boolean shouldSkipFileDuringImport(Path path, @Nullable ConfigImportSettings settings) {
    var fileName = path.getFileName().toString();
    return SESSION_FILES.contains(fileName) ||
           fileName.equals(BUNDLED_PLUGINS_FILENAME) ||
           fileName.equals(StoragePathMacros.APP_INTERNAL_STATE_DB) ||
           fileName.equals(ExpiredPluginsState.EXPIRED_PLUGINS_FILENAME) ||
           fileName.startsWith(SpecialConfigFiles.CHROME_USER_DATA) ||
           fileName.endsWith(".jdk") && fileName.startsWith(String.valueOf(ApplicationNamesInfo.getInstance().getScriptName())) ||
           (settings != null && settings.shouldSkipPath(path));
  }

  private static boolean overwriteOnImport(Path path) {
    return path.endsWith(EarlyAccessRegistryManager.fileName);
  }

  private static String defaultConfigPath(String selector) {
    return newOrUnknown(selector) ? PathManager.getDefaultConfigPathFor(selector) :
           OS.CURRENT == OS.macOS ? SystemProperties.getUserHome() + "/Library/Preferences/" + selector :
           SystemProperties.getUserHome() + "/." + selector + '/' + CONFIG;
  }

  private static String defaultPluginsPath(String selector) {
    return newOrUnknown(selector) ? PathManager.getDefaultPluginPathFor(selector) :
           OS.CURRENT == OS.macOS ? SystemProperties.getUserHome() + "/Library/Application Support/" + selector :
           SystemProperties.getUserHome() + "/." + selector + '/' + CONFIG + '/' + PLUGINS;
  }

  private static String defaultSystemPath(String selector) {
    return newOrUnknown(selector) ? PathManager.getDefaultSystemPathFor(selector) :
           OS.CURRENT == OS.macOS ? SystemProperties.getUserHome() + "/Library/Caches/" + selector :
           SystemProperties.getUserHome() + "/." + selector + '/' + SYSTEM;
  }

  private static String defaultLogsPath(String selector) {
    return newOrUnknown(selector) ? PathManager.getDefaultLogPathFor(selector) :
           OS.CURRENT == OS.macOS ? SystemProperties.getUserHome() + "/Library/Logs/" + selector :
           SystemProperties.getUserHome() + "/." + selector + '/' + SYSTEM + "/logs";
  }

  private static boolean newOrUnknown(String selector) {
    var m = SELECTOR_PATTERN.matcher(selector);
    return !m.matches() || "2020.1".compareTo(m.group(2)) <= 0;
  }

  private static List<Path> getRelatedDirectories(Path config, boolean forAutoClean) {
    var selector = getNameWithVersion(config);
    var fs = config.getFileSystem();
    var system = fs.getPath(defaultSystemPath(selector));

    if (!forAutoClean) {
      var commonParent = config.getParent();
      if (commonParent.equals(system.getParent())) {
        var files = NioFiles.list(commonParent);
        if (files.size() == 1 || files.size() == 2 && files.containsAll(List.of(config, system))) {
          return List.of(commonParent);
        }
      }
    }

    var result = new ArrayList<Path>();

    if (!forAutoClean) {
      result.add(config);
    }

    if (Files.exists(system)) {
      result.add(system);
    }

    if (!forAutoClean) {
      var plugins = fs.getPath(defaultPluginsPath(selector));
      if (!plugins.startsWith(config) && Files.exists(plugins)) {
        result.add(plugins);
      }
    }

    var logs = fs.getPath(defaultLogsPath(selector));
    if (!logs.startsWith(system) && Files.exists(logs)) {
      result.add(logs);
    }

    return result;
  }

  @VisibleForTesting
  @SuppressWarnings("StaticNonFinalField")
  public static @Nullable Function<Path, @Nullable Map<PluginId, Set<String>>> testBrokenPluginsFetcherStub = null;

  @VisibleForTesting
  @SuppressWarnings("StaticNonFinalField")
  public static @Nullable Function<Set<PluginId>, @Nullable Map<PluginId, PluginNode>> testLastCompatiblePluginUpdatesFetcher = null;
}
