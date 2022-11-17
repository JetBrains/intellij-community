// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.core.CoreBundle;
import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.LoadingState;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.util.Java11Shim;
import com.intellij.util.PlatformUtils;
import com.intellij.util.lang.UrlClassLoader;
import com.intellij.util.lang.ZipFilePool;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Deferred;
import kotlinx.coroutines.GlobalScope;
import kotlinx.coroutines.future.FutureKt;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.nio.file.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.util.containers.ContainerUtil.getOnlyItem;

/**
 * See <a href="https://github.com/JetBrains/intellij-community/blob/master/docs/plugin.md">Plugin Model</a> documentation.
 *
 * @implNote Prefer to use only JDK classes. Any post start-up functionality should be placed in {@link PluginManager} class.
 */
public final class PluginManagerCore {
  public static final @NonNls String META_INF = "META-INF/";

  public static final String CORE_PLUGIN_ID = "com.intellij";
  public static final PluginId CORE_ID = PluginId.getId(CORE_PLUGIN_ID);

  public static final PluginId JAVA_PLUGIN_ID = PluginId.getId("com.intellij.java");
  static final PluginId JAVA_MODULE_ID = PluginId.getId("com.intellij.modules.java");

  public static final String PLUGIN_XML = "plugin.xml";
  public static final String PLUGIN_XML_PATH = META_INF + PLUGIN_XML;
  static final PluginId ALL_MODULES_MARKER = PluginId.getId("com.intellij.modules.all");

  public static final String VENDOR_JETBRAINS = "JetBrains";
  public static final String VENDOR_JETBRAINS_SRO = "JetBrains s.r.o.";

  private static final String MODULE_DEPENDENCY_PREFIX = "com.intellij.module";
  private static final String PLATFORM_DEPENDENCY_PREFIX = "com.intellij.platform";

  public static final PluginId SPECIAL_IDEA_PLUGIN_ID = PluginId.getId("IDEA CORE");

  static final @NonNls String DISABLE = "disable";
  static final @NonNls String ENABLE = "enable";
  static final @NonNls String EDIT = "edit";

  private static volatile boolean IGNORE_COMPATIBILITY = Boolean.getBoolean("idea.ignore.plugin.compatibility");

  private static final String THIRD_PARTY_PLUGINS_FILE = "alien_plugins.txt";
  private static volatile @Nullable Boolean thirdPartyPluginsNoteAccepted = null;

  private static Reference<Map<PluginId, Set<String>>> brokenPluginVersions;
  private static volatile @Nullable PluginSet pluginSet;
  private static Map<PluginId, PluginLoadingError> pluginLoadingErrors;

  @SuppressWarnings("StaticNonFinalField")
  @VisibleForTesting
  public static volatile boolean isUnitTestMode = Boolean.getBoolean("idea.is.unit.test");

  @ApiStatus.Internal
  private static final List<Supplier<? extends HtmlChunk>> pluginErrors = new ArrayList<>();

  private static Set<PluginId> ourPluginsToDisable;
  private static Set<PluginId> ourPluginsToEnable;

  /**
   * Bundled plugins that were updated.
   * When we update a bundled plugin, it becomes non-bundled, so it is more difficult for analytics to use that data.
   */
  private static Set<PluginId> shadowedBundledPlugins;

  private static Boolean isRunningFromSources;
  private static volatile Deferred<PluginSet> initFuture;

  private static BuildNumber ourBuildNumber;

  /**
   * Returns list of all available plugin descriptors (bundled and custom, including disabled ones).
   * Use {@link #getLoadedPlugins()} if you need to get loaded plugins only.
   * <p>
   * Do not call this method during bootstrap, should be called in a copy of PluginManager, loaded by PluginClassLoader.
   */
  public static @NotNull IdeaPluginDescriptor @NotNull [] getPlugins() {
    return getPluginSet().allPlugins.toArray(new IdeaPluginDescriptor[0]);
  }

  @ApiStatus.Internal
  public static @NotNull PluginSet getPluginSet() {
    return Objects.requireNonNull(pluginSet);
  }

  static @Nullable PluginSet getNullablePluginSet() {
    return pluginSet;
  }

  /**
   * Returns descriptors of plugins which are successfully loaded into IDE. The result is sorted in a way that if each plugin comes after
   * the plugins it depends on.
   */
  public static @NotNull List<? extends IdeaPluginDescriptor> getLoadedPlugins() {
    return getPluginSet().enabledPlugins;
  }

  @ApiStatus.Internal
  public static @NotNull List<HtmlChunk> getAndClearPluginLoadingErrors() {
    synchronized (pluginErrors) {
      if (pluginErrors.isEmpty()) {
        return Collections.emptyList();
      }

      List<HtmlChunk> errors = new ArrayList<>(pluginErrors.size());
      for (Supplier<? extends HtmlChunk> t : pluginErrors) {
        errors.add(t.get());
      }
      pluginErrors.clear();
      return errors;
    }
  }

  @ApiStatus.Internal
  public static boolean arePluginsInitialized() {
    return pluginSet != null;
  }

  @ApiStatus.Internal
  public static void setPluginSet(@NotNull PluginSet value) {
    pluginSet = value;
  }

  public static boolean isDisabled(@NotNull PluginId pluginId) {
    return PluginEnabler.HEADLESS.isDisabled(pluginId);
  }

  @ApiStatus.Internal
  public static boolean isBrokenPlugin(@NotNull IdeaPluginDescriptor descriptor) {
    PluginId pluginId = descriptor.getPluginId();
    Set<String> set = getBrokenPluginVersions().get(pluginId);
    return set != null && set.contains(descriptor.getVersion());
  }

