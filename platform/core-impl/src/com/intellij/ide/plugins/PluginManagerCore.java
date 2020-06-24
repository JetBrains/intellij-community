// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.LoadingState;
import com.intellij.diagnostic.PluginException;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionInstantiationException;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.*;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.graph.*;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.Reference;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Prefer to use only JDK classes. Any post start-up functionality should be placed in PluginManager class.
public final class PluginManagerCore {
  public static final String META_INF = "META-INF/";
  public static final String IDEA_IS_INTERNAL_PROPERTY = "idea.is.internal";

  public static final PluginId CORE_ID = PluginId.getId("com.intellij");
  public static final String CORE_PLUGIN_ID = "com.intellij";

  public static final PluginId JAVA_PLUGIN_ID = PluginId.getId("com.intellij.java");
  private static final PluginId JAVA_MODULE_ID = PluginId.getId("com.intellij.modules.java");

  public static final String PLUGIN_XML = "plugin.xml";
  public static final String PLUGIN_XML_PATH = META_INF + PLUGIN_XML;
  private static final PluginId ALL_MODULES_MARKER = PluginId.getId("com.intellij.modules.all");

  public static final String VENDOR_JETBRAINS = "JetBrains";

  private static final String MODULE_DEPENDENCY_PREFIX = "com.intellij.module";

  private static final PluginId SPECIAL_IDEA_PLUGIN_ID = PluginId.getId("IDEA CORE");

  static final String PROPERTY_PLUGIN_PATH = "plugin.path";

  public static final String DISABLE = "disable";
  public static final String ENABLE = "enable";
  public static final String EDIT = "edit";

  private static Reference<Map<PluginId, Set<String>>> ourBrokenPluginVersions;
  private static volatile IdeaPluginDescriptorImpl[] ourPlugins;
  static volatile List<IdeaPluginDescriptorImpl> ourLoadedPlugins;
  private static List<PluginError> ourLoadingErrors;

  private static Map<String, String[]> ourAdditionalLayoutMap = Collections.emptyMap();

  @SuppressWarnings("StaticNonFinalField")
  public static volatile boolean isUnitTestMode = Boolean.getBoolean("idea.is.unit.test");
  @ApiStatus.Internal
  static final boolean usePluginClassLoader = Boolean.getBoolean("idea.from.sources.plugins.class.loader");

  @SuppressWarnings("StaticNonFinalField") @ApiStatus.Internal
  public static String ourPluginError;

  @SuppressWarnings("StaticNonFinalField")
  @ApiStatus.Internal
  public static Set<PluginId> ourPluginsToDisable;
  @SuppressWarnings("StaticNonFinalField")
  @ApiStatus.Internal
  public static Set<PluginId> ourPluginsToEnable;

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

  @ApiStatus.Internal
  public static @Nullable String getPluginsCompatibleBuild() {
    return System.getProperty("idea.plugins.compatible.build");
  }

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
  public static boolean arePluginsInitialized() {
    return ourPlugins != null;
  }

  @ApiStatus.Internal
  static synchronized void doSetPlugins(@NotNull IdeaPluginDescriptorImpl @NotNull [] value) {
    ourPlugins = value;
    //noinspection NonPrivateFieldAccessedInSynchronizedContext
    ourLoadedPlugins = Collections.unmodifiableList(getOnlyEnabledPlugins(value));
  }

  public static boolean isDisabled(@NotNull PluginId pluginId) {
    return DisabledPluginsState.getDisabledIds().contains(pluginId);
  }

  /**
   * @deprecated Use {@link #isDisabled(PluginId)}
   */
  @Deprecated
  public static boolean isDisabled(@NotNull String pluginId) {
    return DisabledPluginsState.getDisabledIds().contains(PluginId.getId(pluginId));
  }

  public static boolean isBrokenPlugin(@NotNull IdeaPluginDescriptor descriptor) {
    PluginId pluginId = descriptor.getPluginId();
    if (pluginId == null) {
      return true;
    }

    Set<String> set = getBrokenPluginVersions().get(pluginId);
    return set != null && set.contains(descriptor.getVersion());
  }

  private static @NotNull Map<PluginId, Set<String>> getBrokenPluginVersions() {
    Map<PluginId, Set<String>> result = SoftReference.dereference(ourBrokenPluginVersions);
    if (result != null) {
      return result;
    }

    if (System.getProperty("idea.ignore.disabled.plugins") != null) {
      result = Collections.emptyMap();
      ourBrokenPluginVersions = new java.lang.ref.SoftReference<>(result);
      return result;
    }

    result = new HashMap<>();
    try (InputStream resource = PluginManagerCore.class.getResourceAsStream("/brokenPlugins.txt");
         BufferedReader br = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
      String s;
      while ((s = br.readLine()) != null) {
        s = s.trim();
        if (s.startsWith("//")) {
          continue;
        }

        List<String> tokens = ParametersListUtil.parse(s);
        if (tokens.isEmpty()) {
          continue;
        }

        if (tokens.size() == 1) {
          throw new RuntimeException("brokenPlugins.txt is broken. The line contains plugin name, but does not contains version: " + s);
        }

        PluginId pluginId = PluginId.getId(tokens.get(0));
        List<String> versions = tokens.subList(1, tokens.size());
        result.computeIfAbsent(pluginId, k -> new HashSet<>()).addAll(versions);
      }
    }
    catch (IOException e) {
      throw new RuntimeException("Failed to read /brokenPlugins.txt", e);
    }

    ourBrokenPluginVersions = new java.lang.ref.SoftReference<>(result);
    return result;
  }

  public static void savePluginsList(@NotNull Collection<PluginId> ids, @NotNull Path file, boolean append) throws IOException {
    Files.createDirectories(file.getParent());
    try (BufferedWriter writer = (append ? Files.newBufferedWriter(file, StandardOpenOption.APPEND, StandardOpenOption.CREATE) : Files.newBufferedWriter(file))) {
      writePluginsList(ids, writer);
    }
  }

  public static void writePluginsList(@NotNull Collection<PluginId> ids, @NotNull Writer writer) throws IOException {
    List<PluginId> sortedIds = new ArrayList<>(ids);
    sortedIds.sort(null);
    String separator = LineSeparator.getSystemLineSeparator().getSeparatorString();
    for (PluginId id : sortedIds) {
      writer.write(id.getIdString());
      writer.write(separator);
    }
  }

  /**
   * @deprecated Use {@link #disablePlugin(PluginId)}
   */
  @Deprecated
  public static boolean disablePlugin(@NotNull String id) {
    return disablePlugin(PluginId.getId(id));
  }

  public static boolean disablePlugin(@NotNull PluginId id) {
    return DisabledPluginsState.disablePlugin(id);
  }

