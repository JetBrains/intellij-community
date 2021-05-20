// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.core.CoreBundle;
import com.intellij.diagnostic.*;
import com.intellij.ide.plugins.cl.PluginAwareClassLoader;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.PlatformUtils;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.graph.InboundSemiGraph;
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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ForkJoinPool;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Prefer to use only JDK classes. Any post start-up functionality should be placed in PluginManager class.
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
  private static volatile IdeaPluginDescriptorImpl[] ourPlugins;
  private static volatile List<IdeaPluginDescriptorImpl> ourLoadedPlugins;
  private static Map<PluginId, PluginLoadingError> ourPluginLoadingErrors;

  private static Map<String, String[]> ourAdditionalLayoutMap = Collections.emptyMap();

  @SuppressWarnings("StaticNonFinalField")
  public static volatile boolean isUnitTestMode = Boolean.getBoolean("idea.is.unit.test");
  @ApiStatus.Internal
  static final boolean usePluginClassLoader = Boolean.getBoolean("idea.from.sources.plugins.class.loader");

  @ApiStatus.Internal
  private static final List<Supplier<? extends HtmlChunk>> ourPluginErrors = new ArrayList<>();

  private static Set<PluginId> ourPluginsToDisable;
  private static Set<PluginId> ourPluginsToEnable;

  @SuppressWarnings("StaticNonFinalField")
  @ApiStatus.Internal
  public static boolean ourDisableNonBundledPlugins;

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
  public static @NotNull IdeaPluginDescriptor @NotNull[] getPlugins() {
    IdeaPluginDescriptor[] result = ourPlugins;
    if (result == null) {
      loadAndInitializePlugins(null, null);
      return ourPlugins;
    }
    return result;
  }

  static @NotNull Collection<IdeaPluginDescriptorImpl> getAllPlugins() {
    return Arrays.asList(ourPlugins);
  }

  /**
   * Returns descriptors of plugins which are successfully loaded into IDE. The result is sorted in a way that if each plugin comes after
   * the plugins it depends on.
   */
  public static @NotNull List<? extends IdeaPluginDescriptor> getLoadedPlugins() {
    return getLoadedPlugins(null);
  }

  @ApiStatus.Internal
  public static @NotNull List<IdeaPluginDescriptorImpl> getLoadedPlugins(@Nullable ClassLoader coreClassLoader) {
    List<IdeaPluginDescriptorImpl> result = ourLoadedPlugins;
    if (result == null) {
      loadAndInitializePlugins(null, coreClassLoader);
      return ourLoadedPlugins;
    }
    return result;
  }

  @ApiStatus.Internal
  public static @NotNull List<HtmlChunk> getAndClearPluginLoadingErrors() {
    synchronized (ourPluginErrors) {
      List<HtmlChunk> errors = ContainerUtil.map(ourPluginErrors, Supplier::get);
      ourPluginErrors.clear();
      return errors;
    }
  }

  private static void registerPluginErrors(List<? extends Supplier<? extends HtmlChunk>> errors) {
    synchronized (ourPluginErrors) {
      ourPluginErrors.addAll(errors);
    }
  }

  @ApiStatus.Internal
  public static boolean arePluginsInitialized() {
    return ourPlugins != null;
  }

  static synchronized void doSetPlugins(@NotNull IdeaPluginDescriptorImpl @Nullable [] value) {
    ourPlugins = value;
    ourLoadedPlugins = value == null ? null : Collections.unmodifiableList(getOnlyEnabledPlugins(value));
  }

  public static boolean isDisabled(@NotNull PluginId pluginId) {
    return DisabledPluginsState.isDisabled(pluginId);
  }

  public static boolean isBrokenPlugin(@NotNull IdeaPluginDescriptor descriptor) {
    PluginId pluginId = descriptor.getPluginId();
    if (pluginId == null) {
      return true;
    }

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
    List<IdeaPluginDescriptorImpl> loadedPlugins = ourLoadedPlugins;
    if (loadedPlugins == null ||
        className.startsWith("java.") ||
        className.startsWith("javax.") ||
        className.startsWith("kotlin.") ||
        className.startsWith("groovy.") ||
        !className.contains(".")) {
      return null;
    }

    IdeaPluginDescriptor result = null;
    for (IdeaPluginDescriptorImpl o : loadedPlugins) {
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

    // return if the found plugin is not "core" or the package is obviously "core"
    if (!CORE_ID.equals(result.getPluginId()) ||
        className.startsWith("com.jetbrains.") || className.startsWith("org.jetbrains.") ||
        className.startsWith("com.intellij.") || className.startsWith("org.intellij.") ||
        className.startsWith("com.android.") ||
        className.startsWith("git4idea.") || className.startsWith("org.angularjs.")) {
      return result;
    }

    // otherwise we need to check plugins with use-idea-classloader="true"
    String root = null;
    for (IdeaPluginDescriptorImpl o : loadedPlugins) {
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
    return isDevelopedByJetBrains(plugin.getVendor()) || isDevelopedByJetBrains(plugin.getOrganization());
  }

  public static boolean isDevelopedByJetBrains(@Nullable String vendorString) {
    if (vendorString == null) {
      return false;
    }

    if (vendorString.equals(VENDOR_JETBRAINS) || vendorString.equals(VENDOR_JETBRAINS_SRO)) {
      return true;
    }

    for (String vendor : StringUtil.split(vendorString, ",")) {
      String vendorItem = vendor.trim();
      if (VENDOR_JETBRAINS.equals(vendorItem) || VENDOR_JETBRAINS_SRO.equals(vendorItem)) {
        return true;
      }
    }
    return false;
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

  /**
   * In 191.* and earlier builds Java plugin was part of the platform, so any plugin installed in IntelliJ IDEA might be able to use its
   * classes without declaring explicit dependency on the Java module. This method is intended to add implicit dependency on the Java plugin
   * for such plugins to avoid breaking compatibility with them.
   */
  static @Nullable IdeaPluginDescriptorImpl getImplicitDependency(@NotNull IdeaPluginDescriptorImpl descriptor,
                                                                  @NotNull Supplier<IdeaPluginDescriptorImpl> javaDepGetter) {
    // skip our plugins as expected to be up-to-date whether bundled or not
    if (descriptor.isBundled() ||
        VENDOR_JETBRAINS.equals(descriptor.getVendor())) {
      return null;
    }

    PluginId pluginId = descriptor.getPluginId();
    if (CORE_ID.equals(pluginId) ||
        JAVA_PLUGIN_ID.equals(pluginId)) {
      return null;
    }

    IdeaPluginDescriptorImpl javaDep = javaDepGetter.get();
    if (javaDep == null) {
      return null;
    }

    // If a plugin does not include any module dependency tags in its plugin.xml, it's assumed to be a legacy plugin
    // and is loaded only in IntelliJ IDEA, so it may use classes from Java plugin.
    return hasModuleDependencies(descriptor) ? null : javaDep;
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
    doSetPlugins(null);
    DisabledPluginsState.invalidate();
    ourShadowedBundledPlugins = null;
  }

  private static void logPlugins(@NotNull IdeaPluginDescriptorImpl @NotNull [] plugins,
                                 Collection<IdeaPluginDescriptorImpl> incompletePlugins) {
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

  private static void prepareLoadingPluginsErrorMessage(@NotNull Map<PluginId, PluginLoadingError> pluginErrors,
                                                        @NotNull List<Supplier<@NlsContexts.DetailedDescription String>> globalErrors,
                                                        @NotNull List<Supplier<HtmlChunk>> actions) {
    ourPluginLoadingErrors = pluginErrors;

    if (pluginErrors.isEmpty() && globalErrors.isEmpty()) {
      return;
    }

    // log includes all messages, not only those which need to be reported to the user
    String logMessage = "Problems found loading plugins:\n  " +
                        Stream.concat(globalErrors.stream().map(Supplier::get),
                                      pluginErrors.entrySet().stream()
                                        .sorted(Map.Entry.comparingByKey())
                                        .map(e -> e.getValue().getInternalMessage()))
                          .collect(Collectors.joining("\n  "));

    if (isUnitTestMode || !GraphicsEnvironment.isHeadless()) {
      List<Supplier<HtmlChunk>> errorsList = Stream.<Supplier<HtmlChunk>>concat(
        globalErrors.stream().map(message -> () -> HtmlChunk.text(message.get())),
        pluginErrors.entrySet().stream()
          .sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue)
          .filter(PluginLoadingError::isNotifyUser)
          .map(error -> () -> HtmlChunk.text(error.getDetailedMessage()))
      ).collect(Collectors.toList());

      if (!errorsList.isEmpty()) {
        registerPluginErrors(ContainerUtil.concat(errorsList, actions));
      }

      getLogger().warn(logMessage);
    }
    else {
      getLogger().error(logMessage);
    }
  }

  public static @Nullable @NlsContexts.Label String getShortLoadingErrorMessage(@NotNull IdeaPluginDescriptor pluginDescriptor) {
    PluginLoadingError error = ourPluginLoadingErrors.get(pluginDescriptor.getPluginId());
    return error == null ? null : error.getShortMessage();
  }

  public static @Nullable PluginId getFirstDisabledDependency(@NotNull IdeaPluginDescriptor pluginDescriptor) {
    PluginLoadingError error = ourPluginLoadingErrors.get(pluginDescriptor.getPluginId());
    return error == null ? null : error.disabledDependency;
  }

  static @NotNull CachingSemiGraph<IdeaPluginDescriptorImpl> createPluginIdGraph(@NotNull Collection<IdeaPluginDescriptorImpl> descriptors,
                                                                                 @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idToDescriptorMap,
                                                                                 boolean withOptional) {
    boolean hasAllModules = idToDescriptorMap.containsKey(ALL_MODULES_MARKER);
    Supplier<IdeaPluginDescriptorImpl> javaDep = () -> idToDescriptorMap.get(JAVA_MODULE_ID);
    Set<IdeaPluginDescriptorImpl> uniqueCheck = new HashSet<>();
    Map<IdeaPluginDescriptorImpl, List<IdeaPluginDescriptorImpl>> in = new HashMap<>(descriptors.size());
    for (IdeaPluginDescriptorImpl descriptor : descriptors) {
      List<IdeaPluginDescriptorImpl> list = getDirectDependencies(descriptor, idToDescriptorMap, withOptional, hasAllModules, javaDep, uniqueCheck);
      if (!list.isEmpty()) {
        in.put(descriptor, list);
      }
    }
    return new CachingSemiGraph<>(descriptors, in);
  }

  private static @NotNull List<IdeaPluginDescriptorImpl> getDirectDependencies(@NotNull IdeaPluginDescriptorImpl rootDescriptor,
                                                                               @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idToDescriptorMap,
                                                                               boolean withOptional,
                                                                               boolean hasAllModules,
                                                                               @NotNull Supplier<IdeaPluginDescriptorImpl> javaDep,
                                                                               @NotNull Set<IdeaPluginDescriptorImpl> uniqueCheck) {
    List<PluginDependency> dependencies = rootDescriptor.pluginDependencies;
    IdeaPluginDescriptorImpl implicitDep = hasAllModules ? getImplicitDependency(rootDescriptor, javaDep) : null;
    int capacity = dependencies.size() + rootDescriptor.incompatibilities.size();
    if (!withOptional) {
      for (PluginDependency dependency : dependencies) {
        if (dependency.isOptional()) {
          capacity--;
        }
      }
    }
    if (capacity == 0) {
      return implicitDep == null ? Collections.emptyList() : Collections.singletonList(implicitDep);
    }

    uniqueCheck.clear();

    List<IdeaPluginDescriptorImpl> plugins = new ArrayList<>(capacity + (implicitDep == null ? 0 : 1));
    if (implicitDep != null) {
      if (rootDescriptor == implicitDep) {
        getLogger().error("Plugin " + rootDescriptor + " depends on self");
      }
      else {
        uniqueCheck.add(implicitDep);
        plugins.add(implicitDep);
      }
    }

    for (PluginDependency dependency : dependencies) {
      if (!withOptional && dependency.isOptional()) {
        continue;
      }

      // check for missing optional dependency
      IdeaPluginDescriptorImpl dep = idToDescriptorMap.get(dependency.getPluginId());
      // if 'dep' refers to a module we need to check the real plugin containing this module only if it's still enabled,
      // otherwise the graph will be inconsistent
      if (dep == null) {
        continue;
      }

      // ultimate plugin it is combined plugin, where some included XML can define dependency on ultimate explicitly and for now not clear,
      // can be such requirements removed or not
      if (rootDescriptor == dep) {
        if (!CORE_ID.equals(rootDescriptor.getPluginId())) {
          getLogger().error("Plugin " + rootDescriptor + " depends on self");
        }
      }
      else if (uniqueCheck.add(dep)) {
        plugins.add(dep);
      }
    }

    for (PluginId moduleId : rootDescriptor.incompatibilities) {
      IdeaPluginDescriptorImpl dep = idToDescriptorMap.get(moduleId);
      if (dep != null && uniqueCheck.add(dep)) {
        plugins.add(dep);
      }
    }

    return plugins;
  }

  private static void checkPluginCycles(@NotNull List<IdeaPluginDescriptorImpl> descriptors,
                                        @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idToDescriptorMap,
                                        @NotNull List<Supplier<@Nls String>> errors) {
    CachingSemiGraph<IdeaPluginDescriptorImpl> graph = createPluginIdGraph(descriptors, idToDescriptorMap, true);
    DFSTBuilder<IdeaPluginDescriptorImpl> builder = new DFSTBuilder<>(GraphGenerator.generate(graph));
    if (builder.isAcyclic()) {
      return;
    }

    for (Collection<IdeaPluginDescriptorImpl> component : builder.getComponents()) {
      if (component.size() < 2) {
        continue;
      }
      for (IdeaPluginDescriptor descriptor : component) {
        descriptor.setEnabled(false);
      }
      String pluginsString = component.stream().map(it -> "'" + it.getName() + "'").collect(Collectors.joining(", "));
      errors.add(message("plugin.loading.error.plugins.cannot.be.loaded.because.they.form.a.dependency.cycle", pluginsString));

      StringBuilder detailedMessage = new StringBuilder();
      Function<IdeaPluginDescriptorImpl, String> pluginToString = plugin -> "id = " + plugin.getPluginId().getIdString() + " (" + plugin.getName() + ")";

      detailedMessage.append("Detected plugin dependencies cycle details (only related dependencies are included):\n");
      component.stream()
        .map(p -> Pair.create(p, pluginToString.apply(p)))
        .sorted(Comparator.comparing(p -> p.second, String.CASE_INSENSITIVE_ORDER))
        .forEach(p -> {
          detailedMessage.append("  ").append(p.getSecond()).append(" depends on:\n");

          ContainerUtil.toCollection(() -> graph.getIn(p.first))
            .stream()
            .filter(dep -> component.contains(dep))
            .map(pluginToString)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .forEach(dep -> detailedMessage.append("    ").append(dep).append("\n"));
        });

      getLogger().info(detailedMessage.toString());
    }
  }

  private static void prepareLoadingPluginsErrorMessage(@NotNull Map<PluginId, String> disabledIds,
                                                        @NotNull Set<PluginId> disabledRequiredIds,
                                                        @NotNull Map<PluginId, ? extends IdeaPluginDescriptor> idMap,
                                                        @NotNull Map<PluginId, PluginLoadingError> pluginErrors,
                                                        @NotNull List<Supplier<String>> globalErrors) {
    List<Supplier<HtmlChunk>> actions = new ArrayList<>();
    if (!disabledIds.isEmpty()) {
      @NlsSafe String nameToDisable;
      if (disabledIds.size() == 1) {
        PluginId id = disabledIds.keySet().iterator().next();
        nameToDisable = idMap.containsKey(id) ? idMap.get(id).getName() : id.getIdString();
      }
      else {
        nameToDisable = null;
      }
      actions.add(() -> HtmlChunk.link(DISABLE, CoreBundle.message("link.text.disable.plugin.or.plugins", nameToDisable, nameToDisable != null ? 0 : 1)));
      if (!disabledRequiredIds.isEmpty()) {
        String nameToEnable = disabledRequiredIds.size() == 1 && idMap.containsKey(disabledRequiredIds.iterator().next())
                              ? idMap.get(disabledRequiredIds.iterator().next()).getName()
                              : null;
        actions.add(() -> HtmlChunk
          .link(ENABLE, CoreBundle.message("link.text.enable.plugin.or.plugins", nameToEnable, nameToEnable != null ? 0 : 1)));
      }
      actions.add(() -> HtmlChunk.link(EDIT, CoreBundle.message("link.text.open.plugin.manager")));
    }
    prepareLoadingPluginsErrorMessage(pluginErrors, globalErrors, actions);
  }

  @ApiStatus.Internal
  static synchronized boolean onEnable(boolean enabled) {
    Set<PluginId> pluginIds = enabled ? ourPluginsToEnable : ourPluginsToDisable;
    ourPluginsToEnable = null;
    ourPluginsToDisable = null;

    boolean applied = pluginIds != null;
    if (applied) {
      Map<PluginId, IdeaPluginDescriptorImpl> pluginIdMap = buildPluginIdMap();
      for (PluginId pluginId : pluginIds) {
        IdeaPluginDescriptor descriptor = pluginIdMap.get(pluginId);
        if (descriptor != null) {
          descriptor.setEnabled(enabled);
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
  public static @NotNull CompletionStage<List<IdeaPluginDescriptorImpl>> initPlugins(@NotNull ClassLoader coreClassLoader) {
    CompletableFuture<DescriptorListLoadingContext> future = descriptorListFuture;
    if (future == null) {
      future = CompletableFuture.completedFuture(null);
    }
    return future.thenApply(context -> {
      loadAndInitializePlugins(context, coreClassLoader);
      return ourLoadedPlugins;
    });
  }

  private static @NotNull Map<String, String[]> loadAdditionalLayoutMap() {
    Path fileWithLayout = usePluginClassLoader
                          ? Paths.get(PathManager.getSystemPath(), PlatformUtils.getPlatformPrefix() + ".txt")
                          : null;
    if (fileWithLayout == null || !Files.exists(fileWithLayout)) {
      return Collections.emptyMap();
    }

    Map<String, String[]> additionalLayoutMap = new LinkedHashMap<>();
    try (BufferedReader bufferedReader = Files.newBufferedReader(fileWithLayout)) {
      String line;
      while ((line = bufferedReader.readLine()) != null) {
        List<String> parameters = ParametersListUtil.parse(line.trim());
        if (parameters.size() < 2) {
          continue;
        }
        additionalLayoutMap.put(parameters.get(0), ArrayUtilRt.toStringArray(parameters.subList(1, parameters.size())));
      }
    }
    catch (Exception ignored) {
    }
    return additionalLayoutMap;
  }

  /**
   * not used by plugin manager - only for dynamic plugin reloading.
   * Building plugin graph and using `getInList` as it is done for regular loading is not required - all that magic and checks
   * are not required here because only regular plugins maybe dynamically reloaded.
   */
  @ApiStatus.Internal
  public static @NotNull ClassLoaderConfigurator createClassLoaderConfiguratorForDynamicPlugin(@NotNull IdeaPluginDescriptorImpl pluginDescriptor) {
    Map<PluginId, IdeaPluginDescriptorImpl> idMap = buildPluginIdMap(ContainerUtil.concat(getLoadedPlugins(null), Collections.singletonList(pluginDescriptor)));
    return new ClassLoaderConfigurator(true, PluginManagerCore.class.getClassLoader(), idMap, ourAdditionalLayoutMap);
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
    boolean isNonBundledPluginDisabled = ourDisableNonBundledPlugins;
    if (isNonBundledPluginDisabled) {
      getLogger().info("Running with disableThirdPartyPlugins argument, third-party plugins will be disabled");
    }
    String selectedIds = System.getProperty("idea.load.plugins.id");
    String selectedCategory = System.getProperty("idea.load.plugins.category");

    IdeaPluginDescriptorImpl coreDescriptor = idMap.get(CORE_ID);
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
      Set<IdeaPluginDescriptorImpl> finalExplicitlyEnabled = explicitlyEnabled;
      Set<IdeaPluginDescriptor> depProcessed = new HashSet<>();
      for (IdeaPluginDescriptorImpl descriptor : new ArrayList<>(explicitlyEnabled)) {
        processAllDependencies(descriptor, false, idMap, depProcessed, (id, dependency) -> {
          finalExplicitlyEnabled.add(dependency);
          return FileVisitResult.CONTINUE;
        });
      }
    }

    Map<PluginId, Set<String>> brokenPluginVersions = getBrokenPluginVersions();
    boolean shouldLoadPlugins = Boolean.parseBoolean(System.getProperty("idea.load.plugins", "true"));
    for (IdeaPluginDescriptorImpl descriptor : descriptors) {
      if (descriptor == coreDescriptor) {
        continue;
      }

      Set<String> set = brokenPluginVersions.get(descriptor.getPluginId());
      if (set != null && set.contains(descriptor.getVersion())) {
        descriptor.setEnabled(false);
        errors.put(descriptor.getPluginId(), new PluginLoadingError(descriptor,
                                  message("plugin.loading.error.long.marked.as.broken", descriptor.getName(), descriptor.getVersion()),
                                  message("plugin.loading.error.short.marked.as.broken")));
      }
      else if (explicitlyEnabled != null) {
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
      else if (isNonBundledPluginDisabled && !descriptor.isBundled()) {
        descriptor.setEnabled(false);
        errors.put(descriptor.getPluginId(), new PluginLoadingError(descriptor,
                                                                    message("plugin.loading.error.long.custom.plugin.loading.disabled",
                                                                            descriptor.getName()),
                                                                    message("plugin.loading.error.short.custom.plugin.loading.disabled"),
                                                                    false,
                                                                    null));
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
      BuildNumber sinceBuildNumber = sinceBuild == null ? null : BuildNumber.fromString(sinceBuild, null, null);
      if (sinceBuildNumber != null && sinceBuildNumber.compareTo(ideBuildNumber) > 0) {
        return new PluginLoadingError(descriptor, message("plugin.loading.error.long.incompatible.since.build", descriptor.getName(), descriptor.getVersion(), sinceBuild, ideBuildNumber),
                                         message("plugin.loading.error.short.incompatible.since.build", sinceBuild));
      }

      BuildNumber untilBuildNumber = untilBuild == null ? null : BuildNumber.fromString(untilBuild, null, null);
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

  static @NotNull PluginManagerState initializePlugins(@NotNull DescriptorListLoadingContext context, @NotNull ClassLoader coreLoader, boolean checkEssentialPlugins) {
    PluginLoadingResult loadingResult = context.result;
    Map<PluginId, PluginLoadingError> pluginErrors = new HashMap<>(loadingResult.getPluginErrors$intellij_platform_core_impl());
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
    IdeaPluginDescriptorImpl coreDescriptor = idMap.get(CORE_ID);
    if (checkEssentialPlugins && coreDescriptor == null) {
      throw new EssentialPluginMissingException(Collections.singletonList(CORE_ID + " (platform prefix: " + System.getProperty(PlatformUtils.PLATFORM_PREFIX_KEY) + ")"));
    }

    List<IdeaPluginDescriptorImpl> descriptors = loadingResult.getEnabledPlugins();
    disableIncompatiblePlugins(descriptors, idMap, pluginErrors);
    checkPluginCycles(descriptors, idMap, globalErrors);

    // topological sort based on required dependencies only
    IdeaPluginDescriptorImpl[] sortedRequired = getTopologicallySorted(createPluginIdGraph(descriptors, idMap, false));

    Set<PluginId> enabledPluginIds = new LinkedHashSet<>();
    Set<PluginId> enabledModuleIds = new LinkedHashSet<>();
    Map<PluginId, String> disabledIds = new LinkedHashMap<>();
    Set<PluginId> disabledRequiredIds = new LinkedHashSet<>();

    for (IdeaPluginDescriptorImpl descriptor : sortedRequired) {
      boolean wasEnabled = descriptor.isEnabled();
      if (wasEnabled && computePluginEnabled(descriptor, enabledPluginIds, enabledModuleIds, idMap, disabledRequiredIds, context.disabledPlugins, pluginErrors)) {
        enabledPluginIds.add(descriptor.getPluginId());
        enabledModuleIds.addAll(descriptor.modules);
      }
      else {
        descriptor.setEnabled(false);
        if (wasEnabled) {
          disabledIds.put(descriptor.getPluginId(), descriptor.getName());
        }
      }
    }

    prepareLoadingPluginsErrorMessage(disabledIds, disabledRequiredIds, idMap, pluginErrors, globalErrors);

    // topological sort based on all (required and optional) dependencies
    CachingSemiGraph<IdeaPluginDescriptorImpl> graph = createPluginIdGraph(Arrays.asList(sortedRequired), idMap, true);
    IdeaPluginDescriptorImpl[] sortedAll = getTopologicallySorted(graph);

    List<IdeaPluginDescriptorImpl> enabledPlugins = getOnlyEnabledPlugins(sortedAll);

    for (IdeaPluginDescriptorImpl plugin : enabledPlugins) {
      checkOptionalDescriptors(plugin.pluginDependencies, idMap);
    }
    Map<String, String[]> additionalLayoutMap = loadAdditionalLayoutMap();
    ourAdditionalLayoutMap = additionalLayoutMap;
    ClassLoaderConfigurator classLoaderConfigurator = new ClassLoaderConfigurator(context.usePluginClassLoader, coreLoader, idMap,
                                                                                  additionalLayoutMap);
    enabledPlugins.forEach(classLoaderConfigurator::configure);

    if (checkEssentialPlugins) {
      checkEssentialPluginsAreAvailable(idMap);
    }

    Set<PluginId> effectiveDisabledIds = disabledIds.isEmpty() ? Collections.emptySet() : new HashSet<>(disabledIds.keySet());
    return new PluginManagerState(sortedAll, enabledPlugins, disabledRequiredIds, effectiveDisabledIds, idMap);
  }

  private static void checkOptionalDescriptors(@NotNull List<PluginDependency> pluginDependencies,
                                               @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idMap) {
    for (PluginDependency dependency : pluginDependencies) {
      IdeaPluginDescriptorImpl subDescriptor = dependency.subDescriptor;
      if (subDescriptor == null || dependency.isDisabledOrBroken) {
        continue;
      }

      IdeaPluginDescriptorImpl dependencyDescriptor = idMap.get(dependency.getPluginId());
      if (dependencyDescriptor == null || !dependencyDescriptor.isEnabled()) {
        dependency.isDisabledOrBroken = true;
        continue;
      }

      // check that plugin doesn't depend on unavailable plugin
      List<PluginDependency> childDependencies = subDescriptor.pluginDependencies;
      if (!checkChildDeps(childDependencies, idMap)) {
        dependency.isDisabledOrBroken = true;
      }
    }
  }

  // multiple dependency condition is not supported, so,
  // jsp-javaee.xml depends on com.intellij.javaee.web, and included file in turn define jsp-css.xml that depends on com.intellij.css
  // that's why nesting level is more than one
  private static boolean checkChildDeps(@NotNull List<PluginDependency> childDependencies, @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idMap) {
    for (PluginDependency dependency : childDependencies) {
      if (dependency.isDisabledOrBroken) {
        if (dependency.isOptional()) {
          continue;
        }
        return false;
      }

      IdeaPluginDescriptorImpl dependentDescriptor = idMap.get(dependency.getPluginId());
      if (dependentDescriptor == null || !dependentDescriptor.isEnabled()) {
        dependency.isDisabledOrBroken = true;
        if (dependency.isOptional()) {
          continue;
        }
        return false;
      }

      if (dependency.subDescriptor != null) {
        List<PluginDependency> list = dependency.subDescriptor.pluginDependencies;
        if (!checkChildDeps(list, idMap)) {
          dependency.isDisabledOrBroken = true;
          if (dependency.isOptional()) {
            continue;
          }
          return false;
        }
      }
    }
    return true;
  }

  static @NotNull IdeaPluginDescriptorImpl @NotNull [] getTopologicallySorted(@NotNull InboundSemiGraph<IdeaPluginDescriptorImpl> graph) {
    DFSTBuilder<IdeaPluginDescriptorImpl> requiredOnlyGraph = new DFSTBuilder<>(GraphGenerator.generate(graph));
    IdeaPluginDescriptorImpl[] sortedRequired = graph.getNodes().toArray(new IdeaPluginDescriptorImpl[0]);
    Comparator<IdeaPluginDescriptorImpl> comparator = requiredOnlyGraph.comparator();
    // there is circular reference between core and implementation-detail plugin, as not all such plugins extracted from core,
    // so, ensure that core plugin is always first (otherwise not possible to register actions - parent group not defined)
    Arrays.sort(sortedRequired, (o1, o2) -> {
      return CORE_ID.equals(o1.getPluginId()) ? -1 : CORE_ID.equals(o2.getPluginId()) ? 1 : comparator.compare(o1, o2);
    });
    return sortedRequired;
  }

  @ApiStatus.Internal
  public static @NotNull Map<PluginId, IdeaPluginDescriptorImpl> buildPluginIdMap(@NotNull List<IdeaPluginDescriptorImpl> descriptors) {
    Map<PluginId, IdeaPluginDescriptorImpl> idMap = new HashMap<>(descriptors.size());
    Map<PluginId, List<IdeaPluginDescriptorImpl>> duplicateMap = null;
    for (IdeaPluginDescriptorImpl descriptor : descriptors) {
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

  private static boolean computePluginEnabled(@NotNull IdeaPluginDescriptorImpl descriptor,
                                              @NotNull Set<PluginId> loadedPluginIds,
                                              @NotNull Set<PluginId> loadedModuleIds,
                                              @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idMap,
                                              @NotNull Set<PluginId> disabledRequiredIds,
                                              @NotNull Set<PluginId> disabledPlugins,
                                              @NotNull Map<PluginId, PluginLoadingError> errors) {
    if (CORE_ID.equals(descriptor.getPluginId())) {
      return true;
    }
    boolean notifyUser = !descriptor.isImplementationDetail();

    boolean result = true;

    for (PluginId incompatibleId : descriptor.incompatibilities) {
      if (!loadedModuleIds.contains(incompatibleId) || disabledPlugins.contains(incompatibleId)) {
        continue;
      }

      result = false;
      String presentableName = incompatibleId.getIdString();
      errors.put(descriptor.getPluginId(), new PluginLoadingError(descriptor,
                                                                  message("plugin.loading.error.long.ide.contains.conflicting.module",
                                                                          descriptor.getName(), presentableName),
                                                                  message("plugin.loading.error.short.ide.contains.conflicting.module",
                                                                          presentableName),
                                                                  notifyUser,
                                                                  null));
    }

    for (PluginDependency dependency : descriptor.pluginDependencies) {
      PluginId depId = dependency.getPluginId();
      if (dependency.isOptional() || loadedPluginIds.contains(depId) || loadedModuleIds.contains(depId)) {
        continue;
      }

      result = false;
      IdeaPluginDescriptor dep = idMap.get(depId);
      if (dep != null && disabledPlugins.contains(depId)) {
        // broken/incompatible plugins can be updated, add them anyway
        disabledRequiredIds.add(dep.getPluginId());
      }

      String depName = dep == null ? null : dep.getName();
      if (depName == null) {
        @NlsSafe String depPresentableId = depId.getIdString();
        if (errors.containsKey(depId)) {
          errors.put(descriptor.getPluginId(), new PluginLoadingError(descriptor,
                                                                      message("plugin.loading.error.long.depends.on.failed.to.load.plugin",
                                                                              descriptor.getName(),
                                                                              depPresentableId),
                                                                      message("plugin.loading.error.short.depends.on.failed.to.load.plugin",
                                                                              depPresentableId), notifyUser,
                                                                      null));
        }
        else {
          errors.put(descriptor.getPluginId(), new PluginLoadingError(descriptor,
                                                                      message("plugin.loading.error.long.depends.on.not.installed.plugin",
                                                                              descriptor.getName(),
                                                                              depPresentableId),
                                                                      message("plugin.loading.error.short.depends.on.not.installed.plugin",
                                                                              depPresentableId),
                                                                      notifyUser,
                                                                      null));
        }
      }
      else {
        errors.put(descriptor.getPluginId(), new PluginLoadingError(descriptor,
                                                                    message("plugin.loading.error.long.depends.on.disabled.plugin",
                                                                            descriptor.getName(), depName),
                                                                    message("plugin.loading.error.short.depends.on.disabled.plugin",
                                                                            depName),
                                                                    notifyUser,
                                                                    dep.getPluginId()));
      }
    }
    return result;
  }

  private static @NotNull @Nls Supplier<String> message(@NotNull @PropertyKey(resourceBundle = CoreBundle.BUNDLE) String key, Object @NotNull ... params) {
    //noinspection Convert2Lambda
    return new Supplier<String>() {
      @Override
      public String get() {
        return CoreBundle.message(key, params);
      }
    };
  }

  @SuppressWarnings("NonPrivateFieldAccessedInSynchronizedContext")
  private static synchronized void loadAndInitializePlugins(@Nullable DescriptorListLoadingContext context, @Nullable ClassLoader coreLoader) {
    if (coreLoader == null) {
      Class<?> callerClass = ReflectionUtil.findCallerClass(1);
      assert callerClass != null;
      coreLoader = callerClass.getClassLoader();
    }

    try {
      if (context == null) {
        //noinspection resource
        context = PluginDescriptorLoader.loadDescriptors(isUnitTestMode, isRunningFromSources());
      }
      Activity activity = StartUpMeasurer.startActivity("plugin initialization", ActivityCategory.DEFAULT);
      PluginManagerState initResult = initializePlugins(context, coreLoader, !isUnitTestMode);

      ourPlugins = initResult.sortedPlugins;
      PluginLoadingResult result = context.result;
      if (!result.incompletePlugins.isEmpty()) {
        int oldSize = initResult.sortedPlugins.length;
        IdeaPluginDescriptorImpl[] all = Arrays.copyOf(initResult.sortedPlugins, oldSize + result.incompletePlugins.size());
        ArrayUtil.copy(result.incompletePlugins.values(), all, oldSize);
        ourPlugins = all;
      }

      ourPluginsToDisable = initResult.effectiveDisabledIds;
      ourPluginsToEnable = initResult.disabledRequiredIds;
      ourLoadedPlugins = initResult.sortedEnabledPlugins;
      ourShadowedBundledPlugins = result.shadowedBundledIds;

      activity.end();
      activity.setDescription("plugin count: " + ourLoadedPlugins.size());
      logPlugins(initResult.sortedPlugins, result.incompletePlugins.values());
    }
    catch (RuntimeException e) {
      getLogger().error(e);
      throw e;
    }
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

  public static @Nullable IdeaPluginDescriptor getPlugin(@Nullable PluginId id) {
    if (id != null) {
      for (IdeaPluginDescriptor plugin : getPlugins()) {
        if (id.equals(plugin.getPluginId())) {
          return plugin;
        }
      }
    }
    return null;
  }

  public static @Nullable IdeaPluginDescriptor findPluginByModuleDependency(@NotNull PluginId id) {
    for (IdeaPluginDescriptorImpl descriptor : ourPlugins) {
      if (descriptor.modules.contains(id)) {
        return descriptor;
      }
    }
    return null;
  }

  public static boolean isPluginInstalled(PluginId id) {
    return getPlugin(id) != null;
  }

  @ApiStatus.Internal
  public static @NotNull Map<PluginId, IdeaPluginDescriptorImpl> buildPluginIdMap() {
    LoadingState.COMPONENTS_REGISTERED.checkOccurred();
    return buildPluginIdMap(Arrays.asList(ourPlugins));
  }

  /**
   * You must not use this method in cycle, in this case use {@link #processAllDependencies(IdeaPluginDescriptorImpl, boolean, Map, Function)} instead
   * (to reuse result of {@link #buildPluginIdMap()}).
   *
   * {@link FileVisitResult#SKIP_SIBLINGS} is not supported.
   *
   * Returns {@code false} if processing was terminated because of {@link FileVisitResult#TERMINATE}, and {@code true} otherwise.
   */
  @SuppressWarnings("UnusedReturnValue")
  @ApiStatus.Internal
  public static boolean processAllDependencies(@NotNull IdeaPluginDescriptorImpl rootDescriptor,
                                               boolean withOptionalDeps,
                                               @NotNull Function<? super IdeaPluginDescriptor, FileVisitResult> consumer) {
    return processAllDependencies(rootDescriptor, withOptionalDeps, buildPluginIdMap(), consumer);
  }

  @ApiStatus.Internal
  public static boolean processAllDependencies(@NotNull IdeaPluginDescriptorImpl rootDescriptor,
                                               boolean withOptionalDeps,
                                               @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idToMap,
                                               @NotNull Function<? super IdeaPluginDescriptor, FileVisitResult> consumer) {
    return processAllDependencies(rootDescriptor, withOptionalDeps, idToMap, new HashSet<>(), (id, descriptor) -> descriptor != null ? consumer.apply(descriptor) : FileVisitResult.SKIP_SUBTREE);
  }

  @SuppressWarnings("UnusedReturnValue")
  @ApiStatus.Internal
  public static boolean processAllDependencies(@NotNull IdeaPluginDescriptorImpl rootDescriptor,
                                               boolean withOptionalDeps,
                                               @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idToMap,
                                               @NotNull BiFunction<@NotNull PluginId, @Nullable IdeaPluginDescriptorImpl, FileVisitResult> consumer) {
    return processAllDependencies(rootDescriptor, withOptionalDeps, idToMap, new HashSet<>(), consumer);
  }

  @ApiStatus.Internal
  private static boolean processAllDependencies(@NotNull IdeaPluginDescriptorImpl rootDescriptor,
                                                boolean withOptionalDeps,
                                                @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idToMap,
                                                @NotNull Set<IdeaPluginDescriptor> depProcessed,
                                                @NotNull BiFunction<@NotNull PluginId, @Nullable IdeaPluginDescriptorImpl, FileVisitResult> consumer) {
    for (PluginDependency dependency : rootDescriptor.pluginDependencies) {
      if (!withOptionalDeps && dependency.isOptional()) {
        continue;
      }

      IdeaPluginDescriptorImpl descriptor = idToMap.get(dependency.getPluginId());
      PluginId pluginId = descriptor == null ? dependency.getPluginId() : descriptor.getPluginId();
      switch (consumer.apply(pluginId, descriptor)) {
        case TERMINATE:
          return false;
        case CONTINUE:
          if (descriptor != null && depProcessed.add(descriptor)) {
            processAllDependencies(descriptor, withOptionalDeps, idToMap, depProcessed, consumer);
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

  private static @NotNull List<IdeaPluginDescriptorImpl> getOnlyEnabledPlugins(@NotNull IdeaPluginDescriptorImpl @NotNull[] sortedAll) {
    List<IdeaPluginDescriptorImpl> enabledPlugins = new ArrayList<>(sortedAll.length);
    for (IdeaPluginDescriptorImpl descriptor : sortedAll) {
      if (descriptor.isEnabled()) {
        enabledPlugins.add(descriptor);
       }
     }
     return enabledPlugins;
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