  @ApiStatus.Internal
  public static void updateBrokenPlugins(Map<PluginId, Set<String>> brokenPlugins) {
    brokenPluginVersions = new SoftReference<>(brokenPlugins);
    Path updatedBrokenPluginFile = getUpdatedBrokenPluginFile();
    try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(updatedBrokenPluginFile), 32_000))) {
      out.write(2);
      out.writeUTF(getBuildNumber().asString());
      out.writeInt(brokenPlugins.size());
      for (Map.Entry<PluginId, Set<String>> entry : brokenPlugins.entrySet()) {
        out.writeUTF(entry.getKey().getIdString());
        out.writeShort(entry.getValue().size());
        for (String s : entry.getValue()) {
          out.writeUTF(s);
        }
      }
    }
    catch (NoSuchFileException ignore) { }
    catch (IOException e) {
      getLogger().error("Failed to read " + updatedBrokenPluginFile, e);
    }
  }

  static @NotNull Map<@NotNull PluginId, @NotNull Set<String>> getBrokenPluginVersions() {
    if (PluginEnabler.HEADLESS.isIgnoredDisabledPlugins()) {
      return Collections.emptyMap();
    }

    Map<PluginId, Set<String>> result = brokenPluginVersions == null ? null : brokenPluginVersions.get();
    if (result == null) {
      result = readBrokenPluginFile();
      brokenPluginVersions = new SoftReference<>(result);
    }
    return result;
  }

  private static @NotNull Map<PluginId, Set<String>> readBrokenPluginFile() {
    Map<PluginId, Set<String>> result = null;
    Path updatedBrokenPluginFile = getUpdatedBrokenPluginFile();
    if (Files.exists(updatedBrokenPluginFile)) {
      result = tryReadBrokenPluginsFile(updatedBrokenPluginFile);
      if (result != null) {
        getLogger().info("Using cached broken plugins file");
      }
    }
    if (result == null) {
      result = tryReadBrokenPluginsFile(Paths.get(PathManager.getBinPath() + "/brokenPlugins.db"));
      if (result != null) {
        getLogger().info("Using broken plugins file from IDE distribution");
      }
    }
    if (result != null) {
      return result;
    }
    return Collections.emptyMap();
  }

  @Nullable
  private static Map<PluginId, Set<String>> tryReadBrokenPluginsFile(Path brokenPluginsStorage) {
    try (DataInputStream stream = new DataInputStream(new BufferedInputStream(Files.newInputStream(brokenPluginsStorage), 32_000))) {
      int version = stream.readUnsignedByte();
      if (version != 2) {
        getLogger().info("Unsupported version of " + brokenPluginsStorage + "(fileVersion=" + version + ", supportedVersion=2)");
        return null;
      }
      String buildNumber = stream.readUTF();
      if (!buildNumber.equals(getBuildNumber().toString())) {
        getLogger().info("Ignoring cached broken plugins file from an earlier IDE build (" + buildNumber + ")");
        return null;
      }

      int count = stream.readInt();
      Map<PluginId, Set<String>> result = new HashMap<>(count);
      for (int i = 0; i < count; i++) {
        PluginId pluginId = PluginId.getId(stream.readUTF());
        String[] versions = new String[stream.readUnsignedShort()];
        for (int j = 0; j < versions.length; j++) {
          versions[j] = stream.readUTF();
        }
        //noinspection SSBasedInspection
        result.put(pluginId, versions.length == 1 ? Collections.singleton(versions[0]) : new HashSet<>(Arrays.asList(versions)));
      }
      return result;
    }
    catch (NoSuchFileException ignore) {
    }
    catch (IOException e) {
      getLogger().error("Failed to read " + brokenPluginsStorage, e);
    }
    return null;
  }

  @ApiStatus.Internal
  public static boolean disablePlugin(@NotNull PluginId id) {
    return PluginEnabler.HEADLESS.disableById(Collections.singleton(id));
  }

  @ApiStatus.Internal
  public static boolean enablePlugin(@NotNull PluginId id) {
    return PluginEnabler.HEADLESS.enableById(Collections.singleton(id));
  }

  @ApiStatus.Internal
  public static boolean isModuleDependency(@NotNull PluginId dependentPluginId) {
    String idString = dependentPluginId.getIdString();
    return idString.startsWith(MODULE_DEPENDENCY_PREFIX)
           || idString.startsWith(PLATFORM_DEPENDENCY_PREFIX) && !"com.intellij.platform.images".equals(idString);
  }

  /**
   * @deprecated Use {@link PluginManager#getPluginByClass}.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static @Nullable PluginId getPluginByClassName(@NotNull String className) {
    PluginDescriptor result = getPluginDescriptorOrPlatformByClassName(className);
    PluginId id = result == null ? null : result.getPluginId();
    return (id == null || CORE_ID.equals(id)) ? null : id;
  }

  /**
   * @deprecated Use {@link PluginManager#getPluginByClass}.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static @Nullable PluginId getPluginOrPlatformByClassName(@NotNull String className) {
    PluginDescriptor result = getPluginDescriptorOrPlatformByClassName(className);
    return result == null ? null : result.getPluginId();
  }

  @ApiStatus.Internal
  public static boolean isPlatformClass(@NotNull @NonNls String className) {
    return className.startsWith("java.") ||
           className.startsWith("javax.") ||
           className.startsWith("kotlin.") ||
           className.startsWith("groovy.");
  }

  @ApiStatus.Internal
  public static @Nullable PluginDescriptor getPluginDescriptorOrPlatformByClassName(@NotNull @NonNls String className) {
    PluginSet pluginSet = PluginManagerCore.pluginSet;
    if (pluginSet == null || isPlatformClass(className) || !className.contains(".")) {
      return null;
    }

    IdeaPluginDescriptorImpl result = null;
    for (IdeaPluginDescriptorImpl descriptor : pluginSet.getEnabledModules()) {
      ClassLoader classLoader = descriptor.getPluginClassLoader();
      if (classLoader instanceof UrlClassLoader && ((UrlClassLoader)classLoader).hasLoadedClass(className)) {
        result = descriptor;
        break;
      }
    }

    if (result == null) {
      return null;
    }

    // return if the found plugin is not "core", or the package is obviously "core"
    if (!CORE_ID.equals(result.getPluginId()) ||
        className.startsWith("com.jetbrains.") || className.startsWith("org.jetbrains.") ||
        className.startsWith("com.intellij.") || className.startsWith("org.intellij.") ||
        className.startsWith("com.android.") ||
        className.startsWith("git4idea.") || className.startsWith("org.angularjs.")) {
      return result;
    }

    // otherwise, we need to check plugins with use-idea-classloader="true"
    return findClassInPluginThatUsesCoreClassloader(className, pluginSet);
  }

  private static @Nullable IdeaPluginDescriptorImpl findClassInPluginThatUsesCoreClassloader(@NonNls @NotNull String className,
                                                                                             PluginSet pluginSet) {
    String root = null;
    for (IdeaPluginDescriptorImpl descriptor : pluginSet.enabledPlugins) {
      if (!descriptor.isUseIdeaClassLoader) {
        continue;
      }

      if (root == null) {
        root = PathManager.getResourceRoot(descriptor.getClassLoader(), className.replace('.', '/') + ".class");
        if (root == null) {
          return null;
        }
      }

      Path path = descriptor.getPluginPath();
      if (root.startsWith(FileUtilRt.toSystemIndependentName(path.toString()))) {
        return descriptor;
      }
    }
    return null;
  }

  @ApiStatus.Internal
  public static @Nullable PluginDescriptor getPluginDescriptorIfIdeaClassLoaderIsUsed(@NotNull Class<?> aClass) {
    String className = aClass.getName();
    PluginSet pluginSet = PluginManagerCore.pluginSet;
    if (pluginSet == null || isPlatformClass(className) || !className.contains(".")) {
      return null;
    }
    return findClassInPluginThatUsesCoreClassloader(className, pluginSet);
  }

  public static boolean isDevelopedByJetBrains(@NotNull PluginDescriptor plugin) {
    return CORE_ID.equals(plugin.getPluginId()) ||
           SPECIAL_IDEA_PLUGIN_ID.equals(plugin.getPluginId()) ||
           isDevelopedByJetBrains(plugin.getVendor()) ||
           isDevelopedByJetBrains(plugin.getOrganization());
  }

  public static boolean isDevelopedByJetBrains(@Nullable String vendorString) {
    if (vendorString == null) {
      return false;
    }

    if (isVendorJetBrains(vendorString)) {
      return true;
    }

    for (String vendor : vendorString.split(",")) {
      String vendorItem = vendor.trim();
      if (isVendorJetBrains(vendorItem)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isVendorJetBrains(@NotNull String vendorItem) {
    return VENDOR_JETBRAINS.equals(vendorItem) || VENDOR_JETBRAINS_SRO.equals(vendorItem);
  }

  private static Path getUpdatedBrokenPluginFile() {
    return Paths.get(PathManager.getConfigPath()).resolve("updatedBrokenPlugins.db");
  }

  public static synchronized void invalidatePlugins() {
    pluginSet = null;

    Deferred<PluginSet> future = initFuture;
    if (future != null) {
      initFuture = null;
      future.cancel(new CancellationException("invalidatePlugins"));
    }
    DisabledPluginsState.Companion.invalidate();
    shadowedBundledPlugins = null;
  }

  public static boolean isRunningFromSources() {
    Boolean result = isRunningFromSources;
    if (result == null) {
      result = Files.isDirectory(Paths.get(PathManager.getHomePath(), Project.DIRECTORY_STORE_FOLDER));
      isRunningFromSources = result;
    }
    return result;
  }

  @ReviseWhenPortedToJDK(value = "10", description = "Collectors.toUnmodifiableList()")
  private static @NotNull List<Supplier<HtmlChunk>> preparePluginErrors(@NotNull List<? extends Supplier<@NlsContexts.DetailedDescription String>> globalErrorsSuppliers) {
    if (pluginLoadingErrors.isEmpty() && globalErrorsSuppliers.isEmpty()) {
      return new ArrayList<>();
    }

    @SuppressWarnings("SSBasedInspection") List<@NlsContexts.DetailedDescription String> globalErrors = globalErrorsSuppliers.stream()
      .map(Supplier::get)
      .collect(Collectors.toList());

    // log includes all messages, not only those which need to be reported to the user
    List<PluginLoadingError> loadingErrors = pluginLoadingErrors.entrySet()
      .stream()
      .sorted(Map.Entry.comparingByKey())
      .map(Map.Entry::getValue)
      .collect(Collectors.toList());

    String logMessage = "Problems found loading plugins:\n  " +
                        Stream.concat(
                          globalErrors.stream(),
                          loadingErrors.stream()
                            .map(PluginLoadingError::getInternalMessage)
                        ).collect(Collectors.joining("\n  "));

    if (isUnitTestMode || !GraphicsEnvironment.isHeadless()) {
      getLogger().warn(logMessage);
      return Stream.concat(
          globalErrors.stream(),
          loadingErrors.stream()
            .filter(PluginLoadingError::isNotifyUser)
            .map(PluginLoadingError::getDetailedMessage)
        ).map((@NlsContexts.DetailedDescription String text) -> (Supplier<HtmlChunk>)() -> HtmlChunk.text(text))
        .collect(Collectors.toList());
    }
    else {
      getLogger().error(logMessage);
      return new ArrayList<>();
    }
  }

  public static @Nullable PluginLoadingError getLoadingError(@NotNull PluginId pluginId) {
    return pluginLoadingErrors.get(pluginId);
  }

  private static @NotNull List<Supplier<? extends HtmlChunk>> prepareActions(@NotNull Collection<String> pluginNamesToDisable,
                                                                             @NotNull Collection<String> pluginNamesToEnable) {
    if (pluginNamesToDisable.isEmpty()) {
      return Collections.emptyList();
    }

    List<Supplier<? extends HtmlChunk>> actions = new ArrayList<>();
    String pluginNameToDisable = getOnlyItem(pluginNamesToDisable);
    String disableMessage = pluginNameToDisable != null ?
                            CoreBundle.message("link.text.disable.plugin", pluginNameToDisable) :
                            CoreBundle.message("link.text.disable.not.loaded.plugins");
    actions.add(() -> HtmlChunk.link(DISABLE, disableMessage));

    if (!pluginNamesToEnable.isEmpty()) {
      String pluginNameToEnable = getOnlyItem(pluginNamesToEnable);
      String enableMessage = pluginNameToEnable != null ?
                             CoreBundle.message("link.text.enable.plugin", pluginNameToEnable) :
                             CoreBundle.message("link.text.enable.all.necessary.plugins");
      actions.add(() -> HtmlChunk.link(ENABLE, enableMessage));
    }

    actions.add(() -> HtmlChunk.link(EDIT, CoreBundle.message("link.text.open.plugin.manager")));
    return Collections.unmodifiableList(actions);
  }

  @ApiStatus.Internal
  static synchronized boolean onEnable(boolean enabled) {
    Set<PluginId> pluginIds = enabled ? ourPluginsToEnable : ourPluginsToDisable;
    ourPluginsToEnable = null;
    ourPluginsToDisable = null;

    boolean applied = pluginIds != null;
    if (applied) {
      List<IdeaPluginDescriptorImpl> descriptors = new ArrayList<>();
      for (IdeaPluginDescriptorImpl descriptor : getPluginSet().allPlugins) {
        if (pluginIds.contains(descriptor.getPluginId())) {
          descriptor.setEnabled(enabled);

          if (descriptor.moduleName == null) {
            descriptors.add(descriptor);
          }
        }
      }

      DisabledPluginsState.Companion.setEnabledState(descriptors, enabled);
    }
    return applied;
  }

  public static void scheduleDescriptorLoading(@NotNull CoroutineScope coroutineScope) {
    scheduleDescriptorLoading(coroutineScope, null);
  }

  @ApiStatus.Internal
  public static synchronized void scheduleDescriptorLoading(@NotNull CoroutineScope coroutineScope, @Nullable Deferred<ZipFilePool> zipFilePoolDeferred) {
    if (initFuture == null) {
      initFuture = PluginDescriptorLoader.scheduleLoading(coroutineScope, zipFilePoolDeferred);
    }
  }

  /**
   * Think twice before use and get an approval from the core team. Returns enabled plugins only.
   */
  @ApiStatus.Internal
  public static @NotNull CompletableFuture<List<IdeaPluginDescriptorImpl>> getEnabledPluginRawList() {
    scheduleDescriptorLoading(GlobalScope.INSTANCE, null);
    return FutureKt.asCompletableFuture(initFuture).thenApply(it -> it.enabledPlugins);
  }

  @ApiStatus.Internal
  public static @NotNull Deferred<PluginSet> getInitPluginFuture() {
    Deferred<PluginSet> future = initFuture;
    if (future == null) {
      throw new IllegalStateException("Call scheduleDescriptorLoading() first");
    }
    return future;
  }

  public static @NotNull BuildNumber getBuildNumber() {
    BuildNumber result = ourBuildNumber;
    if (result == null) {
      result = BuildNumber.fromPluginsCompatibleBuild();
      if (getLogger().isDebugEnabled()) {
        getLogger().debug("getBuildNumber: fromPluginsCompatibleBuild=" + (result != null ? result.asString() : "null"));
      }
      if (result == null) {
        if (isUnitTestMode) {
          result = BuildNumber.currentVersion();
        }
        else {
          try {
            result = ApplicationInfoImpl.getShadowInstance().getApiVersionAsNumber();
          }
          catch (RuntimeException ignore) {
            // no need to log error - ApplicationInfo is required in production in any case, so, will be logged if really needed
            result = BuildNumber.currentVersion();
          }
        }
      }
      ourBuildNumber = result;
    }
    return result;
  }

  private static void disableIncompatiblePlugins(@NotNull Collection<IdeaPluginDescriptorImpl> descriptors,
                                                 @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idMap,
                                                 @NotNull Map<PluginId, PluginLoadingError> errors) {
    String selectedIds = System.getProperty("idea.load.plugins.id");
    String selectedCategory = System.getProperty("idea.load.plugins.category");

    Set<IdeaPluginDescriptorImpl> explicitlyEnabled = null;
    if (selectedIds != null) {
      Set<PluginId> set = new HashSet<>();
      for (String it : selectedIds.split(",")) {
        set.add(PluginId.getId(it));
      }
      set.addAll(ApplicationInfoImpl.getShadowInstance().getEssentialPluginsIds());

      explicitlyEnabled = new LinkedHashSet<>(set.size());
      for (PluginId id : set) {
        IdeaPluginDescriptorImpl descriptor = idMap.get(id);
        if (descriptor != null) {
          explicitlyEnabled.add(descriptor);
        }
      }
    }
    else if (selectedCategory != null) {
      explicitlyEnabled = new LinkedHashSet<>();
      for (IdeaPluginDescriptorImpl descriptor : descriptors) {
        if (selectedCategory.equals(descriptor.getCategory())) {
          explicitlyEnabled.add(descriptor);
        }
      }
    }

    if (explicitlyEnabled != null) {
      // add all required dependencies
      List<IdeaPluginDescriptorImpl> nonOptionalDependencies = new ArrayList<>();
      for (IdeaPluginDescriptorImpl descriptor : explicitlyEnabled) {
        processAllNonOptionalDependencies(descriptor, idMap, dependency -> {
          nonOptionalDependencies.add(dependency);
          return FileVisitResult.CONTINUE;
        });
      }

      explicitlyEnabled.addAll(nonOptionalDependencies);
    }

    IdeaPluginDescriptorImpl coreDescriptor = idMap.get(CORE_ID);
    boolean shouldLoadPlugins = Boolean.parseBoolean(System.getProperty("idea.load.plugins", "true"));
    for (IdeaPluginDescriptorImpl descriptor : descriptors) {
      if (descriptor == coreDescriptor) {
        continue;
      }

      if (explicitlyEnabled != null) {
        if (!explicitlyEnabled.contains(descriptor)) {
          descriptor.setEnabled(false);
          getLogger().info("Plugin '" + descriptor.getName() + "' " +
                           (selectedIds != null
                            ? "is not in 'idea.load.plugins.id' system property"
                            : "category doesn't match 'idea.load.plugins.category' system property"));
        }
      }
      else if (!shouldLoadPlugins) {
        descriptor.setEnabled(false);
        errors.put(descriptor.getPluginId(), new PluginLoadingError(descriptor,
                                                                    message("plugin.loading.error.long.plugin.loading.disabled", descriptor.getName()),
                                                                    message("plugin.loading.error.short.plugin.loading.disabled")));
      }
    }
  }

  public static boolean isCompatible(@NotNull IdeaPluginDescriptor descriptor) {
    return isCompatible(descriptor, null);
  }

  public static boolean isCompatible(@NotNull IdeaPluginDescriptor descriptor, @Nullable BuildNumber buildNumber) {
    return !isIncompatible(descriptor, buildNumber);
  }

  public static boolean isIncompatible(@NotNull IdeaPluginDescriptor descriptor) {
    return isIncompatible(descriptor, null);
  }

  public static boolean isIncompatible(@NotNull IdeaPluginDescriptor descriptor,
                                       @Nullable BuildNumber buildNumber) {
    return checkBuildNumberCompatibility(descriptor,
                                         buildNumber != null ? buildNumber : getBuildNumber()) != null;
  }

  @NotNull
  public static Optional<IdeaPluginPlatform> getIncompatiblePlatform(@NotNull IdeaPluginDescriptor descriptor) {
    return descriptor.getDependencies().stream()
      .map(d -> IdeaPluginPlatform.fromModuleId(d.getPluginId()))
      .filter(p -> p != null && !p.isHostPlatform())
      .findFirst();
  }

  public static @Nullable PluginLoadingError checkBuildNumberCompatibility(@NotNull IdeaPluginDescriptor descriptor,
                                                                           @NotNull BuildNumber ideBuildNumber) {
    Optional<IdeaPluginPlatform> incompatiblePlatform = getIncompatiblePlatform(descriptor);
    if (incompatiblePlatform.isPresent()) {
      IdeaPluginPlatform requiredPlatform = incompatiblePlatform.get();
      return new PluginLoadingError(descriptor,
                                    message("plugin.loading.error.long.incompatible.with.platform", descriptor.getName(),
                                            descriptor.getVersion(), requiredPlatform, SystemInfo.getOsName()),
                                    message("plugin.loading.error.short.incompatible.with.platform", requiredPlatform));
    }

    if (IGNORE_COMPATIBILITY) {
      return null;
    }

    try {
      String sinceBuild = descriptor.getSinceBuild();
      if (sinceBuild != null) {
        String pluginName = descriptor.getName();
        BuildNumber sinceBuildNumber = BuildNumber.fromString(sinceBuild, pluginName, null);
        if (sinceBuildNumber != null && sinceBuildNumber.compareTo(ideBuildNumber) > 0) {
          return new PluginLoadingError(descriptor,
                                        message("plugin.loading.error.long.incompatible.since.build", pluginName,
                                                descriptor.getVersion(), sinceBuild, ideBuildNumber),
                                        message("plugin.loading.error.short.incompatible.since.build", sinceBuild));
        }
      }

      String untilBuild = descriptor.getUntilBuild();
      if (untilBuild != null) {
        String pluginName = descriptor.getName();
        BuildNumber untilBuildNumber = BuildNumber.fromString(untilBuild, pluginName, null);
        if (untilBuildNumber != null && untilBuildNumber.compareTo(ideBuildNumber) < 0) {
          return new PluginLoadingError(descriptor,
                                        message("plugin.loading.error.long.incompatible.until.build", pluginName,
                                                descriptor.getVersion(), untilBuild, ideBuildNumber),
                                        message("plugin.loading.error.short.incompatible.until.build", untilBuild));
        }
      }
    }
    catch (Exception e) {
      getLogger().error(e);
      return new PluginLoadingError(descriptor,
                                    message("plugin.loading.error.long.failed.to.load.requirements.for.ide.version",
                                            descriptor.getName()),
                                    message("plugin.loading.error.short.failed.to.load.requirements.for.ide.version"));
    }
    return null;
  }

  @TestOnly
  public static boolean isIgnoreCompatibility() {
    return IGNORE_COMPATIBILITY;
  }

  @TestOnly
  public static void setIgnoreCompatibility(boolean ignoreCompatibility) {
    IGNORE_COMPATIBILITY = ignoreCompatibility;
  }

  private static void checkEssentialPluginsAreAvailable(@NotNull Map<PluginId, IdeaPluginDescriptorImpl> idMap) {
    List<PluginId> required = ApplicationInfoImpl.getShadowInstance().getEssentialPluginsIds();
    List<String> missing = null;
    for (PluginId id : required) {
      IdeaPluginDescriptorImpl descriptor = idMap.get(id);
      if (descriptor == null || !descriptor.isEnabled()) {
        if (missing == null) {
          missing = new ArrayList<>();
        }
        missing.add(id.getIdString());
      }
    }

    if (missing != null) {
      throw new EssentialPluginMissingException(missing);
    }
  }

  static @NotNull PluginManagerState initializePlugins(@NotNull DescriptorListLoadingContext context,
                                                       @NotNull PluginLoadingResult loadingResult,
                                                       @NotNull ClassLoader coreLoader,
                                                       boolean checkEssentialPlugins,
                                                       @Nullable Activity parentActivity) {
    Map<PluginId, PluginLoadingError> pluginErrorsById = loadingResult.copyPluginErrors$intellij_platform_core_impl();
    List<Supplier<String>> globalErrors = context.copyGlobalErrors$intellij_platform_core_impl();

    if (loadingResult.duplicateModuleMap != null) {
      for (Map.Entry<PluginId, List<IdeaPluginDescriptorImpl>> entry : loadingResult.duplicateModuleMap.entrySet()) {
        globalErrors.add(() -> {
          return CoreBundle.message("plugin.loading.error.module.declared.by.multiple.plugins", entry.getKey(),
                                    entry.getValue().stream().map(IdeaPluginDescriptorImpl::toString).collect(Collectors.joining("\n  ")));
        });
      }
    }

    Map<PluginId, IdeaPluginDescriptorImpl> idMap = loadingResult.getIdMap();
    if (checkEssentialPlugins && !idMap.containsKey(CORE_ID)) {
      throw new EssentialPluginMissingException(Collections.singletonList(CORE_ID + " (platform prefix: " +
                                                                          System.getProperty(PlatformUtils.PLATFORM_PREFIX_KEY) + ")"));
    }

    Activity activity = parentActivity == null ? null : parentActivity.startChild("3rd-party plugins consent");
    List<IdeaPluginDescriptorImpl> aliens = new ArrayList<>();
    for (PluginId id : get3rdPartyPluginIds()) {
      IdeaPluginDescriptorImpl pluginDescriptor = idMap.get(id);
      if (pluginDescriptor != null) {
        aliens.add(pluginDescriptor);
      }
    }
    if (!aliens.isEmpty()) {
      check3rdPartyPluginsPrivacyConsent(aliens);
    }
    if (activity != null) {
      activity.end();
    }

    PluginSetBuilder pluginSetBuilder = new PluginSetBuilder(loadingResult.enabledPluginsById.values());
    disableIncompatiblePlugins(pluginSetBuilder.getUnsortedPlugins(), idMap, pluginErrorsById);
    pluginSetBuilder.checkPluginCycles(globalErrors);

    Map<PluginId, String> pluginsToDisable = new HashMap<>();
    Map<PluginId, String> pluginsToEnable = new HashMap<>();

    pluginSetBuilder.computeEnabledModuleMap(descriptor -> {
      Set<PluginId> disabledPlugins = context.disabledPlugins;
      PluginLoadingError loadingError = pluginSetBuilder.initEnableState$intellij_platform_core_impl(descriptor,
                                                                                                     idMap,
                                                                                                     disabledPlugins,
                                                                                                     pluginErrorsById);

      PluginId pluginId = descriptor.getPluginId();
      boolean isLoadable = loadingError == null;

      boolean isLoadableOnDemand = descriptor.isOnDemand() &&
                                   !context.enabledOnDemandPlugins.contains(pluginId);
      if (!isLoadable) {
        pluginErrorsById.put(pluginId, loadingError);
        pluginsToDisable.put(pluginId, descriptor.getName());

        PluginId disabledDependencyId = loadingError.disabledDependency;
        if (disabledDependencyId != null &&
            (disabledPlugins.contains(disabledDependencyId) || isLoadableOnDemand)) {
          pluginsToEnable.put(disabledDependencyId, idMap.get(disabledDependencyId).getName());
        }
      }

      boolean shouldLoad = !context.expiredPlugins.contains(pluginId) &&
                           !isLoadableOnDemand;

      descriptor.setEnabled(descriptor.isEnabled()
                            && isLoadable && shouldLoad);
      return !descriptor.isEnabled();
    });

    List<Supplier<? extends HtmlChunk>> actions = prepareActions(pluginsToDisable.values(),
                                                                 pluginsToEnable.values());
    pluginLoadingErrors = pluginErrorsById;

    List<Supplier<HtmlChunk>> errorList = preparePluginErrors(globalErrors);
    if (!errorList.isEmpty()) {
      synchronized (pluginErrors) {
        pluginErrors.addAll(errorList);
        pluginErrors.addAll(actions);
      }
    }

    if (checkEssentialPlugins) {
      checkEssentialPluginsAreAvailable(idMap);
    }

    PluginSet pluginSet = pluginSetBuilder.createPluginSet(loadingResult.getIncompleteIdMap().values());
    new ClassLoaderConfigurator(pluginSet, coreLoader).configure();
    return new PluginManagerState(pluginSet,
                                  pluginsToDisable.keySet(),
                                  pluginsToEnable.keySet());
  }

  private static void check3rdPartyPluginsPrivacyConsent(@NotNull List<IdeaPluginDescriptorImpl> aliens) {
    if (GraphicsEnvironment.isHeadless()) {
      getLogger().info("3rd-party plugin privacy note not accepted yet; disabling plugins for this headless session");
      aliens.forEach(descriptor -> descriptor.setEnabled(false));
    }
    else if (!ask3rdPartyPluginsPrivacyConsent(aliens)) {
      getLogger().info("3rd-party plugin privacy note declined; disabling plugins");
      aliens.forEach(descriptor -> descriptor.setEnabled(false));
      PluginEnabler.HEADLESS.disable(aliens);
      thirdPartyPluginsNoteAccepted = Boolean.FALSE;
    }
    else {
      thirdPartyPluginsNoteAccepted = Boolean.TRUE;
    }
  }

  @ApiStatus.Internal
  public static @Nullable Boolean isThirdPartyPluginsNoteAccepted() {
    Boolean result = thirdPartyPluginsNoteAccepted;
    thirdPartyPluginsNoteAccepted = null;
    return result;
  }

  @ApiStatus.Internal
  static synchronized void write3rdPartyPlugins(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors) {
    Path path = PathManager.getConfigDir().resolve(THIRD_PARTY_PLUGINS_FILE);
    try {
      writePluginIdsToFile(path,
                           descriptors.stream().map(IdeaPluginDescriptor::getPluginId),
                           StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
    }
    catch (IOException e) {
      getLogger().error(path.toString(), e);
    }
  }

  @ReviseWhenPortedToJDK(value = "10", description = "Set.of")
  private static @NotNull Set<PluginId> get3rdPartyPluginIds() {
    Path path = PathManager.getConfigDir().resolve(THIRD_PARTY_PLUGINS_FILE);
    try {
      Set<PluginId> ids = readPluginIdsFromFile(path);
      if (!ids.isEmpty()) {
        Files.delete(path);
      }
      return ids;
    }
    catch (IOException e) {
      getLogger().error(path.toString(), e);
      return Collections.emptySet();
    }
  }

  @ReviseWhenPortedToJDK(value = "10, 11", description = "toUnmodifiableSet, Set.of, String.isBlank")
  @ApiStatus.Internal
  public synchronized static @NotNull Set<PluginId> readPluginIdsFromFile(@NotNull Path path) throws IOException {
    try (Stream<String> lines = Files.lines(path)) {
      return lines
        .map(String::trim)
        .filter(line -> !line.isEmpty())
        .map(PluginId::getId)
        .collect(Collectors.toSet());
    }
    catch (NoSuchFileException ignored) {
      return Collections.emptySet();
    }
  }

  @ApiStatus.Internal
  public synchronized static @NotNull Set<PluginId> tryReadPluginIdsFromFile(@NotNull Path path,
                                                                             @NotNull Logger logger) {
    try {
      return readPluginIdsFromFile(path);
    }
    catch (IOException e) {
      logger.warn("Unable to read plugin id list from: " + path, e);
      return Collections.emptySet();
    }
  }

  @ApiStatus.Internal
  public synchronized static void writePluginIdsToFile(@NotNull Path path,
                                                       @NotNull Set<PluginId> pluginIds,
                                                       OpenOption... openOptions) throws IOException {
    writePluginIdsToFile(path,
                         pluginIds.stream(),
                         openOptions);
  }

  @ApiStatus.Internal
  public synchronized static boolean tryWritePluginIdsToFile(@NotNull Path path,
                                                             @NotNull Set<PluginId> pluginIds,
                                                             @NotNull Logger logger,
                                                             OpenOption... openOptions) {
    try {
      writePluginIdsToFile(path, pluginIds, openOptions);
      return true;
    }
    catch (IOException e) {
      logger.warn("Unable to write plugin id list to: " + path, e);
      return false;
    }
  }

  @ReviseWhenPortedToJDK(value = "10", description = "toUnmodifiableList")
  @ApiStatus.Internal
  public synchronized static void writePluginIdsToFile(@NotNull Path path,
                                                       @NotNull Stream<PluginId> pluginIds,
                                                       OpenOption... openOptions) throws IOException {
    writePluginIdsToFile(path,
                         pluginIds.map(PluginId::getIdString).collect(Collectors.toList()),
                         openOptions);
  }

  @VisibleForTesting
  public synchronized static void writePluginIdsToFile(@NotNull Path path,
                                                       @NotNull Collection<String> pluginIds,
                                                       OpenOption... openOptions) throws IOException {
    NioFiles.createDirectories(path.getParent());
    Files.write(path,
                new TreeSet<>(pluginIds),
                openOptions);
  }

  @ReviseWhenPortedToJDK(value = "10", description = "toUnmodifiableSet")
  @VisibleForTesting
  public static @NotNull Set<PluginId> toPluginIds(@NotNull Collection<String> pluginIdStrings) {
    Set<PluginId> pluginIds = pluginIdStrings.stream()
      .map(String::trim)
      .filter(s -> !s.isEmpty())
      .map(PluginId::getId)
      .collect(Collectors.toSet());
    return Collections.unmodifiableSet(pluginIds);
  }

  private static boolean ask3rdPartyPluginsPrivacyConsent(@NotNull List<IdeaPluginDescriptorImpl> descriptors) {
    String title = CoreBundle.message("third.party.plugins.privacy.note.title");
    String pluginList = descriptors.stream()
      .map(descriptor -> "&nbsp;&nbsp;&nbsp;" + descriptor.getName() + " (" + descriptor.getVendor() + ')')
      .collect(Collectors.joining("<br>"));
    String text = CoreBundle.message("third.party.plugins.privacy.note.text", pluginList);
    String[] buttons =
      {CoreBundle.message("third.party.plugins.privacy.note.accept"), CoreBundle.message("third.party.plugins.privacy.note.disable")};
    int choice = JOptionPane.showOptionDialog(null, text, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE,
                                              IconManager.getInstance().getPlatformIcon(PlatformIcons.WarningDialog), buttons, buttons[0]);
    return choice == 0;
  }

  @SuppressWarnings("DuplicatedCode")
  private static @Nullable Map<PluginId, List<IdeaPluginDescriptorImpl>> checkAndPut(@NotNull IdeaPluginDescriptorImpl descriptor,
                                                                                     @NotNull PluginId id,
                                                                                     @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idMap,
                                                                                     @Nullable Map<PluginId, List<IdeaPluginDescriptorImpl>> duplicateMap) {
    if (duplicateMap != null) {
      List<IdeaPluginDescriptorImpl> duplicates = duplicateMap.get(id);
      if (duplicates != null) {
        duplicates.add(descriptor);
        return duplicateMap;
      }
    }

    IdeaPluginDescriptorImpl existingDescriptor = idMap.put(id, descriptor);
    if (existingDescriptor == null) {
      return null;
    }

    // if duplicated, both are removed
    idMap.remove(id);
    if (duplicateMap == null) {
      duplicateMap = new LinkedHashMap<>();
    }

    List<IdeaPluginDescriptorImpl> list = new ArrayList<>();
    list.add(existingDescriptor);
    list.add(descriptor);
    duplicateMap.put(id, list);
    return duplicateMap;
  }

  private static @NotNull @Nls Supplier<String> message(@NotNull @PropertyKey(resourceBundle = CoreBundle.BUNDLE) String key,
                                                        Object @NotNull ... params) {
    //noinspection Convert2Lambda
    return new Supplier<String>() {
      @Override
      public String get() {
        return CoreBundle.message(key, params);
      }
    };
  }

  @SuppressWarnings("NonPrivateFieldAccessedInSynchronizedContext")
  static synchronized @NotNull PluginSet initializeAndSetPlugins(@NotNull DescriptorListLoadingContext context,
                                                                 @NotNull PluginLoadingResult loadingResult,
                                                                 @NotNull ClassLoader coreLoader) {
    Activity activity = StartUpMeasurer.startActivity("plugin initialization");
    PluginManagerState initResult = initializePlugins(context, loadingResult, coreLoader, !isUnitTestMode, activity);

    ourPluginsToDisable = Java11Shim.INSTANCE.copyOf(initResult.pluginIdsToDisable);
    ourPluginsToEnable = Java11Shim.INSTANCE.copyOf(initResult.pluginIdsToEnable);
    shadowedBundledPlugins = Java11Shim.INSTANCE.copyOf(loadingResult.shadowedBundledIds);

    activity.end();
    activity.setDescription("plugin count: " + initResult.pluginSet.enabledPlugins.size());
    pluginSet = initResult.pluginSet;
    return initResult.pluginSet;
  }

  @SuppressWarnings("RedundantSuppression")
  public static @NotNull Logger getLogger() {
    // do not use class reference here
    //noinspection SSBasedInspection
    return Logger.getInstance("#com.intellij.ide.plugins.PluginManager");
  }

  public static final class EssentialPluginMissingException extends RuntimeException {
    public final List<String> pluginIds;

    EssentialPluginMissingException(@NotNull List<String> ids) {
      super("Missing essential plugins: " + String.join(", ", ids));

      pluginIds = ids;
    }
  }

  @Contract("null -> null")
  public static @Nullable IdeaPluginDescriptor getPlugin(@Nullable PluginId id) {
    return id != null ? findPlugin(id) : null;
  }

  @ApiStatus.Internal
  public static @Nullable IdeaPluginDescriptorImpl findPlugin(@NotNull PluginId id) {
    PluginSet pluginSet = getPluginSet();
    IdeaPluginDescriptorImpl result = pluginSet.findEnabledPlugin(id);
    if (result != null) {
      return result;
    }

    return pluginSet.findInstalledPlugin(id);
  }

  @ApiStatus.Internal
  public static @Nullable IdeaPluginDescriptorImpl findPluginByModuleDependency(@NotNull PluginId id) {
    for (IdeaPluginDescriptorImpl descriptor : getPluginSet().allPlugins) {
      if (descriptor.modules.contains(id)) {
        return descriptor;
      }
    }
    return null;
  }

  public static boolean isPluginInstalled(@NotNull PluginId id) {
    PluginSet pluginSet = PluginManagerCore.pluginSet;
    if (pluginSet == null) {
      return false;
    }

    return pluginSet.isPluginEnabled(id) ||
           pluginSet.isPluginInstalled(id);
  }

  @ApiStatus.Internal
  public static @NotNull Map<PluginId, IdeaPluginDescriptorImpl> buildPluginIdMap() {
    LoadingState.COMPONENTS_REGISTERED.checkOccurred();
    Map<PluginId, IdeaPluginDescriptorImpl> idMap = new HashMap<>(getPluginSet().allPlugins.size());
    Map<PluginId, List<IdeaPluginDescriptorImpl>> duplicateMap = null;
    for (IdeaPluginDescriptorImpl descriptor : getPluginSet().allPlugins) {
      Map<PluginId, List<IdeaPluginDescriptorImpl>> newDuplicateMap = checkAndPut(descriptor, descriptor.getPluginId(), idMap, duplicateMap);
      if (newDuplicateMap != null) {
        duplicateMap = newDuplicateMap;
        continue;
      }

      for (PluginId module : descriptor.modules) {
        newDuplicateMap = checkAndPut(descriptor, module, idMap, duplicateMap);
        if (newDuplicateMap != null) {
          duplicateMap = newDuplicateMap;
        }
      }
    }
    return idMap;
  }

  @ApiStatus.Internal
  public static void processAllNonOptionalDependencyIds(@NotNull IdeaPluginDescriptorImpl rootDescriptor,
                                                        @NotNull Map<PluginId, IdeaPluginDescriptorImpl> pluginIdMap,
                                                        @NotNull Function<? super PluginId, FileVisitResult> consumer) {
    processAllNonOptionalDependencies(rootDescriptor,
                                      new HashSet<>(),
                                      pluginIdMap,
                                      (pluginId, __) -> consumer.apply(pluginId));
  }

  @ApiStatus.Internal
  public static boolean processAllNonOptionalDependencies(@NotNull IdeaPluginDescriptorImpl rootDescriptor,
                                                          @NotNull Map<PluginId, IdeaPluginDescriptorImpl> pluginIdMap,
                                                          @NotNull Function<? super IdeaPluginDescriptorImpl, FileVisitResult> consumer) {
    return processAllNonOptionalDependencies(rootDescriptor, new HashSet<>(), pluginIdMap, consumer);
  }

  /**
   * {@link FileVisitResult#SKIP_SIBLINGS} is not supported.
   * <p>
   * Returns {@code false} if processing was terminated because of {@link FileVisitResult#TERMINATE}, and {@code true} otherwise.
   */
  @ApiStatus.Internal
  public static boolean processAllNonOptionalDependencies(@NotNull IdeaPluginDescriptorImpl rootDescriptor,
                                                          @NotNull Set<? super IdeaPluginDescriptorImpl> depProcessed,
                                                          @NotNull Map<PluginId, IdeaPluginDescriptorImpl> pluginIdMap,
                                                          @NotNull Function<? super IdeaPluginDescriptorImpl, FileVisitResult> consumer) {

    return processAllNonOptionalDependencies(rootDescriptor,
                                             depProcessed,
                                             pluginIdMap,
                                             (__, descriptor) -> consumer.apply(descriptor));
  }

  private static boolean processAllNonOptionalDependencies(@NotNull IdeaPluginDescriptorImpl rootDescriptor,
                                                           @NotNull Set<? super IdeaPluginDescriptorImpl> depProcessed,
                                                           @NotNull Map<PluginId, IdeaPluginDescriptorImpl> pluginIdMap,
                                                           @NotNull BiFunction<? super PluginId, ? super IdeaPluginDescriptorImpl, ? extends FileVisitResult> consumer) {
    for (PluginId dependencyId : getNonOptionalDependenciesIds(rootDescriptor)) {
      IdeaPluginDescriptorImpl descriptor = pluginIdMap.get(dependencyId);
      PluginId pluginId = descriptor != null ? descriptor.getPluginId() : dependencyId;

      FileVisitResult result = consumer.apply(pluginId, descriptor);
      switch (result) {
        case TERMINATE:
          return false;
        case CONTINUE:
          if (descriptor != null && depProcessed.add(descriptor)) {
            processAllNonOptionalDependencies(descriptor, depProcessed, pluginIdMap, consumer);
          }
          break;
        case SKIP_SUBTREE:
          break;
        case SKIP_SIBLINGS:
          throw new UnsupportedOperationException("FileVisitResult.SKIP_SIBLINGS is not supported");
      }
    }

    return true;
  }

  @ApiStatus.Internal
  public static @NotNull Set<PluginId> getNonOptionalDependenciesIds(@NotNull IdeaPluginDescriptorImpl descriptor) {
    Set<PluginId> dependencies = new LinkedHashSet<>();

    for (PluginDependency dependency : descriptor.pluginDependencies) {
      if (!dependency.isOptional()) {
        dependencies.add(dependency.getPluginId());
      }
    }

    for (ModuleDependenciesDescriptor.PluginReference plugin : descriptor.dependencies.plugins) {
      dependencies.add(plugin.id);
    }

    return Collections.unmodifiableSet(dependencies);
  }

  @ApiStatus.Internal
  public static synchronized boolean isUpdatedBundledPlugin(@NotNull PluginDescriptor plugin) {
    return shadowedBundledPlugins != null && shadowedBundledPlugins.contains(plugin.getPluginId());
  }

  //<editor-fold desc="Deprecated stuff.">

  /** @deprecated Use {@link #disablePlugin(PluginId)} */
  @Deprecated
  public static boolean disablePlugin(@NotNull String id) {
    return disablePlugin(PluginId.getId(id));
  }

  /** @deprecated Use {@link #enablePlugin(PluginId)} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static boolean enablePlugin(@NotNull String id) {
    return enablePlugin(PluginId.getId(id));
  }

  /** @deprecated Use {@link DisabledPluginsState#addDisablePluginListener} directly
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static void addDisablePluginListener(@NotNull Runnable listener) {
    DisabledPluginsState.addDisablePluginListener(listener);
  }
  //</editor-fold>
}