  public static boolean enablePlugin(@NotNull PluginId id) {
    return DisabledPluginsState.enablePlugin(id);
  }

  /**
   * @deprecated Use {@link #enablePlugin(PluginId)}
   */
  @Deprecated
  public static boolean enablePlugin(@NotNull String id) {
    return enablePlugin(PluginId.getId(id));
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
    PluginId pluginId = classLoader instanceof PluginClassLoader ? ((PluginClassLoader)classLoader).getPluginId()
                                                                 : getPluginByClassName(pluginClass.getName());
    return new PluginException(errorMessage, cause, pluginId);
  }

  public static @Nullable PluginId getPluginByClassName(@NotNull String className) {
    PluginId id = getPluginOrPlatformByClassName(className);
    return (id == null || CORE_ID == id) ? null : id;
  }

  public static @Nullable PluginId getPluginOrPlatformByClassName(@NotNull String className) {
    PluginDescriptor result = getPluginDescriptorOrPlatformByClassName(className);
    return result == null ? null : result.getPluginId();
  }

  @ApiStatus.Internal
  public static @Nullable PluginDescriptor getPluginDescriptorOrPlatformByClassName(@NotNull String className) {
    List<IdeaPluginDescriptorImpl> loadedPlugins = ourLoadedPlugins;
    if (loadedPlugins == null ||
        className.startsWith("java.") ||
        className.startsWith("javax.") ||
        className.startsWith("kotlin.") ||
        className.startsWith("groovy.")) {
      return null;
    }

    IdeaPluginDescriptor result = null;
    for (IdeaPluginDescriptorImpl o : loadedPlugins) {
      ClassLoader classLoader = o.getPluginClassLoader();
      if (classLoader == null || !hasLoadedClass(className, classLoader)) {
        continue;
      }

      result = o;
      break;
    }

    if (result == null) {
      return null;
    }

    // return if the found plugin is not "core" or the package is obviously "core"
    if (result.getPluginId() != CORE_ID ||
        className.startsWith("com.jetbrains.") || className.startsWith("org.jetbrains.") ||
        className.startsWith("com.intellij.") || className.startsWith("org.intellij.") ||
        className.startsWith("com.android.") ||
        className.startsWith("git4idea.") || className.startsWith("org.angularjs.")) {
      return result;
    }

    // otherwise we need to check plugins with use-idea-classloader="true"
    String root = PathManager.getResourceRoot(result.getPluginClassLoader(), "/" + className.replace('.', '/') + ".class");
    if (root == null) {
      return null;
    }

    for (IdeaPluginDescriptorImpl o : loadedPlugins) {
      if (!o.isUseIdeaClassLoader()) {
        continue;
      }

      Path path = o.getPluginPath();
      if (!root.startsWith(FileUtilRt.toSystemIndependentName(path.toString()))) {
        continue;
      }

      result = o;
      break;
    }
    return result;
  }

  private static boolean hasLoadedClass(@NotNull String className, @NotNull ClassLoader loader) {
    if (loader instanceof UrlClassLoader) {
      return ((UrlClassLoader)loader).hasLoadedClass(className);
    }

    // it can be an UrlClassLoader loaded by another class loader, so instanceof doesn't work
    Class<? extends ClassLoader> aClass = loader.getClass();
    if (isInstanceofUrlClassLoader(aClass)) {
      try {
        return (Boolean)aClass.getMethod("hasLoadedClass", String.class).invoke(loader, className);
      }
      catch (Exception ignored) {
      }
    }
    return false;
  }

  private static boolean isInstanceofUrlClassLoader(Class<?> aClass) {
    String urlClassLoaderName = UrlClassLoader.class.getName();
    while (aClass != null) {
      if (aClass.getName().equals(urlClassLoaderName)) return true;
      aClass = aClass.getSuperclass();
    }
    return false;
  }

  /**
   * In 191.* and earlier builds Java plugin was part of the platform, so any plugin installed in IntelliJ IDEA might be able to use its
   * classes without declaring explicit dependency on the Java module. This method is intended to add implicit dependency on the Java plugin
   * for such plugins to avoid breaking compatibility with them.
   */
  private static @Nullable IdeaPluginDescriptorImpl getImplicitDependency(@NotNull IdeaPluginDescriptorImpl descriptor,
                                                                @Nullable IdeaPluginDescriptorImpl javaDep,
                                                                boolean hasAllModules) {
    // skip our plugins as expected to be up-to-date whether bundled or not
    if (descriptor.getPluginId() == CORE_ID || descriptor.getPluginId() == JAVA_PLUGIN_ID ||
        VENDOR_JETBRAINS.equals(descriptor.getVendor()) ||
        !hasAllModules ||
        javaDep == null) {
      return null;
    }

    // If a plugin does not include any module dependency tags in its plugin.xml, it's assumed to be a legacy plugin
    // and is loaded only in IntelliJ IDEA, so it may use classes from Java plugin.
    return hasModuleDependencies(descriptor) ? null : javaDep;
  }

  static boolean hasModuleDependencies(@NotNull IdeaPluginDescriptorImpl descriptor) {
    if (descriptor.pluginDependencies == null) {
      return false;
    }

    for (PluginDependency dependency : descriptor.pluginDependencies) {
      PluginId depId = dependency.id;
      if (depId == JAVA_PLUGIN_ID || depId == JAVA_MODULE_ID || isModuleDependency(depId)) {
        return true;
      }
    }
    return false;
  }

  private static @NotNull ClassLoader createPluginClassLoader(ClassLoader @NotNull [] parentLoaders,
                                                              @NotNull IdeaPluginDescriptorImpl descriptor,
                                                              @NotNull UrlClassLoader.Builder urlLoaderBuilder,
                                                              @NotNull ClassLoader coreLoader,
                                                              @NotNull Map<String, String[]> additionalLayoutMap) {
    List<Path> classPath = descriptor.jarFiles;
    if (classPath == null) {
      classPath = descriptor.collectClassPath(additionalLayoutMap);
    }
    else {
      descriptor.jarFiles = null;
    }

    if (descriptor.isUseIdeaClassLoader()) {
      getLogger().warn(descriptor.getPluginId() + " uses deprecated `use-idea-classloader` attribute");
      ClassLoader loader = PluginManagerCore.class.getClassLoader();
      try {
        // `UrlClassLoader#addURL` can't be invoked directly, because the core classloader is created at bootstrap in a "lost" branch
        MethodHandle addURL = MethodHandles.lookup().findVirtual(loader.getClass(), "addURL", MethodType.methodType(void.class, URL.class));
        for (Path pathElement : classPath) {
          addURL.invoke(loader, localFileToUrl(pathElement, descriptor));
        }
        return loader;
      }
      catch (Throwable t) {
        throw new IllegalStateException("An unexpected core classloader: " + loader.getClass(), t);
      }
    }
    else {
      List<URL> urls = new ArrayList<>(classPath.size());
      for (Path pathElement : classPath) {
        urls.add(localFileToUrl(pathElement, descriptor));
      }
      PluginClassLoader loader = new PluginClassLoader(urlLoaderBuilder.urls(urls), parentLoaders, descriptor, descriptor.getPluginPath());
      if (usePluginClassLoader) {
        loader.setCoreLoader(coreLoader);
      }
      return loader;
    }
  }

