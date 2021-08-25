// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.core.CoreBundle;
import com.intellij.diagnostic.*;
import com.intellij.ide.plugins.cl.PluginAwareClassLoader;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.*;

import java.awt.*;
import java.io.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.nio.file.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Prefer to use only JDK classes. Any post start-up functionality should be placed in PluginManager class.
/**
 * See <a href="https://github.com/JetBrains/intellij-community/blob/master/platform/core-impl/src/com/intellij/ide/plugins/readme.md">Plugin Model V2 documentation</a>
 */
public final class PluginManagerCore {
  public static final @NonNls String META_INF = "META-INF/";

  public static final PluginId CORE_ID = PluginId.getId("com.intellij");
  public static final String CORE_PLUGIN_ID = "com.intellij";

  public static final PluginId JAVA_PLUGIN_ID = PluginId.getId("com.intellij.java");
  static final PluginId JAVA_MODULE_ID = PluginId.getId("com.intellij.modules.java");

  public static final String PLUGIN_XML = "plugin.xml";
  public static final String PLUGIN_XML_PATH = META_INF + PLUGIN_XML;
  static final PluginId ALL_MODULES_MARKER = PluginId.getId("com.intellij.modules.all");

  public static final String VENDOR_JETBRAINS = "JetBrains";
  public static final String VENDOR_JETBRAINS_SRO = "JetBrains s.r.o.";

  private static final String MODULE_DEPENDENCY_PREFIX = "com.intellij.module";

  public static final PluginId SPECIAL_IDEA_PLUGIN_ID = PluginId.getId("IDEA CORE");

  static final String PROPERTY_PLUGIN_PATH = "plugin.path";

  static final @NonNls String DISABLE = "disable";
  static final @NonNls String ENABLE = "enable";
  static final @NonNls String EDIT = "edit";

  private static final boolean IGNORE_DISABLED_PLUGINS = Boolean.getBoolean("idea.ignore.disabled.plugins");
  private static final MethodType HAS_LOADED_CLASS_METHOD_TYPE = MethodType.methodType(boolean.class, String.class);

  private static Reference<Map<PluginId, Set<String>>> brokenPluginVersions;
  private static volatile @Nullable PluginSet pluginSet;
  private static Map<PluginId, PluginLoadingError> pluginLoadingErrors;

  @SuppressWarnings("StaticNonFinalField")
  public static volatile boolean isUnitTestMode = Boolean.getBoolean("idea.is.unit.test");

  @ApiStatus.Internal
  private static final List<Supplier<? extends HtmlChunk>> pluginErrors = new ArrayList<>();

  private static Set<PluginId> ourPluginsToDisable;
  private static Set<PluginId> ourPluginsToEnable;

  /**
   * Bundled plugins that were updated.
   * When we update bundled plugin it becomes not bundled, so it is more difficult for analytics to use that data.
   */
  private static Set<PluginId> ourShadowedBundledPlugins;

  private static Boolean isRunningFromSources;
  private static volatile CompletableFuture<DescriptorListLoadingContext> descriptorListFuture;

  private static BuildNumber ourBuildNumber;

  /**
   * Returns list of all available plugin descriptors (bundled and custom, include disabled ones). Use {@link #getLoadedPlugins()}
   * if you need to get loaded plugins only.
   *
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

  /**
   * Returns descriptors of plugins which are successfully loaded into IDE. The result is sorted in a way that if each plugin comes after
   * the plugins it depends on.
   */
  public static @NotNull List<? extends IdeaPluginDescriptor> getLoadedPlugins() {
    return getPluginSet().enabledPlugins;
  }

  @ApiStatus.Internal
  public static @NotNull List<IdeaPluginDescriptorImpl> getLoadedPlugins(@Nullable ClassLoader coreClassLoader) {
    PluginSet result = pluginSet;
    if (result != null) {
      return result.enabledPlugins;
    }
    return loadAndInitializePlugins(PluginDescriptorLoader.loadDescriptors(isUnitTestMode, isRunningFromSources()),
                                    coreClassLoader == null ? PluginManagerCore.class.getClassLoader() : coreClassLoader).enabledPlugins;
  }