  private static @NotNull URL localFileToUrl(@NotNull Path file, @NotNull IdeaPluginDescriptor descriptor) {
    try {
      // it is important not to have traversal elements in classpath
      return file.normalize().toUri().toURL();
    }
    catch (MalformedURLException e) {
      throw new PluginException("Corrupted path element: `" + file + '`', e, descriptor.getPluginId());
    }
  }

  public static synchronized void invalidatePlugins() {
    ourPlugins = null;
    //noinspection NonPrivateFieldAccessedInSynchronizedContext
    ourLoadedPlugins = null;
    DisabledPluginsState.invalidate();
    ourShadowedBundledPlugins = null;
  }

  private static void logPlugins(@NotNull IdeaPluginDescriptorImpl @NotNull[] plugins) {
    StringBuilder bundled = new StringBuilder();
    StringBuilder disabled = new StringBuilder();
    StringBuilder custom = new StringBuilder();
    for (IdeaPluginDescriptor descriptor : plugins) {
      StringBuilder target;
      if (!descriptor.isEnabled()) {
        target = disabled;
      }
      else if (descriptor.isBundled() || descriptor.getPluginId() == SPECIAL_IDEA_PLUGIN_ID) {
        target = bundled;
      }
      else {
        target = custom;
      }

      if (target.length() > 0) {
        target.append(", ");
      }

      target.append(descriptor.getName());
      String version = descriptor.getVersion();
      if (version != null) {
        target.append(" (").append(version).append(')');
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

  public static boolean isRunningFromSources() {
    Boolean result = isRunningFromSources;
    if (result == null) {
      result = Files.isDirectory(Paths.get(PathManager.getHomePath(), Project.DIRECTORY_STORE_FOLDER));
      isRunningFromSources = result;
    }
    return result;
  }

  private static void prepareLoadingPluginsErrorMessage(@NotNull List<PluginError> errors, @NotNull List<String> actions) {
    ourLoadingErrors = errors;
    List<PluginError> errorsToReport = new ArrayList<>();
    for (PluginError error : errors) {
      if (error.isNotifyUser()) errorsToReport.add(error);
    }

    // Log includes all messages, not only those which need to be reported to the user
    String message = "Problems found loading plugins:\n  " + errors.stream().map(PluginError::toString).collect(Collectors.joining("\n  "));
    Application app = ApplicationManager.getApplication();
    if (app == null || !app.isHeadlessEnvironment() || isUnitTestMode) {
      if (!errorsToReport.isEmpty()) {
        String errorMessage = Stream.concat(errorsToReport.stream().map(o -> StringUtil.escapeXmlEntities(o.toUserError()) + "."), actions.stream()).collect(Collectors.joining("<p/>"));
        if (ourPluginError == null) {
          ourPluginError = errorMessage;
        }
        else {
          ourPluginError += "<p/>\n" + errorMessage;
        }
      }

      // as warn in tests
      if (!errors.isEmpty()) {
        getLogger().warn(message);
      }
    }
    else {
      if (!errors.isEmpty()) {
        getLogger().error(message);
      }
    }
  }

  public static @Nullable String getLoadingError(@NotNull IdeaPluginDescriptor pluginDescriptor) {
    PluginError error = findErrorForPlugin(ourLoadingErrors, pluginDescriptor.getPluginId());
    if (error != null) {
      String reason = error.getIncompatibleReason();
      if (reason != null) {
        return "Incompatible (" + reason + ")";
      }
      return error.getMessage();
    }
    return null;
  }

  public static @Nullable PluginId getFirstDisabledDependency(@NotNull IdeaPluginDescriptor pluginDescriptor) {
    PluginError error = findErrorForPlugin(ourLoadingErrors, pluginDescriptor.getPluginId());
    if (error != null) {
      return error.getDisabledDependency();
    }
    return null;
  }

  private static @Nullable PluginError findErrorForPlugin(@Nullable List<PluginError> errors, @NotNull PluginId pluginId) {
    if (errors == null) return null;
    for (PluginError error : errors) {
      if (error.plugin != null && error.plugin.getPluginId().equals(pluginId)) {
        return error;
      }
    }
    return null;
  }

  private static @NotNull CachingSemiGraph<IdeaPluginDescriptorImpl> createPluginIdGraph(@NotNull List<IdeaPluginDescriptorImpl> descriptors,
                                                                                         @NotNull Function<PluginId, IdeaPluginDescriptorImpl> idToDescriptorMap,
                                                                                         boolean withOptional,
                                                                                         boolean hasAllModules) {
    IdeaPluginDescriptorImpl javaDep = idToDescriptorMap.apply(JAVA_MODULE_ID);
    Set<IdeaPluginDescriptorImpl> uniqueCheck = new HashSet<>();
    return new CachingSemiGraph<>(descriptors, rootDescriptor -> {
      List<PluginDependency> dependencies = rootDescriptor.pluginDependencies;
      List<PluginId> incompatibleModuleIds = ContainerUtil.notNullize(rootDescriptor.incompatibilities);
      if (dependencies == null) {
        dependencies = Collections.emptyList();
      }

      IdeaPluginDescriptorImpl implicitDep = getImplicitDependency(rootDescriptor, javaDep, hasAllModules);
      int capacity = dependencies.size() + incompatibleModuleIds.size();
      if (!withOptional) {
        for (PluginDependency dependency : dependencies) {
          if (dependency.isOptional) {
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
        if (!withOptional && dependency.isOptional) {
          continue;
        }

        // check for missing optional dependency
        IdeaPluginDescriptorImpl dep = idToDescriptorMap.apply(dependency.id);
        // if 'dep' refers to a module we need to check the real plugin containing this module only if it's still enabled,
        // otherwise the graph will be inconsistent
        if (dep == null) {
          continue;
        }

        // ultimate plugin it is combined plugin, where some included XML can define dependency on ultimate explicitly and for now not clear,
        // can be such requirements removed or not
        if (rootDescriptor == dep) {
          if (rootDescriptor.getPluginId() != CORE_ID) {
            getLogger().error("Plugin " + rootDescriptor + " depends on self");
          }
        }
        else if (uniqueCheck.add(dep)) {
          plugins.add(dep);
        }
      }

      for (PluginId moduleId : incompatibleModuleIds) {
        IdeaPluginDescriptorImpl dep = idToDescriptorMap.apply(moduleId);
        if (dep != null && uniqueCheck.add(dep)) {
          plugins.add(dep);
        }
      }

      return plugins;
    });
  }

  private static void checkPluginCycles(@NotNull List<IdeaPluginDescriptorImpl> descriptors,
                                        @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idToDescriptorMap,
                                        @NotNull List<PluginError> errors) {
    CachingSemiGraph<IdeaPluginDescriptorImpl> graph = createPluginIdGraph(descriptors, idToDescriptorMap::get, true,
                                                                           idToDescriptorMap.containsKey(ALL_MODULES_MARKER));
    DFSTBuilder<IdeaPluginDescriptorImpl> builder = new DFSTBuilder<>(GraphGenerator.generate(graph));
    if (builder.isAcyclic()) {
      return;
    }

    StringBuilder cyclePresentation = new StringBuilder();
    for (Collection<IdeaPluginDescriptorImpl> component : builder.getComponents()) {
      if (component.size() < 2) {
        continue;
      }

      if (cyclePresentation.length() > 0) {
        cyclePresentation.append(", ");
      }

      String separator = " <-> ";
      for (IdeaPluginDescriptor descriptor : component) {
        descriptor.setEnabled(false);

        cyclePresentation.append(descriptor.getPluginId());
        cyclePresentation.append(separator);
      }

      cyclePresentation.setLength(cyclePresentation.length() - separator.length());
    }

    if (cyclePresentation.length() > 0) {
      errors.add(new PluginError(null, "Plugins should not have cyclic dependencies: " + cyclePresentation, null));
    }
  }

  @Nullable
  static PathBasedJdomXIncluder.PathResolver<Path> createPluginJarsPathResolver(@NotNull Path pluginDir, @NotNull DescriptorLoadingContext context) {
    List<Path> pluginJarFiles = new ArrayList<>(), dirs = new ArrayList<>();
    if (!PluginDescriptorLoader.collectPluginDirectoryContents(pluginDir, pluginJarFiles, dirs)) return null;
    return new PluginXmlPathResolver(pluginJarFiles, context);
  }

  public static void getDescriptorsToMigrate(@NotNull Path dir,
                                             @Nullable BuildNumber compatibleBuildNumber,
                                             @Nullable String bundledPluginsPath,
                                             @Nullable Map<PluginId, Set<String>> brokenPluginVersions,
                                             List<IdeaPluginDescriptorImpl> pluginsToMigrate,
                                             List<IdeaPluginDescriptorImpl> incompatiblePlugins) throws ExecutionException, InterruptedException {
    PluginLoadingResult loadingResult = new PluginLoadingResult(
      brokenPluginVersions != null ? brokenPluginVersions : getBrokenPluginVersions(),
      () -> compatibleBuildNumber == null ? getBuildNumber() : compatibleBuildNumber
    );
    int flags = DescriptorListLoadingContext.IGNORE_MISSING_SUB_DESCRIPTOR | DescriptorListLoadingContext.IGNORE_MISSING_INCLUDE;
    DescriptorListLoadingContext context = new DescriptorListLoadingContext(flags, Collections.emptySet(), loadingResult);
    if (bundledPluginsPath != null) {
      context.loadBundledPlugins = true;
      context.bundledPluginsPath = bundledPluginsPath;
    }
    PluginDescriptorLoader.loadBundledDescriptorsAndDescriptorsFromDir(context, dir);

    for (IdeaPluginDescriptorImpl descriptor : loadingResult.idMap.values()) {
      if (!descriptor.isBundled()) {
        if (loadingResult.isBroken(descriptor.getPluginId())) {
          incompatiblePlugins.add(descriptor);
        }
        else {
          pluginsToMigrate.add(descriptor);
        }
      }
    }
    for (IdeaPluginDescriptorImpl descriptor : loadingResult.incompletePlugins.values()) {
      if (!descriptor.isBundled()) {
        incompatiblePlugins.add(descriptor);
      }
    }
  }

  private static void prepareLoadingPluginsErrorMessage(@NotNull Map<PluginId, String> disabledIds,
                                                        @NotNull Set<PluginId> disabledRequiredIds,
                                                        @NotNull Map<PluginId, ? extends IdeaPluginDescriptor> idMap,
                                                        @NotNull List<PluginError> errors) {
    List<String> actions = new ArrayList<>();
    if (!disabledIds.isEmpty()) {
      String text = "<br><a href=\"" + DISABLE + "\">Disable ";
      if (disabledIds.size() == 1) {
        PluginId id = disabledIds.keySet().iterator().next();
        text += idMap.containsKey(id) ? toPresentableName(idMap.get(id)) : toPresentableName(id.getIdString());
      }
      else {
        text += "not loaded plugins";
      }
      actions.add(text + "</a>");
      if (!disabledRequiredIds.isEmpty()) {
        String name = disabledRequiredIds.size() == 1
                      ? toPresentableName(idMap.get(disabledRequiredIds.iterator().next()))
                      : "all necessary plugins";
        actions.add("<a href=\"" + ENABLE + "\">Enable " + name + "</a>");
      }
      actions.add("<a href=\"" + EDIT + "\">Open plugin manager</a>");
    }
    prepareLoadingPluginsErrorMessage(errors, actions);
  }

  @TestOnly
  public static @NotNull List<? extends IdeaPluginDescriptor> testLoadDescriptorsFromClassPath(@NotNull ClassLoader loader)
    throws ExecutionException, InterruptedException {
    Map<URL, String> urlsFromClassPath = new LinkedHashMap<>();
    PluginDescriptorLoader.collectPluginFilesInClassPath(loader, urlsFromClassPath);
    BuildNumber buildNumber = BuildNumber.fromString("2042.42");
    DescriptorListLoadingContext context = new DescriptorListLoadingContext(0, Collections.emptySet(), new PluginLoadingResult(Collections.emptyMap(), () -> buildNumber, false));
    try (DescriptorLoadingContext loadingContext = new DescriptorLoadingContext(context, true, true, new ClassPathXmlPathResolver(loader))) {
      PluginDescriptorLoader.loadDescriptorsFromClassPath(urlsFromClassPath, loadingContext, null);
    }

    context.result.finishLoading();
    return context.result.getEnabledPlugins();
  }

  public static void scheduleDescriptorLoading() {
    getOrScheduleLoading();
  }

  private static synchronized @NotNull CompletableFuture<DescriptorListLoadingContext> getOrScheduleLoading() {
    CompletableFuture<DescriptorListLoadingContext> future = descriptorListFuture;
    if (future != null) {
      return future;
    }

    future = CompletableFuture.supplyAsync(() -> {
      Activity activity = StartUpMeasurer.startActivity("plugin descriptor loading");
      DescriptorListLoadingContext context = PluginDescriptorLoader.loadDescriptors();
      activity.end();
      return context;
    }, AppExecutorUtil.getAppExecutorService());
    descriptorListFuture = future;
    return future;
  }

  /**
   * Think twice before use and get approve from core team. Returns enabled plugins only.
   */
  @ApiStatus.Internal
  public static @NotNull List<IdeaPluginDescriptorImpl> getEnabledPluginRawList() {
    return getOrScheduleLoading().join().result.getEnabledPlugins();
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

  static @NotNull PluginLoadingResult createLoadingResult(@Nullable BuildNumber buildNumber) {
    return new PluginLoadingResult(getBrokenPluginVersions(), () -> buildNumber == null ? getBuildNumber() : buildNumber);
  }

  private static void mergeOptionalConfigs(@NotNull List<IdeaPluginDescriptorImpl> enabledPlugins,
                                           @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idMap) {
    if (isRunningFromSources()) {
      // fix optional configs
      for (IdeaPluginDescriptorImpl descriptor : enabledPlugins) {
        if (!descriptor.isUseCoreClassLoader() || descriptor.pluginDependencies == null) {
          continue;
        }

        for (PluginDependency dependency : descriptor.pluginDependencies) {
          if (dependency.subDescriptor == null) {
            continue;
          }

          IdeaPluginDescriptorImpl dependent = idMap.get(dependency.id);
          if (dependent != null && !dependent.isUseCoreClassLoader()) {
            // for what?
            dependency.subDescriptor = null;
          }
        }
      }
    }

    for (IdeaPluginDescriptorImpl mainDescriptor : enabledPlugins) {
      List<PluginDependency> pluginDependencies = mainDescriptor.pluginDependencies;
      if (pluginDependencies != null) {
        mergeOptionalDescriptors(mainDescriptor, pluginDependencies, idMap);
      }
    }
  }

  private static void mergeOptionalDescriptors(@NotNull IdeaPluginDescriptorImpl mergedDescriptor,
                                               @NotNull List<PluginDependency> pluginDependencies,
                                               @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idMap) {
    loop:
    for (PluginDependency dependency : pluginDependencies) {
      IdeaPluginDescriptorImpl subDescriptor = dependency.subDescriptor;
      dependency.subDescriptor = null;

      if (subDescriptor == null || dependency.isDisabledOrBroken) {
        continue;
      }

      IdeaPluginDescriptorImpl dependencyDescriptor = idMap.get(dependency.id);
      if (dependencyDescriptor == null || !dependencyDescriptor.isEnabled()) {
        continue;
      }

      // check that plugin doesn't depend on unavailable plugin
      if (subDescriptor.pluginDependencies != null) {
        for (PluginDependency pluginDependency : subDescriptor.pluginDependencies) {
          // ignore if optional
          if (!pluginDependency.isOptional) {
            if (pluginDependency.isDisabledOrBroken) {
              continue loop;
            }

            IdeaPluginDescriptorImpl dependentDescriptor = idMap.get(pluginDependency.id);
            if (dependentDescriptor == null || !dependentDescriptor.isEnabled()) {
              continue loop;
            }
          }
        }
      }

      mergedDescriptor.mergeOptionalConfig(subDescriptor);
      List<PluginDependency> childDependencies = subDescriptor.pluginDependencies;
      if (childDependencies != null) {
        mergeOptionalDescriptors(mergedDescriptor, childDependencies, idMap);
      }
    }
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

  // not used by plugin manager - only for dynamic plugin reloading
  @ApiStatus.Internal
  public static void initClassLoader(@NotNull IdeaPluginDescriptorImpl rootDescriptor) {
    Map<PluginId, IdeaPluginDescriptorImpl> idMap = buildPluginIdMap(ContainerUtil.concat(getLoadedPlugins(null), Collections.singletonList(rootDescriptor)));

    Set<ClassLoader> loaders = new LinkedHashSet<>();
    processAllDependencies(rootDescriptor, true, idMap, descriptor -> {
      ClassLoader loader = descriptor.getPluginClassLoader();
      if (loader == null) {
        getLogger().error(rootDescriptor.formatErrorMessage("requires missing class loader for " + toPresentableName(descriptor)));
      }
      else {
        loaders.add(loader);
      }
      // see configureClassLoaders about why we don't need to process recursively
      return FileVisitResult.SKIP_SUBTREE;
    });

    IdeaPluginDescriptorImpl javaDep = idMap.get(JAVA_MODULE_ID);
    boolean hasAllModules = idMap.containsKey(ALL_MODULES_MARKER);
    IdeaPluginDescriptorImpl implicitDependency = getImplicitDependency(rootDescriptor, javaDep, hasAllModules);
    if (implicitDependency != null && implicitDependency.getPluginClassLoader() != null) {
      loaders.add(implicitDependency.getPluginClassLoader());
    }

    ClassLoader[] array = loaders.isEmpty()
                          ? new ClassLoader[]{PluginManagerCore.class.getClassLoader()}
                          : loaders.toArray(new ClassLoader[0]);
    rootDescriptor.setLoader(createPluginClassLoader(array, rootDescriptor, createUrlClassLoaderBuilder(), PluginManagerCore.class.getClassLoader(), ourAdditionalLayoutMap));
  }

  private static @NotNull UrlClassLoader.Builder createUrlClassLoaderBuilder() {
    return UrlClassLoader.build().allowLock().useCache().urlsInterned();
  }

  static @NotNull BuildNumber getBuildNumber() {
    BuildNumber result = ourBuildNumber;
    if (result == null) {
      result = BuildNumber.fromString(getPluginsCompatibleBuild());
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
                                                 @NotNull List<PluginError> errors) {
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
      List<String> strings = StringUtil.split(selectedIds, ",");
      for (String it : strings) {
        set.add(PluginId.getId(it));
      }
      set.addAll(((ApplicationInfoImpl)ApplicationInfoImpl.getShadowInstance()).getEssentialPluginsIds());

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
        errors.add(new PluginError(descriptor, "was marked as broken", "marked as broken"));
      }
      else if (explicitlyEnabled != null) {
        if (!explicitlyEnabled.contains(descriptor)) {
          descriptor.setEnabled(false);
          getLogger().info("Plugin " + toPresentableName(descriptor) + " " +
                           (selectedIds != null
                            ? "is not in 'idea.load.plugins.id' system property"
                            : "category doesn't match 'idea.load.plugins.category' system property"));
        }
      }
      else if (!shouldLoadPlugins) {
        descriptor.setEnabled(false);
        errors.add(new PluginError(descriptor, "is skipped (plugins loading disabled)", null));
      }
      else if (isNonBundledPluginDisabled && !descriptor.isBundled()) {
        descriptor.setEnabled(false);
        errors.add(new PluginError(descriptor, "is skipped (third-party plugins loading disabled)", null, false));
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
    return getIncompatibleMessage(buildNumber, descriptor.getSinceBuild(), descriptor.getUntilBuild()) != null;
  }

  static @Nullable String getIncompatibleMessage(@NotNull BuildNumber buildNumber, @Nullable String sinceBuild, @Nullable String untilBuild) {
    try {
      String message = null;
      BuildNumber sinceBuildNumber = sinceBuild == null ? null : BuildNumber.fromString(sinceBuild, null, null);
      if (sinceBuildNumber != null && sinceBuildNumber.compareTo(buildNumber) > 0) {
        message = "since build " + sinceBuildNumber + " > " + buildNumber;
      }

      BuildNumber untilBuildNumber = untilBuild == null ? null : BuildNumber.fromString(untilBuild, null, null);
      if (untilBuildNumber != null && untilBuildNumber.compareTo(buildNumber) < 0) {
        if (message == null) {
          message = "";
        }
        else {
          message += ", ";
        }
        message += "until build " + untilBuildNumber + " < " + buildNumber;
      }
      return message;
    }
    catch (Exception e) {
      getLogger().error(e);
      return "version check failed";
    }
  }

  private static void checkEssentialPluginsAreAvailable(@NotNull Map<PluginId, IdeaPluginDescriptorImpl> idMap) {
    List<PluginId> required = ((ApplicationInfoImpl)ApplicationInfoImpl.getShadowInstance()).getEssentialPluginsIds();
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
    List<PluginError> errors = new ArrayList<>(loadingResult.getErrors());

    if (loadingResult.duplicateModuleMap != null) {
      for (Map.Entry<PluginId, List<IdeaPluginDescriptorImpl>> entry : loadingResult.duplicateModuleMap.entrySet()) {
        errors.add(new PluginError(null, "Module " + entry.getKey() + " is declared by plugins:\n  " + StringUtil.join(entry.getValue(), "\n  "), null));
      }
    }

    Map<PluginId, IdeaPluginDescriptorImpl> idMap = loadingResult.idMap;
    IdeaPluginDescriptorImpl coreDescriptor = idMap.get(CORE_ID);
    if (checkEssentialPlugins && coreDescriptor == null) {
      throw new EssentialPluginMissingException(Collections.singletonList(CORE_ID + " (platform prefix: " + System.getProperty(PlatformUtils.PLATFORM_PREFIX_KEY) + ")"));
    }

    List<IdeaPluginDescriptorImpl> descriptors = loadingResult.getEnabledPlugins();
    disableIncompatiblePlugins(descriptors, idMap, errors);
    checkPluginCycles(descriptors, idMap, errors);

    // topological sort based on required dependencies only
    IdeaPluginDescriptorImpl[] sortedRequired = getTopologicallySorted(createPluginIdGraph(descriptors, idMap::get, false,
                                                                                           idMap.containsKey(ALL_MODULES_MARKER)));

    Set<PluginId> enabledPluginIds = new LinkedHashSet<>();
    Set<PluginId> enabledModuleIds = new LinkedHashSet<>();
    Map<PluginId, String> disabledIds = new LinkedHashMap<>();
    Set<PluginId> disabledRequiredIds = new LinkedHashSet<>();

    for (IdeaPluginDescriptorImpl descriptor : sortedRequired) {
      boolean wasEnabled = descriptor.isEnabled();
      if (wasEnabled && computePluginEnabled(descriptor, enabledPluginIds, enabledModuleIds, idMap, disabledRequiredIds, context.disabledPlugins, errors)) {
        enabledPluginIds.add(descriptor.getPluginId());
        enabledModuleIds.addAll(descriptor.getModules());
      }
      else {
        descriptor.setEnabled(false);
        if (wasEnabled) {
          disabledIds.put(descriptor.getPluginId(), descriptor.getName());
        }
      }
    }

    prepareLoadingPluginsErrorMessage(disabledIds, disabledRequiredIds, idMap, errors);

    // topological sort based on all (required and optional) dependencies
    CachingSemiGraph<IdeaPluginDescriptorImpl> graph = createPluginIdGraph(Arrays.asList(sortedRequired), idMap::get, true,
                                                                           idMap.containsKey(ALL_MODULES_MARKER));
    IdeaPluginDescriptorImpl[] sortedAll = getTopologicallySorted(graph);

    List<IdeaPluginDescriptorImpl> enabledPlugins = getOnlyEnabledPlugins(sortedAll);

    mergeOptionalConfigs(enabledPlugins, idMap);
    Map<String, String[]> additionalLayoutMap = loadAdditionalLayoutMap();
    ourAdditionalLayoutMap = additionalLayoutMap;
    configureClassLoaders(coreLoader, graph, coreDescriptor, enabledPlugins, additionalLayoutMap, context.usePluginClassLoader);

    if (checkEssentialPlugins) {
      checkEssentialPluginsAreAvailable(idMap);
    }

    Set<PluginId> effectiveDisabledIds = disabledIds.isEmpty() ? Collections.emptySet() : new HashSet<>(disabledIds.keySet());
    return new PluginManagerState(sortedAll, enabledPlugins, disabledRequiredIds, effectiveDisabledIds, idMap);
  }

  private static void configureClassLoaders(@NotNull ClassLoader coreLoader,
                                            @NotNull CachingSemiGraph<IdeaPluginDescriptorImpl> graph,
                                            @Nullable IdeaPluginDescriptor coreDescriptor,
                                            @NotNull List<IdeaPluginDescriptorImpl> enabledPlugins,
                                            @NotNull Map<String, String[]> additionalLayoutMap,
                                            boolean usePluginClassLoader) {
    ArrayList<ClassLoader> loaders = new ArrayList<>();
    ClassLoader[] emptyClassLoaderArray = new ClassLoader[0];
    UrlClassLoader.Builder urlClassLoaderBuilder = createUrlClassLoaderBuilder();
    for (IdeaPluginDescriptorImpl rootDescriptor : enabledPlugins) {
      if (rootDescriptor == coreDescriptor || rootDescriptor.isUseCoreClassLoader()) {
        rootDescriptor.setLoader(coreLoader);
        continue;
      }

      if (!usePluginClassLoader) {
        rootDescriptor.setLoader(null);
        continue;
      }

      loaders.clear();

      // no need to process dependencies recursively because dependency will use own classloader
      // (that in turn will delegate class searching to parent class loader if needed)
      List<IdeaPluginDescriptorImpl> dependencies = graph.getInList(rootDescriptor);
      if (!dependencies.isEmpty()) {
        loaders.ensureCapacity(dependencies.size());

        // do not add core loader - will be added to some dependency
        for (IdeaPluginDescriptorImpl descriptor : dependencies) {
          ClassLoader loader = descriptor.getPluginClassLoader();
          if (loader == null) {
            getLogger().error(rootDescriptor.formatErrorMessage("requires missing class loader for " + toPresentableName(descriptor)));
          }
          else {
            loaders.add(loader);
          }
        }
      }

      ClassLoader[] parentLoaders = loaders.isEmpty() ? new ClassLoader[]{coreLoader} : loaders.toArray(emptyClassLoaderArray);
      rootDescriptor.setLoader(createPluginClassLoader(parentLoaders, rootDescriptor, urlClassLoaderBuilder, coreLoader, additionalLayoutMap));
    }
  }

  private static @NotNull IdeaPluginDescriptorImpl @NotNull [] getTopologicallySorted(@NotNull InboundSemiGraph<IdeaPluginDescriptorImpl> graph) {
    DFSTBuilder<IdeaPluginDescriptorImpl> requiredOnlyGraph = new DFSTBuilder<>(GraphGenerator.generate(graph));
    IdeaPluginDescriptorImpl[] sortedRequired = graph.getNodes().toArray(IdeaPluginDescriptorImpl.EMPTY_ARRAY);
    Comparator<IdeaPluginDescriptorImpl> comparator = requiredOnlyGraph.comparator();
    // there is circular reference between core and implementation-detail plugin, as not all such plugins extracted from core,
    // so, ensure that core plugin is always first (otherwise not possible to register actions - parent group not defined)
    Arrays.sort(sortedRequired, (o1, o2) -> {
      if (o1.getPluginId() == CORE_ID) {
        return -1;
      }
      else if (o2.getPluginId() == CORE_ID) {
        return 1;
      }
      else {
        return comparator.compare(o1, o2);
      }
    });
    return sortedRequired;
  }

  public static List<IdeaPluginDescriptorImpl> getPluginsSortedByDependency(List<IdeaPluginDescriptorImpl> plugins, boolean withOptional) {
    CachingSemiGraph<IdeaPluginDescriptorImpl> graph = createPluginIdGraph(plugins,
                                                                           (id) -> (IdeaPluginDescriptorImpl)getPlugin(id),
                                                                           withOptional,
                                                                           findPluginByModuleDependency(ALL_MODULES_MARKER) != null);
    IdeaPluginDescriptorImpl[] sortedRequired = getTopologicallySorted(graph);
    return Arrays.asList(sortedRequired);
  }

  @ApiStatus.Internal
  public static @NotNull Map<PluginId, IdeaPluginDescriptorImpl> buildPluginIdMap(@NotNull List<IdeaPluginDescriptorImpl> descriptors) {
    Map<PluginId, IdeaPluginDescriptorImpl> idMap = new LinkedHashMap<>(descriptors.size());
    Map<PluginId, List<IdeaPluginDescriptorImpl>> duplicateMap = null;
    for (IdeaPluginDescriptorImpl descriptor : descriptors) {
      Map<PluginId, List<IdeaPluginDescriptorImpl>> newDuplicateMap = checkAndPut(descriptor, descriptor.getPluginId(), idMap, duplicateMap);
      if (newDuplicateMap != null) {
        duplicateMap = newDuplicateMap;
        continue;
      }

      for (PluginId module : descriptor.getModules()) {
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
                                              @NotNull List<PluginError> errors) {
    if (descriptor.getPluginId() == CORE_ID) {
      return true;
    }
    boolean notifyUser = !descriptor.isImplementationDetail();

    boolean result = true;

    for (PluginId incompatibleId : ContainerUtil.notNullize(descriptor.incompatibilities)) {
      if (!loadedModuleIds.contains(incompatibleId) || disabledPlugins.contains(incompatibleId)) continue;

      result = false;
      String presentableName = toPresentableName(incompatibleId.getIdString());
      errors.add(new PluginError(descriptor, "is incompatible with the IDE containing module " + presentableName,
                                 "IDE contains module " + presentableName, notifyUser));
    }

    // no deps at all
    if (descriptor.pluginDependencies == null) {
      return result;
    }

    for (PluginDependency dependency : descriptor.pluginDependencies) {
      PluginId depId = dependency.id;
      if (dependency.isOptional || loadedPluginIds.contains(depId) || loadedModuleIds.contains(depId)) {
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
        if (findErrorForPlugin(errors, depId) != null) {
          errors.add(new PluginError(descriptor, "depends on plugin " + toPresentableName(depId.getIdString()) + " that failed to load", null, notifyUser));
        }
        else {
          errors.add(new PluginError(descriptor, "requires " + toPresentableName(depId.getIdString()) + " plugin to be installed", null, notifyUser));
        }
      }
      else {
        PluginError error = new PluginError(descriptor, "requires " + toPresentableName(depName) + " plugin to be enabled", null, notifyUser);
        error.setDisabledDependency(dep.getPluginId());
        errors.add(error);
      }
    }
    return result;
  }

  private static String toPresentableName(@Nullable IdeaPluginDescriptor descriptor) {
    return toPresentableName(descriptor == null ? null : descriptor.getName());
  }

  private static @NotNull String toPresentableName(@Nullable String s) {
    return "\"" + (s == null ? "" : s) + "\"";
  }

  /**
   * Load extensions points and extensions from a configuration file in plugin.xml format
   * <p>
   * Use it only for CoreApplicationEnvironment. Do not use otherwise. For IntelliJ Platform application and tests plugins are loaded in parallel
   * (including other optimizations).
   *
   * @param pluginRoot jar file or directory which contains the configuration file
   * @param fileName   name of the configuration file located in 'META-INF' directory under {@code pluginRoot}
   * @param area       area which extension points and extensions should be registered
   */
  public static void registerExtensionPointAndExtensions(@NotNull Path pluginRoot, @NotNull String fileName, @NotNull ExtensionsArea area) {
    IdeaPluginDescriptorImpl descriptor;
    DescriptorListLoadingContext parentContext = DescriptorListLoadingContext.createSingleDescriptorContext(
      DisabledPluginsState.disabledPlugins());
    try (DescriptorLoadingContext context = new DescriptorLoadingContext(parentContext, true, true, PathBasedJdomXIncluder.DEFAULT_PATH_RESOLVER)) {
      if (Files.isDirectory(pluginRoot)) {
        descriptor = PluginDescriptorLoader.loadDescriptorFromDir(pluginRoot, META_INF + fileName, null, context);
      }
      else {
        descriptor = PluginDescriptorLoader.loadDescriptorFromJar(pluginRoot, fileName, PathBasedJdomXIncluder.DEFAULT_PATH_RESOLVER, context, null);
      }
    }

    if (descriptor == null) {
      getLogger().error("Cannot load " + fileName + " from " + pluginRoot);
      return;
    }

    List<ExtensionPointImpl<?>> extensionPoints = descriptor.appContainerDescriptor.extensionPoints;
    if (extensionPoints != null) {
      ((ExtensionsAreaImpl)area).registerExtensionPoints(extensionPoints, false);
    }
    descriptor.registerExtensions((ExtensionsAreaImpl)area, descriptor, descriptor.appContainerDescriptor, null);
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
        context = PluginDescriptorLoader.loadDescriptors();
      }
      Activity activity = StartUpMeasurer.startActivity("plugin initialization");
      PluginManagerState initResult = initializePlugins(context, coreLoader, !isUnitTestMode);

      ourPlugins = initResult.sortedPlugins;
      PluginLoadingResult result = context.result;
      if (!result.incompletePlugins.isEmpty()) {
        int oldSize = initResult.sortedPlugins.length;
        IdeaPluginDescriptorImpl[] all = new IdeaPluginDescriptorImpl[oldSize + result.incompletePlugins.size()];
        System.arraycopy(initResult.sortedPlugins, 0, all, 0, oldSize);
        ArrayUtil.copy(result.incompletePlugins.values(), all, oldSize);
        ourPlugins = all;
      }

      ourPluginsToDisable = initResult.effectiveDisabledIds;
      ourPluginsToEnable = initResult.disabledRequiredIds;
      ourLoadedPlugins = initResult.sortedEnabledPlugins;
      ourShadowedBundledPlugins = result.getShadowedBundledIds();

      activity.end();
      activity.setDescription("plugin count: " + ourLoadedPlugins.size());
      logPlugins(initResult.sortedPlugins);
    }
    catch (ExtensionInstantiationException e) {
      throw new PluginException(e, e.getExtensionOwnerId());
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
        if (id == plugin.getPluginId()) {
          return plugin;
        }
      }
    }
    return null;
  }

  public static @Nullable IdeaPluginDescriptor findPluginByModuleDependency(@NotNull PluginId id) {
    for (IdeaPluginDescriptor descriptor : getPlugins()) {
      if (descriptor instanceof IdeaPluginDescriptorImpl) {
        if (((IdeaPluginDescriptorImpl)descriptor).getModules().contains(id)) {
          return descriptor;
        }
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
                                               @NotNull Function<IdeaPluginDescriptor, FileVisitResult> consumer) {
    return processAllDependencies(rootDescriptor, withOptionalDeps, buildPluginIdMap(), consumer);
  }

  @ApiStatus.Internal
  public static boolean processAllDependencies(@NotNull IdeaPluginDescriptorImpl rootDescriptor,
                                               boolean withOptionalDeps,
                                               @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idToMap,
                                               @NotNull Function<IdeaPluginDescriptor, FileVisitResult> consumer) {
    return processAllDependencies(rootDescriptor, withOptionalDeps, idToMap, new HashSet<>(), (id, descriptor) -> descriptor != null ? consumer.apply(descriptor) : FileVisitResult.SKIP_SUBTREE);
  }

  @SuppressWarnings("UnusedReturnValue")
  @ApiStatus.Internal
  public static boolean processAllDependencies(@NotNull IdeaPluginDescriptorImpl rootDescriptor,
                                               boolean withOptionalDeps,
                                               @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idToMap,
                                               @NotNull BiFunction<? super @NotNull PluginId, ? super @Nullable IdeaPluginDescriptor, FileVisitResult> consumer) {
    return processAllDependencies(rootDescriptor, withOptionalDeps, idToMap, new HashSet<>(), consumer);
  }

  @ApiStatus.Internal
  private static boolean processAllDependencies(@NotNull IdeaPluginDescriptorImpl rootDescriptor,
                                               boolean withOptionalDeps,
                                               @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idToMap,
                                               @NotNull Set<IdeaPluginDescriptor> depProcessed,
                                               @NotNull BiFunction<? super PluginId, ? super IdeaPluginDescriptorImpl, FileVisitResult> consumer) {

    if (rootDescriptor.pluginDependencies == null) {
      return true;
    }

    for (PluginDependency dependency : rootDescriptor.pluginDependencies) {
      if (!withOptionalDeps && dependency.isOptional) {
        continue;
      }

      IdeaPluginDescriptorImpl descriptor = idToMap.get(dependency.id);
      PluginId pluginId = descriptor == null ? dependency.id : descriptor.getPluginId();
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

  public static boolean processAllBackwardDependencies(@NotNull IdeaPluginDescriptorImpl rootDescriptor,
                                                       boolean withOptionalDeps,
                                                       @NotNull Function<IdeaPluginDescriptor, FileVisitResult> consumer) {
    CachingSemiGraph<IdeaPluginDescriptorImpl> semiGraph = createPluginIdGraph(Arrays.asList(ourPlugins),
                                                                               (id) -> (IdeaPluginDescriptorImpl)getPlugin(id),
                                                                               withOptionalDeps,
                                                                               findPluginByModuleDependency(ALL_MODULES_MARKER) != null);
    Graph<IdeaPluginDescriptorImpl> graph = GraphGenerator.generate(semiGraph);
    Set<IdeaPluginDescriptorImpl> dependencies = new LinkedHashSet<>();
    GraphAlgorithms.getInstance().collectOutsRecursively(graph, rootDescriptor, dependencies);
    for (IdeaPluginDescriptorImpl dependency : dependencies) {
      if (dependency == rootDescriptor) continue;
      if (consumer.apply(dependency) == FileVisitResult.TERMINATE) return false;
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

  /**
   * @deprecated Use {@link PluginManager#addDisablePluginListener}
   */
  @Deprecated
  public static void addDisablePluginListener(@NotNull Runnable listener) {
    PluginManager.getInstance().addDisablePluginListener(listener);
  }

  /**
   * @deprecated Use {@link PluginManager#addDisablePluginListener}
   */
  @Deprecated
  public static void removeDisablePluginListener(@NotNull Runnable listener) {
    PluginManager.getInstance().removeDisablePluginListener(listener);
  }

  public static synchronized boolean isUpdatedBundledPlugin(@NotNull PluginDescriptor plugin) {
    return ourShadowedBundledPlugins != null && ourShadowedBundledPlugins.contains(plugin.getPluginId());
  }
}