  @ApiStatus.Internal
  public static @NotNull List<HtmlChunk> getAndClearPluginLoadingErrors() {
    synchronized (pluginErrors) {
      List<HtmlChunk> errors = ContainerUtil.map(pluginErrors, Supplier::get);
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
    return DisabledPluginsState.isDisabled(pluginId);
  }

  public static boolean isBrokenPlugin(@NotNull IdeaPluginDescriptor descriptor) {
    PluginId pluginId = descriptor.getPluginId();
    Set<String> set = getBrokenPluginVersions().get(pluginId);
    return set != null && set.contains(descriptor.getVersion());
  }

  public static void updateBrokenPlugins(Map<PluginId, Set<String>> brokenPlugins) {
    brokenPluginVersions = new SoftReference<>(brokenPlugins);
    Path updatedBrokenPluginFile = getUpdatedBrokenPluginFile();
    try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(updatedBrokenPluginFile), 32_000))) {
      out.write(1);
      out.writeInt(brokenPlugins.size());
      for (Map.Entry<PluginId, Set<String>> entry : brokenPlugins.entrySet()) {
        out.writeUTF(entry.getKey().getIdString());
        out.writeShort(entry.getValue().size());
        for (String s : entry.getValue()) {
          out.writeUTF(s);
        }
      }
    }
    catch (NoSuchFileException ignore) {
    }
    catch (IOException e) {
      getLogger().error("Failed to read " + updatedBrokenPluginFile, e);
    }
  }

  static @NotNull Map<@NotNull PluginId, @NotNull Set<String>> getBrokenPluginVersions() {
    if (IGNORE_DISABLED_PLUGINS) {
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
    Path updatedBrokenPluginFile = getUpdatedBrokenPluginFile();
    Path brokenPluginsStorage;
    if (Files.exists(updatedBrokenPluginFile)) {
      brokenPluginsStorage = updatedBrokenPluginFile;
    }
    else {
      brokenPluginsStorage = Paths.get(PathManager.getBinPath() + "/brokenPlugins.db");
    }
    try (DataInputStream stream = new DataInputStream(new BufferedInputStream(Files.newInputStream(brokenPluginsStorage), 32_000))) {
      int version = stream.readUnsignedByte();
      if (version != 1) {
        getLogger().error("Unsupported version of " + brokenPluginsStorage + "(fileVersion=" + version + ", supportedVersion=1)");
        return Collections.emptyMap();
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
    return Collections.emptyMap();
  }

  public static void writePluginsList(@NotNull Collection<PluginId> ids, @NotNull Writer writer) throws IOException {
    List<PluginId> sortedIds = new ArrayList<>(ids);
    sortedIds.sort(null);
    for (PluginId id : sortedIds) {
      writer.write(id.getIdString());
      writer.write('\n');
    }
  }

  public static boolean disablePlugin(@NotNull PluginId id) {
    return DisabledPluginsState.setEnabledState(Collections.singleton(id), false);
  }

  public static boolean enablePlugin(@NotNull PluginId id) {
    return DisabledPluginsState.setEnabledState(Collections.singleton(id), true);
  }

  public static boolean isModuleDependency(@NotNull PluginId dependentPluginId) {
    return dependentPluginId.getIdString().startsWith(MODULE_DEPENDENCY_PREFIX);
  }

  /**
   * This is an internal method, use {@link PluginException#createByClass(String, Throwable, Class)} instead.
   */
  @ApiStatus.Internal
  public static @NotNull PluginException createPluginException(@NotNull String errorMessage, @Nullable Throwable cause,
                                                               @NotNull Class<?> pluginClass) {
    ClassLoader classLoader = pluginClass.getClassLoader();
    PluginId pluginId;
    if (classLoader instanceof PluginAwareClassLoader) {
      pluginId = ((PluginAwareClassLoader)classLoader).getPluginId();
    }
    else {
      pluginId = getPluginByClassName(pluginClass.getName());
    }
    return new PluginException(errorMessage, cause, pluginId);
  }

  public static @Nullable PluginId getPluginByClassName(@NotNull String className) {
    PluginId id = getPluginOrPlatformByClassName(className);
    return (id != null && !CORE_ID.equals(id)) ? id : null;
  }

  public static @Nullable PluginId getPluginOrPlatformByClassName(@NotNull String className) {
    PluginDescriptor result = getPluginDescriptorOrPlatformByClassName(className);
    return result == null ? null : result.getPluginId();
  }

  @ApiStatus.Internal
  public static @Nullable PluginDescriptor getPluginDescriptorOrPlatformByClassName(@NotNull @NonNls String className) {
    PluginSet pluginSet = PluginManagerCore.pluginSet;
    if (pluginSet == null ||
        className.startsWith("java.") ||
        className.startsWith("javax.") ||
        className.startsWith("kotlin.") ||
        className.startsWith("groovy.") ||
        !className.contains(".")) {
      return null;
    }

    IdeaPluginDescriptor result = null;
    for (IdeaPluginDescriptorImpl o : pluginSet.enabledPlugins) {
      ClassLoader classLoader = o.getPluginClassLoader();
      if (!hasLoadedClass(className, classLoader)) {
        continue;
      }

      result = o;
      break;
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
    String root = null;
    for (IdeaPluginDescriptorImpl o : pluginSet.enabledPlugins) {
      if (!o.isUseIdeaClassLoader) {
        continue;
      }

      if (root == null) {
        root = PathManager.getResourceRoot(result.getPluginClassLoader(), className.replace('.', '/') + ".class");
        if (root == null) {
          return null;
        }
      }

      Path path = o.getPluginPath();
      if (root.startsWith(FileUtilRt.toSystemIndependentName(path.toString()))) {
        return o;
      }
    }
    return null;
  }

  public static boolean isDevelopedByJetBrains(@NotNull PluginDescriptor plugin) {
    String vendor = plugin.getVendor();
    if (vendor == null &&
        !(plugin.getPluginClassLoader() instanceof PluginClassLoader) &&
        plugin instanceof IdeaPluginDescriptorImpl && !((IdeaPluginDescriptorImpl)plugin).isUseIdeaClassLoader &&
        ApplicationInfoEx.getInstanceEx().isVendorJetBrains()) {
      return true;
    }
    return isDevelopedByJetBrains(vendor) || isDevelopedByJetBrains(vendor);
  }

  public static boolean isDevelopedByJetBrains(@Nullable String vendorString) {
    if (vendorString == null) {
      return false;
    }

    if (isVendorJetBrains(vendorString)) {
      return true;
    }

    for (String vendor : StringUtil.split(vendorString, ",")) {
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

  private static Path getUpdatedBrokenPluginFile(){
    return Paths.get(PathManager.getConfigPath()).resolve("updatedBrokenPlugins.db");
  }

  private static boolean hasLoadedClass(@NotNull String className, @NotNull ClassLoader loader) {
    if (loader instanceof UrlClassLoader) {
      return ((UrlClassLoader)loader).hasLoadedClass(className);
    }

    // it can be an UrlClassLoader loaded by another class loader, so instanceof doesn't work
    Class<?> aClass = loader.getClass();
    if (aClass.isAnonymousClass() || aClass.isMemberClass()) {
      aClass = aClass.getSuperclass();
    }
    try {
      return (boolean)MethodHandles.publicLookup().findVirtual(aClass, "hasLoadedClass", HAS_LOADED_CLASS_METHOD_TYPE)
        .invoke(loader, className);
    }
    catch (NoSuchMethodError | IllegalAccessError | IllegalAccessException ignore) {
    }
    catch (Throwable e) {
      getLogger().error(e);
    }
    return false;
  }

  static boolean hasModuleDependencies(@NotNull IdeaPluginDescriptorImpl descriptor) {
    for (PluginDependency dependency : descriptor.pluginDependencies) {
      PluginId dependencyPluginId = dependency.getPluginId();
      if (JAVA_PLUGIN_ID.equals(dependencyPluginId) ||
          JAVA_MODULE_ID.equals(dependencyPluginId) ||
          isModuleDependency(dependencyPluginId)) {
        return true;
      }
    }
    return false;
  }

  public static synchronized void invalidatePlugins() {
    pluginSet = null;

    CompletableFuture<DescriptorListLoadingContext> future = descriptorListFuture;
    if (future != null) {
      descriptorListFuture = null;
      future.cancel(false);
    }
    DisabledPluginsState.invalidate();
    ourShadowedBundledPlugins = null;
  }

  private static void logPlugins(@NotNull List<IdeaPluginDescriptorImpl> plugins,
                                 @NotNull Collection<IdeaPluginDescriptorImpl> incompletePlugins) {
    StringBuilder bundled = new StringBuilder();
    StringBuilder disabled = new StringBuilder();
    StringBuilder custom = new StringBuilder();
    Set<PluginId> disabledPlugins = new HashSet<>();
    for (IdeaPluginDescriptor descriptor : plugins) {
      StringBuilder target;
      PluginId pluginId = descriptor.getPluginId();
      if (!descriptor.isEnabled()) {
        if (!isDisabled(pluginId)) {
          // plugin will be logged as part of "Problems found loading plugins"
          continue;
        }
        disabledPlugins.add(pluginId);
        target = disabled;
      }
      else if (descriptor.isBundled() || SPECIAL_IDEA_PLUGIN_ID.equals(pluginId)) {
        target = bundled;
      }
      else {
        target = custom;
      }

      appendPlugin(descriptor, target);
    }
    for (IdeaPluginDescriptorImpl plugin : incompletePlugins) {
      // log only explicitly disabled plugins
      PluginId pluginId = plugin.getPluginId();
      if (isDisabled(pluginId) &&
          !disabledPlugins.contains(pluginId)) {
        appendPlugin(plugin, disabled);
      }
    }

    Logger logger = getLogger();
    logger.info("Loaded bundled plugins: " + bundled);
    if (custom.length() > 0) {
      logger.info("Loaded custom plugins: " + custom);
    }
    if (disabled.length() > 0) {
      logger.info("Disabled plugins: " + disabled);
    }
  }

  private static void appendPlugin(IdeaPluginDescriptor descriptor, StringBuilder target) {
    if (target.length() > 0) {
      target.append(", ");
    }

    target.append(descriptor.getName());
    String version = descriptor.getVersion();
    if (version != null) {
      target.append(" (").append(version).append(')');
    }
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
  private static @NotNull List<Supplier<HtmlChunk>> preparePluginsError(@NotNull List<Supplier<@NlsContexts.DetailedDescription String>> globalErrorsSuppliers) {
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

  private static @NotNull List<Supplier<HtmlChunk>> prepareActions(@NotNull Set<IdeaPluginDescriptorImpl> disabledIds,
                                                                   @NotNull Set<IdeaPluginDescriptorImpl> disabledRequiredIds) {
    if (disabledIds.isEmpty()) {
      return Collections.emptyList();
    }

    List<Supplier<HtmlChunk>> actions = new ArrayList<>();
    String nameToDisable = getFirstPluginName(disabledIds);
    actions.add(() -> {
      return HtmlChunk.link(DISABLE, nameToDisable == null
                                     ? CoreBundle.message("link.text.disable.not.loaded.plugins")
                                     : CoreBundle.message("link.text.disable.plugin", nameToDisable));
    });
    if (!disabledRequiredIds.isEmpty()) {
      String nameToEnable = getFirstPluginName(disabledRequiredIds);
      actions.add(() -> {
        return HtmlChunk.link(ENABLE, nameToEnable == null
                                      ? CoreBundle.message("link.text.enable.all.necessary.plugins")
                                      : CoreBundle.message("link.text.enable.plugin", nameToEnable));
      });
    }
    actions.add(() -> HtmlChunk.link(EDIT, CoreBundle.message("link.text.open.plugin.manager")));
    return actions;
  }

  private static @Nullable @NlsSafe String getFirstPluginName(@NotNull Set<IdeaPluginDescriptorImpl> modules) {
    switch (modules.size()) {
      case 0:
        throw new IllegalArgumentException("Plugins set should not be empty");
      case 1:
        return modules.iterator().next().getName();
      default:
        return null;
    }
  }

  @ApiStatus.Internal
  static synchronized boolean onEnable(boolean enabled) {
    Set<PluginId> pluginIds = enabled ? ourPluginsToEnable : ourPluginsToDisable;
    ourPluginsToEnable = null;
    ourPluginsToDisable = null;

    boolean applied = pluginIds != null;
    if (applied) {
      for (IdeaPluginDescriptorImpl module : getPluginSet().allPlugins) {
        if (pluginIds.contains(module.getPluginId())) {
          module.setEnabled(enabled);
        }
      }

      DisabledPluginsState.setEnabledState(pluginIds, enabled);
    }
    return applied;
  }

  // separate method to avoid exposing of DescriptorListLoadingContext class
  public static void scheduleDescriptorLoading() {
    getOrScheduleLoading();
  }

  private static synchronized @NotNull CompletableFuture<DescriptorListLoadingContext> getOrScheduleLoading() {
    CompletableFuture<DescriptorListLoadingContext> future = descriptorListFuture;
    if (future != null) {
      return future;
    }

    future = CompletableFuture.supplyAsync(() -> {
      Activity activity = StartUpMeasurer.startActivity("plugin descriptor loading", ActivityCategory.DEFAULT);
      DescriptorListLoadingContext context = PluginDescriptorLoader.loadDescriptors(isUnitTestMode, isRunningFromSources());
      activity.end();
      return context;
    }, ForkJoinPool.commonPool());
    descriptorListFuture = future;
    return future;
  }

  /**
   * Think twice before use and get approve from core team. Returns enabled plugins only.
   */
  @ApiStatus.Internal
  public static @NotNull CompletableFuture<List<IdeaPluginDescriptorImpl>> getEnabledPluginRawList() {
    return getOrScheduleLoading().thenApply(it -> it.result.getEnabledPlugins());
  }

  @ApiStatus.Internal
  public static @NotNull CompletableFuture<PluginSet> initPlugins(@NotNull ClassLoader coreClassLoader) {
    CompletableFuture<DescriptorListLoadingContext> future = descriptorListFuture;
    if (future == null) {
      throw new IllegalStateException("Call scheduleDescriptorLoading() first");
    }
    return future.thenApply(context -> {
      return loadAndInitializePlugins(context, coreClassLoader);
    });
  }

  public static @NotNull BuildNumber getBuildNumber() {
    BuildNumber result = ourBuildNumber;
    if (result == null) {
      result = BuildNumber.fromPluginsCompatibleBuild();
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

  private static void disableIncompatiblePlugins(@NotNull List<IdeaPluginDescriptorImpl> descriptors,
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
        processAllNonOptionalDependencies(descriptor, idMap, (__, dependency) -> {
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
                                                                       message("plugin.loading.error.long.plugin.loading.disabled",
                                                                               descriptor.getName()),
                                                                       message("plugin.loading.error.short.plugin.loading.disabled")));
      }
    }
  }

  public static boolean isCompatible(@NotNull IdeaPluginDescriptor descriptor) {
    return !isIncompatible(descriptor);
  }

  public static boolean isCompatible(@NotNull IdeaPluginDescriptor descriptor, @Nullable BuildNumber buildNumber) {
    return !isIncompatible(descriptor, buildNumber);
  }

  public static boolean isIncompatible(@NotNull IdeaPluginDescriptor descriptor) {
    return isIncompatible(descriptor, getBuildNumber());
  }

  public static boolean isIncompatible(@NotNull IdeaPluginDescriptor descriptor, @Nullable BuildNumber buildNumber) {
    if (buildNumber == null) {
      buildNumber = getBuildNumber();
    }
    return checkBuildNumberCompatibility(descriptor, buildNumber) != null;
  }

  public static @Nullable PluginLoadingError checkBuildNumberCompatibility(@NotNull IdeaPluginDescriptor descriptor,
                                                                           @NotNull BuildNumber ideBuildNumber) {
    String sinceBuild = descriptor.getSinceBuild();
    String untilBuild = descriptor.getUntilBuild();
    try {
      BuildNumber sinceBuildNumber = sinceBuild == null ? null : BuildNumber.fromString(sinceBuild, descriptor.getName(), null);
      if (sinceBuildNumber != null && sinceBuildNumber.compareTo(ideBuildNumber) > 0) {
        return new PluginLoadingError(descriptor, message("plugin.loading.error.long.incompatible.since.build", descriptor.getName(), descriptor.getVersion(), sinceBuild, ideBuildNumber),
                                         message("plugin.loading.error.short.incompatible.since.build", sinceBuild));
      }

      BuildNumber untilBuildNumber = untilBuild == null ? null : BuildNumber.fromString(untilBuild, descriptor.getName(), null);
      if (untilBuildNumber != null && untilBuildNumber.compareTo(ideBuildNumber) < 0) {
        return new PluginLoadingError(descriptor, message("plugin.loading.error.long.incompatible.until.build", descriptor.getName(), descriptor.getVersion(), untilBuild, ideBuildNumber),
                                         message("plugin.loading.error.short.incompatible.until.build", untilBuild));
      }
      return null;
    }
    catch (Exception e) {
      getLogger().error(e);
      return new PluginLoadingError(descriptor,
                                       message("plugin.loading.error.long.failed.to.load.requirements.for.ide.version", descriptor.getName()),
                                       message("plugin.loading.error.short.failed.to.load.requirements.for.ide.version"));
    }
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
                                                       @NotNull ClassLoader coreLoader,
                                                       boolean checkEssentialPlugins) {
    PluginLoadingResult loadingResult = context.result;
    Map<PluginId, PluginLoadingError> pluginErrorsById = new HashMap<>(loadingResult.getPluginErrors$intellij_platform_core_impl());
    @NotNull List<Supplier<String>> globalErrors = loadingResult.getGlobalErrors();

    if (loadingResult.duplicateModuleMap != null) {
      for (Map.Entry<PluginId, List<IdeaPluginDescriptorImpl>> entry : loadingResult.duplicateModuleMap.entrySet()) {
        globalErrors.add(() -> {
          return CoreBundle.message("plugin.loading.error.module.declared.by.multiple.plugins", entry.getKey(),
                                    entry.getValue().stream().map(IdeaPluginDescriptorImpl::toString).collect(Collectors.joining("\n  ")));
        });
      }
    }

    Map<PluginId, IdeaPluginDescriptorImpl> idMap = loadingResult.idMap;
    if (checkEssentialPlugins && !idMap.containsKey(CORE_ID)) {
      throw new EssentialPluginMissingException(
        Collections.singletonList(CORE_ID + " (platform prefix: " + System.getProperty(PlatformUtils.PLATFORM_PREFIX_KEY) + ")"));
    }

    List<IdeaPluginDescriptorImpl> descriptors = loadingResult.getEnabledPlugins();
    disableIncompatiblePlugins(descriptors, idMap, pluginErrorsById);
    PluginSetBuilder pluginSetBuilder = new PluginSetBuilder(descriptors);
    pluginSetBuilder.checkPluginCycles(globalErrors);

    Set<IdeaPluginDescriptorImpl> disabledAfterInit = new HashSet<>();
    Set<IdeaPluginDescriptorImpl> disabledRequired = new HashSet<>();

    pluginSetBuilder.computeEnabledModuleMap(descriptor -> {
      if (pluginSetBuilder.initEnableState$intellij_platform_core_impl(descriptor, idMap, disabledRequired,
                                                                       context.disabledPlugins, pluginErrorsById)) {
        return false;
      }

      descriptor.setEnabled(false);
      disabledAfterInit.add(descriptor);
      return true;
    });

    List<Supplier<HtmlChunk>> actions = prepareActions(disabledAfterInit, disabledRequired);
    pluginLoadingErrors = pluginErrorsById;

    List<Supplier<HtmlChunk>> errorsList = preparePluginsError(globalErrors);
    if (!errorsList.isEmpty()) {
      synchronized (pluginErrors) {
        pluginErrors.addAll(errorsList);
        pluginErrors.addAll(actions);
      }
    }

    if (checkEssentialPlugins) {
      checkEssentialPluginsAreAvailable(idMap);
    }

    PluginSet pluginSet = pluginSetBuilder.createPluginSet(context.result.incompletePlugins.values());
    new ClassLoaderConfigurator(pluginSet, coreLoader).configure();
    return new PluginManagerState(pluginSet, disabledRequired, disabledAfterInit);
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
  private static synchronized @NotNull PluginSet loadAndInitializePlugins(@NotNull DescriptorListLoadingContext context,
                                                                          @NotNull ClassLoader coreLoader) {
    if (IdeaPluginDescriptorImpl.disableNonBundledPlugins) {
      getLogger().info("Running with disableThirdPartyPlugins argument, third-party plugins will be disabled");
    }

    Activity activity = StartUpMeasurer.startActivity("plugin initialization", ActivityCategory.DEFAULT);
    PluginManagerState initResult = initializePlugins(context, coreLoader, !isUnitTestMode);
    PluginLoadingResult result = context.result;

    ourPluginsToDisable = initResult.effectiveDisabledIds;
    ourPluginsToEnable = initResult.disabledRequiredIds;

    ourShadowedBundledPlugins = result.shadowedBundledIds;

    activity.end();
    activity.setDescription("plugin count: " + initResult.pluginSet.enabledPlugins.size());
    logPlugins(initResult.pluginSet.allPlugins, result.incompletePlugins.values());
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

    for (IdeaPluginDescriptorImpl plugin : pluginSet.allPlugins) {
      if (id.equals(plugin.getPluginId())) {
        return plugin;
      }
    }
    return null;
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

    if (pluginSet.isPluginEnabled(id)) {
      return true;
    }

    for (IdeaPluginDescriptor plugin : pluginSet.allPlugins) {
      if (id.equals(plugin.getPluginId())) {
        return true;
      }
    }
    return false;
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

  @SuppressWarnings("UnusedReturnValue")
  @ApiStatus.Internal
  public static boolean processAllNonOptionalDependencies(@NotNull IdeaPluginDescriptorImpl rootDescriptor,
                                                          @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idToMap,
                                                          @NotNull Function<@NotNull IdeaPluginDescriptorImpl, FileVisitResult> consumer) {
    return processAllNonOptionalDependencies(rootDescriptor,
                                             idToMap,
                                             (__, descriptor) -> descriptor != null
                                                                 ? consumer.apply(descriptor)
                                                                 : FileVisitResult.SKIP_SUBTREE);
  }

  @ApiStatus.Internal
  public static boolean processAllNonOptionalDependencies(@NotNull IdeaPluginDescriptorImpl rootDescriptor,
                                                          @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idToMap,
                                                          @NotNull BiFunction<@NotNull PluginId, @Nullable IdeaPluginDescriptorImpl, FileVisitResult> consumer) {
    return processAllNonOptionalDependencies(rootDescriptor,
                                             idToMap,
                                             new HashSet<>(),
                                             consumer);
  }

  /**
   * {@link FileVisitResult#SKIP_SIBLINGS} is not supported.
   * <p>
   * Returns {@code false} if processing was terminated because of {@link FileVisitResult#TERMINATE}, and {@code true} otherwise.
   */
  @ApiStatus.Internal
  private static boolean processAllNonOptionalDependencies(@NotNull IdeaPluginDescriptorImpl rootDescriptor,
                                                           @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idToMap,
                                                           @NotNull Set<PluginId> depProcessed,
                                                           @NotNull BiFunction<@NotNull PluginId, @Nullable IdeaPluginDescriptorImpl, FileVisitResult> consumer) {
    for (PluginId dependencyId : getNonOptionalDependenciesIds(rootDescriptor)) {
      IdeaPluginDescriptorImpl descriptor = idToMap.get(dependencyId);
      PluginId pluginId = descriptor != null ? descriptor.getPluginId() : dependencyId;
      switch (consumer.apply(pluginId, descriptor)) {
        case TERMINATE:
          return false;
        case CONTINUE:
          if (descriptor != null && depProcessed.add(descriptor.getPluginId())) {
            processAllNonOptionalDependencies(descriptor, idToMap, depProcessed, consumer);
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

  private static @NotNull List<PluginId> getNonOptionalDependenciesIds(@NotNull IdeaPluginDescriptorImpl descriptor) {
    List<PluginId> dependencies = new ArrayList<>();
    for (PluginDependency dependency : descriptor.pluginDependencies) {
      if (dependency.isOptional()) {
        continue;
      }

      dependencies.add(dependency.getPluginId());
    }
    for (ModuleDependenciesDescriptor.PluginReference plugin : descriptor.dependencies.plugins) {
      dependencies.add(plugin.id);
    }
    return dependencies;
  }

  public static synchronized boolean isUpdatedBundledPlugin(@NotNull PluginDescriptor plugin) {
    return ourShadowedBundledPlugins != null && ourShadowedBundledPlugins.contains(plugin.getPluginId());
  }

  //<editor-fold desc="Deprecated stuff.">
  /** @deprecated Use {@link #isDisabled(PluginId)} */
  @Deprecated
  public static boolean isDisabled(@NotNull String pluginId) {
    return isDisabled(PluginId.getId(pluginId));
  }

  /** @deprecated Use {@link #disablePlugin(PluginId)} */
  @Deprecated
  public static boolean disablePlugin(@NotNull String id) {
    return disablePlugin(PluginId.getId(id));
  }

  /** @deprecated Use {@link #enablePlugin(PluginId)} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public static boolean enablePlugin(@NotNull String id) {
    return enablePlugin(PluginId.getId(id));
  }

  /** @deprecated Use {@link DisabledPluginsState#addDisablePluginListener} directly
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
  public static void addDisablePluginListener(@NotNull Runnable listener) {
    DisabledPluginsState.addDisablePluginListener(listener);
  }
  //</editor-fold>
}
