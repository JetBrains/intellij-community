// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.LoadingState;
import com.intellij.diagnostic.PluginException;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.JetBrainsProtocolHandler;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.reference.SoftReference;
import com.intellij.serialization.SerializationException;
import com.intellij.util.*;
import com.intellij.util.containers.*;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.graph.*;
import com.intellij.util.io.URLUtil;
import com.intellij.util.lang.UrlClassLoader;
import com.intellij.util.text.VersionComparatorUtil;
import gnu.trove.THashSet;
import gnu.trove.TObjectIntHashMap;
import org.jdom.Element;
import org.jdom.JDOMException;
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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.intellij.util.ObjectUtils.notNull;

public class PluginManagerCore {
  public static final String META_INF = "META-INF/";
  public static final String IDEA_IS_INTERNAL_PROPERTY = "idea.is.internal";

  public static final String DISABLED_PLUGINS_FILENAME = "disabled_plugins.txt";
  public static final String CORE_PLUGIN_ID = "com.intellij";
  public static final String PLUGIN_XML = "plugin.xml";
  public static final String PLUGIN_XML_PATH = META_INF + PLUGIN_XML;
  private static final String ALL_MODULES_MARKER = "com.intellij.modules.all";

  public static final String VENDOR_JETBRAINS = "JetBrains";

  @SuppressWarnings("StaticNonFinalField")
  public static String BUILD_NUMBER;

  private static final TObjectIntHashMap<PluginId> ourId2Index = new TObjectIntHashMap<>();
  private static final String MODULE_DEPENDENCY_PREFIX = "com.intellij.module";

  private static final PluginId SPECIAL_IDEA_PLUGIN_ID = PluginId.getId("IDEA CORE");

  private static final String PROPERTY_PLUGIN_PATH = "plugin.path";

  public static final String DISABLE = "disable";
  public static final String ENABLE = "enable";
  public static final String EDIT = "edit";

  private static volatile Set<String> ourDisabledPlugins;
  private static Reference<Map<String, Set<String>>> ourBrokenPluginVersions;
  private static volatile IdeaPluginDescriptorImpl[] ourPlugins;
  private static List<? extends IdeaPluginDescriptor> ourLoadedPlugins;

  @SuppressWarnings("StaticNonFinalField")
  public static volatile boolean isUnitTestMode = Boolean.getBoolean("idea.is.unit.test");

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private static boolean ourUnitTestWithBundledPlugins = Boolean.getBoolean("idea.run.tests.with.bundled.plugins");

  @SuppressWarnings("StaticNonFinalField") @ApiStatus.Internal
  public static String ourPluginError;

  @SuppressWarnings("StaticNonFinalField")
  @ApiStatus.Internal
  public static Set<String> ourPlugins2Disable;
  @SuppressWarnings("StaticNonFinalField")
  @ApiStatus.Internal
  public static Set<String> ourPlugins2Enable;

  private static class Holder {
    private static final BuildNumber ourBuildNumber = calcBuildNumber();
    private static final boolean ourIsRunningFromSources =
      new File(PathManager.getHomePath(), Project.DIRECTORY_STORE_FOLDER).isDirectory();

    private static BuildNumber calcBuildNumber() {
      BuildNumber ourBuildNumber = BuildNumber.fromString(System.getProperty("idea.plugins.compatible.build"));
      if (ourBuildNumber == null) {
        ourBuildNumber = BUILD_NUMBER == null ? null : BuildNumber.fromString(BUILD_NUMBER);
        if (ourBuildNumber == null) {
          ourBuildNumber = BuildNumber.currentVersion();
        }
      }
      return ourBuildNumber;
    }
  }

  private static final List<Runnable> ourDisabledPluginsListeners = new CopyOnWriteArrayList<>();

  /**
   * Returns list of all available plugin descriptors (bundled and custom, include disabled ones). Use {@link #getLoadedPlugins()}
   * if you need to get loaded plugins only.
   *
   * <p>
   * Do not call this method during bootstrap, should be called in a copy of PluginManager, loaded by PluginClassLoader.
   */
  @NotNull
  public static IdeaPluginDescriptor[] getPlugins() {
    IdeaPluginDescriptor[] result = ourPlugins;
    if (result == null) {
      initPlugins(null);
      return ourPlugins;
    }
    return result;
  }

  /**
   * Returns descriptors of plugins which are successfully loaded into IDE. The result is sorted in a way that if each plugin comes after
   * the plugins it depends on.
   */
  @NotNull
  public static List<? extends IdeaPluginDescriptor> getLoadedPlugins() {
    return getLoadedPlugins(null);
  }

  @NotNull
  public static synchronized List<? extends IdeaPluginDescriptor> getLoadedPlugins(@Nullable ClassLoader coreClassLoader) {
    List<? extends IdeaPluginDescriptor> result = ourLoadedPlugins;
    if (result == null) {
      initPlugins(coreClassLoader);
      return ourLoadedPlugins;
    }
    return result;
  }

  @ApiStatus.Internal
  public static boolean arePluginsInitialized() {
    return ourPlugins != null;
  }

  @ApiStatus.Internal
  public static synchronized void setPlugins(@NotNull IdeaPluginDescriptor[] descriptors) {
    //noinspection SuspiciousToArrayCall
    IdeaPluginDescriptorImpl[] copy = Arrays.asList(descriptors).toArray(IdeaPluginDescriptorImpl.EMPTY_ARRAY);
    ourPlugins = copy;
    ourLoadedPlugins = Collections.unmodifiableList(ContainerUtil.findAll(copy, IdeaPluginDescriptor::isEnabled));
  }

  @ApiStatus.Internal
  public static void loadDisabledPlugins(@NotNull String configPath, @NotNull Collection<String> disabledPlugins) {
    Path file = Paths.get(configPath, DISABLED_PLUGINS_FILENAME);
    if (!Files.isRegularFile(file)) {
      return;
    }

    List<String> requiredPlugins = StringUtil.split(System.getProperty(JetBrainsProtocolHandler.REQUIRED_PLUGINS_KEY, ""), ",");
    try {
      boolean updateDisablePluginsList = false;
      try (BufferedReader reader = Files.newBufferedReader(file)) {
        String id;
        while ((id = reader.readLine()) != null) {
          id = id.trim();
          if (!requiredPlugins.contains(id) && !ApplicationInfoImpl.getShadowInstance().isEssentialPlugin(id)) {
            disabledPlugins.add(id);
          }
          else {
            updateDisablePluginsList = true;
          }
        }
      }
      finally {
        if (updateDisablePluginsList) {
          savePluginsList(disabledPlugins, false, file.toFile());
          fireEditDisablePlugins();
        }
      }
    }
    catch (IOException e) {
      getLogger().info("Unable to load disabled plugins list from " + file, e);
    }
  }

  // For use in headless environment only
  public static void dontLoadDisabledPlugins() {
    ourDisabledPlugins = Collections.emptySet();
  }

  /**
   * @deprecated Bad API, sorry. Please use {@link #isDisabled(String)} to check plugin's state,
   * {@link #enablePlugin(String)}/{@link #disablePlugin(String)} for state management,
   * {@link #disabledPlugins()} to get an unmodifiable collection of all disabled plugins (rarely needed).
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  public static @NotNull
  List<String> getDisabledPlugins() {
    Set<String> list = loadDisabledPlugins();
    return new AbstractList<String>() {
      //<editor-fold desc="Just a ist-like immutable wrapper over a set; move along.">
      @Override
      public boolean contains(Object o) {
        return list.contains(o);
      }

      @Override
      public int size() {
        return list.size();
      }

      @Override
      public String get(int index) {
        if (index < 0 || index >= list.size()) {
          throw new IndexOutOfBoundsException("index=" + index + " size=" + list.size());
        }
        Iterator<String> iterator = list.iterator();
        for (int i = 0; i < index; i++) iterator.next();
        return iterator.next();
      }
      //</editor-fold>
    };
  }

  @NotNull
  private static Set<String> loadDisabledPlugins() {
    Set<String> result = ourDisabledPlugins;
    if (result != null) {
      return result;
    }

    // to preserve the order of additions and removals
    if (System.getProperty("idea.ignore.disabled.plugins") != null) {
      return Collections.emptySet();
    }

    //noinspection SynchronizeOnThis
    synchronized (PluginManagerCore.class) {
      result = ourDisabledPlugins;
      if (result != null) {
        return result;
      }

      result = new LinkedHashSet<>();
      loadDisabledPlugins(PathManager.getConfigPath(), result);
      ourDisabledPlugins = result;
    }
    return result;
  }

  @NotNull
  public static Set<String> disabledPlugins() {
    return Collections.unmodifiableSet(loadDisabledPlugins());
  }

  public static boolean isDisabled(@NotNull String pluginId) {
    return loadDisabledPlugins().contains(pluginId);
  }

  public static boolean isBrokenPlugin(@NotNull IdeaPluginDescriptor descriptor) {
    PluginId pluginId = descriptor.getPluginId();
    if (pluginId == null) {
      return true;
    }

    Set<String> set = getBrokenPluginVersions().get(pluginId.getIdString());
    return set != null && set.contains(descriptor.getVersion());
  }

  @NotNull
  private static Map<String, Set<String>> getBrokenPluginVersions() {
    Map<String, Set<String>> result = SoftReference.dereference(ourBrokenPluginVersions);
    if (result != null) {
      return result;
    }

    result = new HashMap<>();

    if (System.getProperty("idea.ignore.disabled.plugins") == null) {
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

          String pluginId = tokens.get(0);
          List<String> versions = tokens.subList(1, tokens.size());

          Set<String> set = result.get(pluginId);
          if (set == null) {
            set = new HashSet<>();
            result.put(pluginId, set);
          }
          set.addAll(versions);
        }
      }
      catch (IOException e) {
        throw new RuntimeException("Failed to read /brokenPlugins.txt", e);
      }
    }
    ourBrokenPluginVersions = new java.lang.ref.SoftReference<>(result);

    return result;
  }

  public static void addDisablePluginListener(@NotNull Runnable listener) {
    ourDisabledPluginsListeners.add(listener);
  }

  public static void removeDisablePluginListener(@NotNull Runnable listener) {
    ourDisabledPluginsListeners.remove(listener);
  }

  private static void fireEditDisablePlugins() {
    for (Runnable listener : ourDisabledPluginsListeners) {
      listener.run();
    }
  }

  public static void savePluginsList(@NotNull Collection<String> ids, boolean append, @NotNull File plugins) throws IOException {
    if (!plugins.isFile()) {
      FileUtilRt.ensureCanCreateFile(plugins);
    }
    try (BufferedWriter writer = new BufferedWriter(
      new OutputStreamWriter(new FileOutputStream(plugins, append), StandardCharsets.UTF_8))) {
      writePluginsList(ids, writer);
    }
  }

  public static void writePluginsList(@NotNull Collection<String> ids, @NotNull Writer writer) throws IOException {
    String[] sortedIds = ArrayUtil.toStringArray(ids);
    Arrays.sort(sortedIds);
    String separator = LineSeparator.getSystemLineSeparator().getSeparatorString();
    for (String id : sortedIds) {
      writer.write(id);
      writer.write(separator);
    }
  }

  public static boolean disablePlugin(@NotNull String id) {
    Set<String> disabledPlugins = loadDisabledPlugins();
    return disabledPlugins.add(id) && trySaveDisabledPlugins(disabledPlugins);
  }

  public static boolean enablePlugin(@NotNull String id) {
    Set<String> disabledPlugins = loadDisabledPlugins();
    return disabledPlugins.remove(id) && trySaveDisabledPlugins(disabledPlugins);
  }

  private static boolean trySaveDisabledPlugins(Collection<String> disabledPlugins) {
    try {
      saveDisabledPlugins(disabledPlugins, false);
      return true;
    }
    catch (IOException e) {
      getLogger().warn("Unable to save disabled plugins list", e);
      return false;
    }
  }

  public static void saveDisabledPlugins(@NotNull Collection<String> ids, boolean append) throws IOException {
    saveDisabledPlugins(PathManager.getConfigPath(), ids, append);
  }

  public static void saveDisabledPlugins(@NotNull String configPath, @NotNull Collection<String> ids, boolean append) throws IOException {
    File plugins = new File(configPath, DISABLED_PLUGINS_FILENAME);
    savePluginsList(ids, append, plugins);
    ourDisabledPlugins = null;
    fireEditDisablePlugins();
  }

  public static int getPluginLoadingOrder(@NotNull PluginId id) {
    return ourId2Index.get(id);
  }

  public static boolean isModuleDependency(@NotNull PluginId dependentPluginId) {
    return dependentPluginId.getIdString().startsWith(MODULE_DEPENDENCY_PREFIX);
  }

  /**
   * This is an internal method, use {@link PluginException#createByClass(String, Throwable, Class)} instead.
   */
  @ApiStatus.Internal
  @NotNull
  public static PluginException createPluginException(@NotNull String errorMessage, @Nullable Throwable cause,
                                                      @NotNull Class<?> pluginClass) {
    ClassLoader classLoader = pluginClass.getClassLoader();
    PluginId pluginId = classLoader instanceof PluginClassLoader ? ((PluginClassLoader)classLoader).getPluginId()
                                                                 : getPluginByClassName(pluginClass.getName());
    return new PluginException(errorMessage, cause, pluginId);
  }

  @Nullable
  public static PluginId getPluginByClassName(@NotNull String className) {
    final PluginId id = getPluginOrPlatformByClassName(className);
    return id == null || CORE_PLUGIN_ID.equals(id.getIdString()) ? null : id;
  }

  @Nullable
  public static PluginId getPluginOrPlatformByClassName(@NotNull String className) {
    if (className.startsWith("java.") ||
        className.startsWith("javax.") ||
        className.startsWith("kotlin.") ||
        className.startsWith("groovy.")) {
      return null;
    }
    IdeaPluginDescriptor result = null;
    for (IdeaPluginDescriptor o : getPlugins()) {
      if (!hasLoadedClass(className, o.getPluginClassLoader())) continue;
      result = o;
      break;
    }
    if (result == null) return null;

    // return if the found plugin is not "core" or the package is obviously "core"
    if (!result.getPluginId().getIdString().equals(CORE_PLUGIN_ID) ||
        className.startsWith("com.jetbrains.") || className.startsWith("org.jetbrains.") ||
        className.startsWith("com.intellij.") || className.startsWith("org.intellij.") ||
        className.startsWith("com.android.") ||
        className.startsWith("git4idea.") || className.startsWith("org.angularjs.")) {
      return result.getPluginId();
    }
    // otherwise we need to check plugins with use-idea-classloader="true"
    String root = PathManager.getResourceRoot(result.getPluginClassLoader(), "/" + className.replace('.', '/') + ".class");
    if (root == null) return null;
    for (IdeaPluginDescriptor o : getPlugins()) {
      if (!o.getUseIdeaClassLoader()) continue;
      File path = o.getPath();
      String pluginPath = path == null ? null : FileUtilRt.toSystemIndependentName(path.getPath());
      if (pluginPath == null || !root.startsWith(pluginPath)) continue;
      result = o;
      break;
    }
    return result.getPluginId();
  }

  private static boolean hasLoadedClass(@NotNull String className, ClassLoader loader) {
    if (loader instanceof UrlClassLoader) {
      return ((UrlClassLoader)loader).hasLoadedClass(className);
    }

    // it can be an UrlClassLoader loaded by another class loader, so instanceof doesn't work
    try {
      return ((Boolean)loader.getClass().getMethod("hasLoadedClass", String.class).invoke(loader, className)).booleanValue();
    }
    catch (Exception e) {
      return false;
    }
  }

  /**
   * In 191.* and earlier builds Java plugin was part of the platform, so any plugin installed in IntelliJ IDEA might be able to use its
   * classes without declaring explicit dependency on the Java module. This method is intended to add implicit dependency on the Java plugin
   * for such plugins to avoid breaking compatibility with them.
   */
  @Nullable
  private static PluginId getImplicitDependency(@NotNull IdeaPluginDescriptor descriptor,
                                                @NotNull Map<PluginId, ? extends IdeaPluginDescriptor> idMap) {
    String id = descriptor.getPluginId().getIdString();
    // Skip our plugins as expected to be up-to-date whether bundled or not.
    if (id.equals(CORE_PLUGIN_ID) || VENDOR_JETBRAINS.equals(descriptor.getVendor())) {
      return null;
    }
    if (!idMap.containsKey(PluginId.getId(ALL_MODULES_MARKER))) {
      return null;
    }
    PluginId javaId = PluginId.getId("com.intellij.modules.java");
    if (!idMap.containsKey(javaId)) {
      return null;
    }

    // If a plugin does not include any module dependency tags in its plugin.xml, it's assumed to be a legacy plugin
    // and is loaded only in IntelliJ IDEA, so it may use classes from Java plugin.
    boolean isLegacyPlugin = !hasModuleDependencies(descriptor);
    // Many custom plugins use classes from Java plugin and don't declare a dependency on the Java module (although they declare dependency
    // on some other platform modules). This is definitely a misconfiguration but lets temporary add the Java plugin to their dependencies
    // to avoid breaking compatibility.
    boolean isCustomPlugin = !descriptor.isBundled();
    return isLegacyPlugin || isCustomPlugin ? javaId : null;
  }

  private static boolean hasModuleDependencies(@NotNull IdeaPluginDescriptor descriptor) {
    for (PluginId depId : descriptor.getDependentPluginIds()) {
      if (isModuleDependency(depId)) {
        return true;
      }
    }
    return false;
  }

  private static boolean shouldLoadPlugins() {
    try {
      // no plugins during bootstrap
      Class.forName("com.intellij.openapi.extensions.Extensions");
    }
    catch (ClassNotFoundException e) {
      return false;
    }
    String loadPlugins = System.getProperty("idea.load.plugins");
    return loadPlugins == null || Boolean.TRUE.toString().equals(loadPlugins);
  }

  @Nullable
  private static ClassLoader createPluginClassLoader(@NotNull List<Path> classPath,
                                                     @NotNull ClassLoader[] parentLoaders,
                                                     @NotNull IdeaPluginDescriptorImpl descriptor) {
    if (isUnitTestMode && !ourUnitTestWithBundledPlugins) {
      return null;
    }

    if (descriptor.getUseIdeaClassLoader()) {
      getLogger().warn(descriptor.getPluginId() + " uses deprecated `use-idea-classloader` attribute");
      ClassLoader loader = PluginManagerCore.class.getClassLoader();
      try {
        // `UrlClassLoader#addURL` can't be invoked directly, because the core classloader is created at bootstrap in a "lost" branch
        MethodHandle addURL = MethodHandles.lookup().findVirtual(loader.getClass(), "addURL", MethodType.methodType(void.class, URL.class));
        for (Path pathElement : classPath) {
          addURL.invoke(loader, classpathElementToUrl(pathElement, descriptor));
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
        urls.add(classpathElementToUrl(pathElement, descriptor));
      }
      return new PluginClassLoader(urls, parentLoaders, descriptor.getPluginId(), descriptor.getVersion(), descriptor.getPluginPath());
    }
  }

  @NotNull
  private static URL classpathElementToUrl(Path cpElement, IdeaPluginDescriptor descriptor) {
    try {
      // it is important not to have traversal elements in classpath
      return cpElement.normalize().toUri().toURL();
    }
    catch (MalformedURLException e) {
      throw new PluginException("Corrupted path element: `" + cpElement + '`', e, descriptor.getPluginId());
    }
  }

  public static synchronized void invalidatePlugins() {
    ourPlugins = null;
    ourLoadedPlugins = null;
    ourDisabledPlugins = null;
  }

  public static boolean isPluginClass(@NotNull String className) {
    return ourPlugins != null && getPluginByClassName(className) != null;
  }

  private static void logPlugins(@NotNull List<? extends IdeaPluginDescriptor> plugins) {
    List<String> bundled = new ArrayList<>();
    List<String> disabled = new ArrayList<>();
    List<String> custom = new ArrayList<>();

    for (IdeaPluginDescriptor descriptor : plugins) {
      String version = descriptor.getVersion();
      String s = descriptor.getName() + (version != null ? " (" + version + ")" : "");

      if (!descriptor.isEnabled()) {
        disabled.add(s);
      }
      else if (SPECIAL_IDEA_PLUGIN_ID == descriptor.getPluginId() || descriptor.isBundled()) {
        bundled.add(s);
      }
      else {
        custom.add(s);
      }
    }

    bundled.sort(null);
    custom.sort(null);
    disabled.sort(null);

    Logger logger = getLogger();
    logger.info("Loaded bundled plugins: " + StringUtil.join(bundled, ", "));
    if (!custom.isEmpty()) {
      logger.info("Loaded custom plugins: " + StringUtil.join(custom, ", "));
    }
    if (!disabled.isEmpty()) {
      logger.info("Disabled plugins: " + StringUtil.join(disabled, ", "));
    }
  }

  @NotNull
  private static Collection<ClassLoader> getParentLoaders(@NotNull IdeaPluginDescriptor rootDescriptor, @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idMap) {
    if (isUnitTestMode && !ourUnitTestWithBundledPlugins) {
      return Collections.emptyList();
    }

    Set<ClassLoader> loaders = new LinkedHashSet<>();
    processAllDependencies(rootDescriptor, true, idMap, descriptor -> {
      ClassLoader loader = descriptor.getPluginClassLoader();
      if (loader == null) {
        getLogger().error("Plugin " + toPresentableName(rootDescriptor) + " requires missing class loader for " + toPresentableName(descriptor));
      }
      else {
        loaders.add(loader);
      }
      return FileVisitResult.CONTINUE;
    });
    return loaders;
  }

  public static boolean isRunningFromSources() {
    return Holder.ourIsRunningFromSources;
  }

  private static void prepareLoadingPluginsErrorMessage(@NotNull List<String> errors) {
    prepareLoadingPluginsErrorMessage(errors, Collections.emptyList());
  }

  private static void prepareLoadingPluginsErrorMessage(@NotNull List<String> errors, @NotNull List<String> actions) {
    if (errors.isEmpty()) return;
    Application app = ApplicationManager.getApplication();
    String title = "Problems found loading plugins:";

    if (app == null || !app.isHeadlessEnvironment() || isUnitTestMode) {
      String errorMessage = StringUtil.join(JBIterable.from(errors).map(o -> o + ".").append(actions), "<p/>");
      if (ourPluginError == null) {
        ourPluginError = errorMessage;
      }
      else {
        ourPluginError += "<p/>\n" + errorMessage;
      }
      getLogger().warn(StringUtil.join(JBIterable.of(title).append(errors), "\n"));
    }
    else {
      getLogger().error(StringUtil.join(JBIterable.of(title).append(errors), "\n"));
    }
  }

  @NotNull
  private static Graph<IdeaPluginDescriptor> createPluginIdGraph(@NotNull Map<PluginId, ? extends IdeaPluginDescriptor> idToDescriptorMap) {
    List<IdeaPluginDescriptor> nodes = new ArrayList<>(idToDescriptorMap.size());
    Set<IdeaPluginDescriptor> uniqueCheck = new HashSet<>();
    idToDescriptorMap.forEach((id, descriptor) -> {
      if (uniqueCheck.add(descriptor)) {
        nodes.add(descriptor);
      }
    });

    // this magic ensures that the dependent plugins always follow their dependencies in lexicographic order
    // needed to make sure that extensions are always in the same order
    nodes.sort(Comparator.comparing(PluginDescriptor::getPluginId));

    return GraphGenerator.generate(CachingSemiGraph.cache(new InboundSemiGraph<IdeaPluginDescriptor>() {
      @NotNull
      @Override
      public Collection<IdeaPluginDescriptor> getNodes() {
        return nodes;
      }

      @NotNull
      @Override
      public Iterator<IdeaPluginDescriptor> getIn(@NotNull IdeaPluginDescriptor rootDescriptor) {
        PluginId[] dependentPluginIds = rootDescriptor.getDependentPluginIds();
        PluginId implicitDepId = getImplicitDependency(rootDescriptor, idToDescriptorMap);
        IdeaPluginDescriptor implicitDep = implicitDepId == null ? null : idToDescriptorMap.get(implicitDepId);
        if (dependentPluginIds.length == 0) {
          return implicitDep == null ? Collections.emptyIterator() : Collections.singletonList(implicitDep).iterator();
        }

        List<IdeaPluginDescriptor> plugins = new ArrayList<>(dependentPluginIds.length + (implicitDep == null ? 0 : 1));
        if (implicitDep != null) {
          if (rootDescriptor == implicitDep) {
            getLogger().error("Plugin " + rootDescriptor +  " depends on self");
          }
          else {
            plugins.add(implicitDep);
          }
        }

        for (PluginId dependentPluginId : dependentPluginIds) {
          // check for missing optional dependency
          IdeaPluginDescriptor dep = idToDescriptorMap.get(dependentPluginId);
          // if 'dep' refers to a module we need to check the real plugin containing this module only if it's still enabled,
          // otherwise the graph will be inconsistent
          if (dep == null) {
            continue;
          }

          // ultimate plugin it is combined plugin, where some included XML can define dependency on ultimate explicitly and for now not clear,
          // can be such requirements removed or not
          if (rootDescriptor == dep) {
            if (dep.getPluginId() == SPECIAL_IDEA_PLUGIN_ID) {
              getLogger().error("Plugin " + rootDescriptor + " depends on self");
            }
          }
          else {
            plugins.add(dep);
          }
        }
        return plugins.iterator();
      }
    }));
  }

  private static void checkPluginCycles(@NotNull Map<PluginId, ? extends IdeaPluginDescriptor> idToDescriptorMap,
                                        @NotNull List<? super String> errors) {
    Graph<IdeaPluginDescriptor> graph = createPluginIdGraph(idToDescriptorMap);
    DFSTBuilder<IdeaPluginDescriptor> builder = new DFSTBuilder<>(graph);
    if (builder.isAcyclic()) {
      return;
    }

    StringBuilder cyclePresentation = new StringBuilder();
    for (Collection<IdeaPluginDescriptor> component : builder.getComponents()) {
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
      errors.add("Plugins should not have cyclic dependencies: " + cyclePresentation);
    }
  }

  @Nullable
  private static IdeaPluginDescriptorImpl loadDescriptorFromDir(@NotNull Path file,
                                                                @NotNull String pathName,
                                                                @Nullable Path pluginPath,
                                                                @NotNull LoadingContext loadingContext) {
    Path descriptorFile = file.resolve(META_INF + pathName);
    if (!Files.exists(descriptorFile)) {
      return null;
    }

    try {
      IdeaPluginDescriptorImpl descriptor = new IdeaPluginDescriptorImpl(notNull(pluginPath, file), loadingContext.isBundled);
      SafeJdomFactory xmlFactory = loadingContext.getXmlFactory();
      descriptor.readExternal(JDOMUtil.load(descriptorFile, xmlFactory), descriptorFile.getParent(), isUnitTestMode, loadingContext.pathResolver,
                              xmlFactory == null ? null : xmlFactory.stringInterner(),
                              loadingContext.disabledPlugins);
      return descriptor;
    }
    catch (SerializationException | JDOMException | IOException e) {
      if (loadingContext.isEssential) ExceptionUtil.rethrow(e);
      getLogger().warn("Cannot load " + descriptorFile, e);
      prepareLoadingPluginsErrorMessage(Collections.singletonList("File '" + file.getFileName() + "' contains invalid plugin descriptor"));
    }
    catch (Throwable e) {
      if (loadingContext.isEssential) ExceptionUtil.rethrow(e);
      getLogger().warn("Cannot load " + descriptorFile, e);
    }
    return null;
  }

  @Nullable
  private static IdeaPluginDescriptorImpl loadDescriptorFromJar(@NotNull Path file,
                                                                @NotNull String fileName,
                                                                @NotNull PathBasedJdomXIncluder.PathResolver<?> pathResolver,
                                                                @NotNull LoadingContext context,
                                                                @Nullable Path pluginPath) {
    SafeJdomFactory factory = context.getXmlFactory();
    try {
      Path metaInf = context.open(file).getPath("/META-INF");
      Element element;
      try {
        element = JDOMUtil.load(metaInf.resolve(fileName), factory);
      }
      catch (NoSuchFileException ignore) {
        return null;
      }

      IdeaPluginDescriptorImpl descriptor = new IdeaPluginDescriptorImpl(notNull(pluginPath, file), context.isBundled);
      Interner<String> interner = factory == null ? null : factory.stringInterner();
      descriptor.readExternal(element, metaInf, isUnitTestMode, pathResolver, interner, context.disabledPlugins);
      context.lastZipWithDescriptor = file;
      return descriptor;
    }
    catch (SerializationException | InvalidDataException e) {
      if (context.isEssential) ExceptionUtil.rethrow(e);
      getLogger().info("Cannot load " + file + "!/META-INF/" + fileName, e);
      prepareLoadingPluginsErrorMessage(Collections.singletonList("File '" + file.getFileName() + "' contains invalid plugin descriptor"));
    }
    catch (Throwable e) {
      if (context.isEssential) {
        ExceptionUtil.rethrow(e);
      }
      getLogger().info("Cannot load " + file + "!/META-INF/" + fileName, e);
    }

    return null;
  }

  /**
   * @deprecated Use {@link #loadDescriptor(Path, String)}
   */
  @Nullable
  @Deprecated
  public static IdeaPluginDescriptorImpl loadDescriptor(@NotNull File file, @NotNull String fileName) {
    return loadDescriptor(file.toPath(), fileName, disabledPlugins());
  }

  @Nullable
  public static IdeaPluginDescriptorImpl loadDescriptor(@NotNull Path file, @NotNull String fileName) {
    return loadDescriptor(file, fileName, disabledPlugins());
  }

  @Nullable
  public static IdeaPluginDescriptorImpl loadDescriptor(@NotNull Path file, @NotNull String fileName, @Nullable Set<String> disabledPlugins) {
    try (LoadingContext context = new LoadingContext(null, false, false, disabledPlugins)) {
      return loadDescriptorFromFileOrDir(file, fileName, context, Files.isDirectory(file));
    }
  }

  @Nullable
  private static IdeaPluginDescriptorImpl loadDescriptor(@NotNull Path file,
                                                         boolean bundled,
                                                         @Nullable Set<String> disabledPlugins,
                                                         @Nullable LoadDescriptorsContext parentContext) {
    try (LoadingContext context = new LoadingContext(parentContext, bundled, false, disabledPlugins)) {
      return loadDescriptorFromFileOrDir(file, PLUGIN_XML, context, Files.isDirectory(file));
    }
  }

  @Nullable
  private static IdeaPluginDescriptorImpl loadDescriptorFromFileOrDir(@NotNull Path file, @NotNull String pathName, @NotNull LoadingContext context, boolean isDirectory) {
    if (isDirectory) {
      return loadDescriptorFromDirAndNormalize(file, pathName, context);
    }
    else if (StringUtilRt.endsWithIgnoreCase(file.getFileName().toString(), ".jar")) {
      IdeaPluginDescriptorImpl descriptor = loadDescriptorFromJar(file, pathName, context.pathResolver, context, null);
      return descriptor == null || !normalizeDescriptor(file, pathName, context, descriptor, false) ? null : descriptor;
    }
    else {
      return null;
    }
  }

  @Nullable
  private static IdeaPluginDescriptorImpl loadDescriptorFromDirAndNormalize(@NotNull Path file,
                                                                            @NotNull String pathName,
                                                                            @NotNull LoadingContext context) {
    IdeaPluginDescriptorImpl descriptor = loadDescriptorFromDir(file, pathName, null, context);
    if (descriptor != null) {
      return normalizeDescriptor(file, pathName, context, descriptor, true) ? descriptor : null;
    }

    List<Path> files;
    try (DirectoryStream<Path> s = Files.newDirectoryStream(file.resolve("lib"))) {
      files = ContainerUtil.collect(s.iterator());
    }
    catch (IOException e) {
      return null;
    }

    if (files.isEmpty()) {
      return null;
    }

    putMoreLikelyPluginJarsFirst(file, files);

    List<Path> pluginJarFiles = null;
    for (Path childFile : files) {
      if (Files.isDirectory(childFile)) {
        IdeaPluginDescriptorImpl otherDescriptor = loadDescriptorFromDir(childFile, pathName, file, context);
        if (otherDescriptor != null) {
          if (descriptor != null) {
            getLogger().info("Cannot load " + file + " because two or more plugin.xml's detected");
            return null;
          }
          descriptor = otherDescriptor;
        }
      }
      else {
        String path = childFile.toString();
        if (StringUtilRt.endsWithIgnoreCase(path, ".jar") || StringUtilRt.endsWithIgnoreCase(path, ".zip")) {
          if (files.size() == 1) {
            pluginJarFiles = Collections.singletonList(childFile);
          }
          else {
            if (pluginJarFiles == null) {
              pluginJarFiles = new ArrayList<>();
            }
            //noinspection ConstantConditions
            pluginJarFiles.add(childFile);
          }
        }
      }
    }

    if (pluginJarFiles != null) {
      PluginXmlPathResolver pathResolver = new PluginXmlPathResolver(pluginJarFiles, context);
      for (Path jarFile : pluginJarFiles) {
        descriptor = loadDescriptorFromJar(jarFile, pathName, pathResolver, context, file);
        if (descriptor != null) {
          break;
        }
      }
    }
    return descriptor == null || !normalizeDescriptor(file, pathName, context, descriptor, true) ? null : descriptor;
  }

  private static boolean normalizeDescriptor(@NotNull Path file,
                                             @NotNull String pathName,
                                             @NotNull LoadingContext context,
                                             @NotNull IdeaPluginDescriptorImpl descriptor,
                                             boolean isDirectory) {
    if (PLUGIN_XML.equals(pathName) && (descriptor.getPluginId() == null || descriptor.getName() == null)) {
      getLogger().info("Cannot load descriptor from " + file + ": ID or name missing");
      prepareLoadingPluginsErrorMessage(Collections.singletonList("'" + file.getFileName() + "' contains invalid plugin descriptor"));
      return false;
    }

    Map<PluginId, List<String>> optionalConfigs = descriptor.getOptionalConfigs();
    if (optionalConfigs == null) {
      return true;
    }

    List<AbstractMap.SimpleEntry<String, IdeaPluginDescriptorImpl>> visitedFiles = context.getVisitedFiles();
    visitedFiles.add(new AbstractMap.SimpleEntry<>(pathName, descriptor));
    // try last file that had the descriptor that worked
    // JDOMXIncluder can find included descriptor files via classloading in URLUtil.openResourceStream
    // and here code supports the same behavior.
    // Note that this code is meant for IDE development / testing purposes

    Map<PluginId, List<IdeaPluginDescriptorImpl>> descriptors = new LinkedHashMap<>(optionalConfigs.size());
    loop:
    for (Map.Entry<PluginId, List<String>> entry : optionalConfigs.entrySet()) {
      for (String configFile : entry.getValue()) {
        for (int i = 0, size = visitedFiles.size(); i < size; i++) {
          AbstractMap.SimpleEntry<String, IdeaPluginDescriptorImpl> visitedFile = visitedFiles.get(i);
          if (visitedFile.getKey().equals(configFile)) {
            List<AbstractMap.SimpleEntry<String, IdeaPluginDescriptorImpl>> cycle = visitedFiles.subList(i, visitedFiles.size());
            getLogger().info("Plugin " + toPresentableName(visitedFiles.get(0).getValue()) + " optional descriptors form a cycle: " +
                             StringUtil.join(cycle, o -> o.getKey(), ", "));
            continue loop;
          }
        }

        IdeaPluginDescriptorImpl optionalDescriptor = null;
        // try last file that had the descriptor that worked
        if (context.lastZipWithDescriptor != null) {
          optionalDescriptor = loadDescriptorFromFileOrDir(context.lastZipWithDescriptor, configFile, context, false);
        }

        if (optionalDescriptor == null) {
          optionalDescriptor = loadDescriptorFromFileOrDir(file, configFile, context, isDirectory);
        }

        if (optionalDescriptor == null && (isDirectory || resolveDescriptorsInResources())) {
          // JDOMXIncluder can find included descriptor files via classloading in URLUtil.openResourceStream
          // and here code supports the same behavior.
          // Note that this code is meant for IDE development / testing purposes
          URL resource = PluginManagerCore.class.getClassLoader().getResource(META_INF + configFile);
          if (resource != null) {
            try (LoadingContext childContext = context.copy(/* isEssential = */ false)) {
              optionalDescriptor = loadDescriptorFromResource(resource, configFile, childContext);
            }
          }
        }

        if (optionalDescriptor == null) {
          getLogger().info("Plugin " + toPresentableName(descriptor) + " misses optional descriptor " + configFile);
        }
        else {
          ContainerUtilRt.putValue(entry.getKey(), optionalDescriptor, descriptors);
        }
      }
    }
    descriptor.setOptionalDescriptors(descriptors);

    visitedFiles.remove(visitedFiles.size() - 1);
    return true;
  }

  private static boolean resolveDescriptorsInResources() {
    return System.getProperty("resolve.descriptors.in.resources") != null;
  }

  /*
   * Sort the files heuristically to load the plugin jar containing plugin descriptors without extra ZipFile accesses
   * File name preference:
   * a) last order for files with resources in name, like resources_en.jar
   * b) last order for files that have -digit suffix is the name e.g. completion-ranking.jar is before gson-2.8.0.jar or junit-m5.jar
   * c) jar with name close to plugin's directory name, e.g. kotlin-XXX.jar is before allopen-XXX.jar
   * d) shorter name, e.g. android.jar is before android-base-common.jar
   */
  private static void putMoreLikelyPluginJarsFirst(@NotNull Path pluginDir, @NotNull List<Path> filesInLibUnderPluginDir) {
    String pluginDirName = pluginDir.getFileName().toString();

    filesInLibUnderPluginDir.sort((o1, o2) -> {
      String o2Name = o2.getFileName().toString();
      String o1Name = o1.getFileName().toString();

      boolean o2StartsWithResources = o2Name.startsWith("resources");
      boolean o1StartsWithResources = o1Name.startsWith("resources");
      if (o2StartsWithResources != o1StartsWithResources) {
        return o2StartsWithResources ? -1 : 1;
      }

      boolean o2IsVersioned = fileNameIsLikeVersionedLibraryName(o2Name);
      boolean o1IsVersioned = fileNameIsLikeVersionedLibraryName(o1Name);
      if (o2IsVersioned != o1IsVersioned) {
        return o2IsVersioned ? -1 : 1;
      }

      boolean o2StartsWithNeededName = StringUtil.startsWithIgnoreCase(o2Name, pluginDirName);
      boolean o1StartsWithNeededName = StringUtil.startsWithIgnoreCase(o1Name, pluginDirName);
      if (o2StartsWithNeededName != o1StartsWithNeededName) {
        return o2StartsWithNeededName ? 1 : -1;
      }

      return o1Name.length() - o2Name.length();
    });
  }

  private static boolean fileNameIsLikeVersionedLibraryName(@NotNull String name) {
    int i = name.lastIndexOf('-');
    if (i == -1) return false;
    if (i + 1 < name.length()) {
      char c = name.charAt(i + 1);
      if (Character.isDigit(c)) return true;
      return (c == 'm' || c == 'M') && i + 2 < name.length() && Character.isDigit(name.charAt(i + 2));
    }
    return false;
  }

  private static void loadDescriptorsFromDir(@NotNull Path dir,
                                             @NotNull List<IdeaPluginDescriptorImpl> result,
                                             boolean bundled,
                                             @NotNull LoadDescriptorsContext context) throws ExecutionException, InterruptedException {
    List<Future<IdeaPluginDescriptorImpl>> tasks = new ArrayList<>();
    ExecutorService executorService = context.getExecutorService();
    try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(dir)) {
      for (Path file : dirStream) {
        tasks.add(executorService.submit(() -> loadDescriptor(file, bundled, context.disabledPlugins, context)));
      }
    }
    catch (IOException ignore) {
      return;
    }

    Set<IdeaPluginDescriptor> uniqueCheck = new THashSet<>(result);
    for (Future<IdeaPluginDescriptorImpl> task : tasks) {
      IdeaPluginDescriptorImpl descriptor = task.get();
      if (descriptor == null) {
        continue;
      }

      if (uniqueCheck.add(descriptor)) {
        result.add(descriptor);
      }
      else {
        int prevIndex = result.indexOf(descriptor);
        IdeaPluginDescriptor prevDescriptor = result.get(prevIndex);
        boolean compatible = isCompatible(descriptor);
        boolean prevCompatible = isCompatible(prevDescriptor);
        boolean newer = VersionComparatorUtil.compare(descriptor.getVersion(), prevDescriptor.getVersion()) > 0;
        if (compatible && !prevCompatible || compatible == prevCompatible && newer) {
          result.set(prevIndex, descriptor);
          getLogger().info(descriptor.getPath() + " overrides " + prevDescriptor.getPath());
        }
      }
    }
  }

  private static void prepareLoadingPluginsErrorMessage(@NotNull Map<PluginId, String> disabledIds,
                                                        @NotNull Set<PluginId> disabledRequiredIds,
                                                        @NotNull Map<PluginId, ? extends IdeaPluginDescriptor> idMap,
                                                        @NotNull List<String> errors) {
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
  public static List<? extends IdeaPluginDescriptor> testLoadDescriptorsFromClassPath(@NotNull ClassLoader loader)
    throws ExecutionException, InterruptedException {
    List<IdeaPluginDescriptorImpl> descriptors = new SmartList<>();
    LinkedHashMap<URL, String> urlsFromClassPath = new LinkedHashMap<>();
    URL platformPluginURL = computePlatformPluginUrlAndCollectPluginUrls(loader, urlsFromClassPath);
    try (LoadingContext loadingContext = new LoadingContext(new LoadDescriptorsContext(false, null), true, true, null, new ClassPathXmlPathResolver(loader))) {
      loadDescriptorsFromClassPath(urlsFromClassPath, descriptors, loadingContext, platformPluginURL);
    }
    return descriptors;
  }

  @TestOnly
  public static List<? extends IdeaPluginDescriptor> testLoadDescriptorsFromDir(@NotNull Path dir)
    throws ExecutionException, InterruptedException {
    List<IdeaPluginDescriptorImpl> descriptors = new ArrayList<>();
    loadDescriptorsFromDir(dir, descriptors, true, new LoadDescriptorsContext(false, null));
    return descriptors;
  }

  private static void loadDescriptorsFromClassPath(@NotNull Map<URL, String> urls,
                                                   @NotNull List<IdeaPluginDescriptorImpl> result,
                                                   @NotNull LoadingContext loadingContext,
                                                   @Nullable URL platformPluginURL) throws ExecutionException, InterruptedException {
    if (urls.isEmpty()) {
      return;
    }

    List<Future<IdeaPluginDescriptorImpl>> tasks = new ArrayList<>(urls.size());
    ExecutorService executorService = loadingContext.parentContext.getExecutorService();
    for (Map.Entry<URL, String> entry : urls.entrySet()) {
      URL url = entry.getKey();
      tasks.add(executorService.submit(() -> {
        return loadDescriptorFromResource(url, entry.getValue(), loadingContext.copy(url.equals(platformPluginURL)));
      }));
    }

    // plugin projects may have the same plugins in plugin path (sandbox or SDK) and on the classpath; latter should be ignored
    Set<IdeaPluginDescriptorImpl> existingResults = new THashSet<>(result);
    for (Future<IdeaPluginDescriptorImpl> task : tasks) {
      IdeaPluginDescriptorImpl descriptor = task.get();
      if (descriptor != null && existingResults.add(descriptor)) {
        descriptor.setUseCoreClassLoader(true);
        result.add(descriptor);
      }
    }
  }

  @Nullable
  private static URL computePlatformPluginUrlAndCollectPluginUrls(@NotNull ClassLoader loader, @NotNull Map<URL, String> urls) {
    String platformPrefix = System.getProperty(PlatformUtils.PLATFORM_PREFIX_KEY);
    URL result = null;
    if (platformPrefix != null) {
      String fileName = platformPrefix + "Plugin.xml";
      URL resource = loader.getResource(META_INF + fileName);
      if (resource != null) {
        urls.put(resource, fileName);
        result = resource;
      }
    }

    try {
      Enumeration<URL> enumeration = loader.getResources(PLUGIN_XML_PATH);
      while (enumeration.hasMoreElements()) {
        urls.put(enumeration.nextElement(), PLUGIN_XML);
      }
    }
    catch (IOException e) {
      getLogger().info(e);
    }

    return result;
  }

  @Nullable
  private static IdeaPluginDescriptorImpl loadDescriptorFromResource(@NotNull URL resource, @NotNull String pathName, @NotNull LoadingContext loadingContext) {
    try {
      Path file;
      if (URLUtil.FILE_PROTOCOL.equals(resource.getProtocol())) {
        file = Paths.get(StringUtil.trimEnd(FileUtilRt.toSystemIndependentName(urlToFile(resource).toString()), pathName)).getParent();
        return loadDescriptorFromFileOrDir(file, pathName, loadingContext, Files.isDirectory(file));
      }
      else if (URLUtil.JAR_PROTOCOL.equals(resource.getProtocol())) {
        String path = resource.getFile();
        file = urlToFile(path.substring(0, path.indexOf(URLUtil.JAR_SEPARATOR)));
        IdeaPluginDescriptorImpl descriptor = loadDescriptorFromJar(file, pathName, loadingContext.pathResolver, loadingContext, null);
        return descriptor == null || !normalizeDescriptor(file, pathName, loadingContext, descriptor, false) ? null : descriptor;
      }
      else {
        return null;
      }
    }
    catch (Throwable e) {
      if (loadingContext.isEssential) {
        ExceptionUtil.rethrow(e);
      }
      getLogger().info("Cannot load " + resource, e);
      return null;
    }
  }

  // work around corrupted URLs produced by File.getURL()
  private static Path urlToFile(@NotNull String url) throws URISyntaxException {
    try {
      return Paths.get(new URI(url));
    }
    catch (URISyntaxException e) {
      if (url.indexOf(' ') > 0) {
        return Paths.get(new URI(StringUtil.replace(url, " ", "%20")));
      }
      throw e;
    }
  }

  // work around corrupted URLs produced by File.getURL()
  private static Path urlToFile(URL url) throws URISyntaxException, MalformedURLException {
    try {
      return Paths.get(url.toURI());
    }
    catch (URISyntaxException e) {
      String str = url.toString();
      if (str.indexOf(' ') > 0) {
        return Paths.get(new URL(StringUtil.replace(str, " ", "%20")).toURI());
      }
      throw e;
    }
  }

  private static void loadDescriptorsFromProperty(@NotNull List<? super IdeaPluginDescriptorImpl> result,
                                                  @NotNull LoadDescriptorsContext context) {
    String pathProperty = System.getProperty(PROPERTY_PLUGIN_PATH);
    if (pathProperty == null) {
      return;
    }

    Set<String> disabledPlugins = disabledPlugins();
    for (StringTokenizer t = new StringTokenizer(pathProperty, File.pathSeparator + ","); t.hasMoreTokens(); ) {
      String s = t.nextToken();
      IdeaPluginDescriptorImpl ideaPluginDescriptor = loadDescriptor(Paths.get(s), false, disabledPlugins, context);
      if (ideaPluginDescriptor != null) {
        result.add(ideaPluginDescriptor);
      }
    }
  }

  @NotNull
  @ApiStatus.Internal
  public static List<IdeaPluginDescriptorImpl> loadDescriptors() {
    List<IdeaPluginDescriptorImpl> result = new ArrayList<>();
    LinkedHashMap<URL, String> urlsFromClassPath = new LinkedHashMap<>();
    ClassLoader classLoader = PluginManagerCore.class.getClassLoader();
    URL platformPluginURL = computePlatformPluginUrlAndCollectPluginUrls(classLoader, urlsFromClassPath);
    boolean parallel = SystemProperties.getBooleanProperty("parallel.pluginDescriptors.loading", true);
    try (LoadDescriptorsContext context = new LoadDescriptorsContext(parallel, disabledPlugins())) {
      try (LoadingContext loadingContext = new LoadingContext(context, /* isBundled = */ true, /* isEssential, doesn't matter = */ true,
                                                              context.disabledPlugins, new ClassPathXmlPathResolver(classLoader))) {
        loadDescriptorsFromClassPath(urlsFromClassPath, result, loadingContext, platformPluginURL);
      }

      loadDescriptorsFromDir(Paths.get(PathManager.getPluginsPath()), result, false, context);

      if (!isUnitTestMode) {
        loadDescriptorsFromDir(Paths.get(PathManager.getPreInstalledPluginsPath()), result, true, context);
      }

      loadDescriptorsFromProperty(result, context);

      if (isUnitTestMode && result.size() <= 1) {
        // We're running in unit test mode but the classpath doesn't contain any plugins; try to load bundled plugins anyway
        ourUnitTestWithBundledPlugins = true;
        loadDescriptorsFromDir(Paths.get(PathManager.getPreInstalledPluginsPath()), result, true, context);
      }
    }
    catch (InterruptedException | ExecutionException e) {
      ExceptionUtil.rethrow(e);
    }
    result.sort((o1, o2) -> Comparing.compare(String.valueOf(o1.getPluginId()), String.valueOf(o2.getPluginId())));
    return result;
  }

  private static void mergeOptionalConfigs(@NotNull List<IdeaPluginDescriptorImpl> enabledPlugins,
                                           @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idMap) {
    Predicate<PluginId> enabledCondition = depId -> {
      IdeaPluginDescriptorImpl dep = idMap.get(depId);
      return dep != null && dep.isEnabled();
    };
    for (IdeaPluginDescriptorImpl descriptor : enabledPlugins) {
      for (IdeaPluginDescriptorImpl dep : optionalDescriptorRecursively(descriptor, enabledCondition)) {
        boolean requiredDepMissing = false;
        for (PluginId depId : dep.getDependentPluginIds()) {
          if (!enabledCondition.test(depId) &&
              ArrayUtil.indexOf(dep.getOptionalDependentPluginIds(), depId) == -1) {
            requiredDepMissing = true;
            break;
          }
        }
        if (requiredDepMissing) continue;
        descriptor.mergeOptionalConfig(dep);
      }
    }
    for (IdeaPluginDescriptorImpl descriptor : enabledPlugins) {
      descriptor.setOptionalDescriptors(null);
    }
  }

  @ApiStatus.Internal
  public static void initClassLoader(@NotNull IdeaPluginDescriptorImpl descriptor,
                                     @NotNull ClassLoader coreLoader,
                                     @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idMap) {
    Collection<ClassLoader> parentLoaders = getParentLoaders(descriptor, idMap);
    if (parentLoaders.isEmpty()) {
      parentLoaders = Collections.singletonList(coreLoader);
    }
    descriptor.setLoader(createPluginClassLoader(descriptor.getClassPath(), parentLoaders.toArray(new ClassLoader[0]), descriptor));
  }

  static BuildNumber getBuildNumber() {
    return Holder.ourBuildNumber;
  }

  private static void disableIncompatiblePlugins(@NotNull JBTreeTraverser<PluginId> traverser,
                                                 @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idMap,
                                                 @NotNull Set<? super PluginId> brokenIds,
                                                 @NotNull List<? super String> errors) {
    String selectedIds = System.getProperty("idea.load.plugins.id");
    String selectedCategory = System.getProperty("idea.load.plugins.category");
    boolean shouldLoadPlugins = shouldLoadPlugins();

    Set<IdeaPluginDescriptor> allDescriptors = new LinkedHashSet<>(idMap.values());
    IdeaPluginDescriptorImpl coreDescriptor = notNull(idMap.get(PluginId.getId(CORE_PLUGIN_ID)));
    boolean checkModuleDependencies = !coreDescriptor.getModules().isEmpty() &&
                                      !coreDescriptor.getModules().contains(ALL_MODULES_MARKER);

    Set<PluginId> explicitlyEnabled = null;
    if (selectedIds != null) {
      HashSet<String> set = new HashSet<>(StringUtil.split(selectedIds, ","));
      set.addAll(((ApplicationInfoImpl)ApplicationInfoImpl.getShadowInstance()).getEssentialPluginsIds());
      explicitlyEnabled = JBIterable.from(allDescriptors)
        .map(IdeaPluginDescriptor::getPluginId)
        .filter(o -> set.contains(o.getIdString())).addAllTo(new LinkedHashSet<>());
    }
    else if (selectedCategory != null) {
      explicitlyEnabled = JBIterable.from(allDescriptors)
        .filter(o -> selectedCategory.equals(o.getCategory()))
        .map(IdeaPluginDescriptor::getPluginId)
        .addAllTo(new LinkedHashSet<>());
    }
    if (explicitlyEnabled != null) {
      // add all required dependencies
      traverser.withRoots(new ArrayList<>(explicitlyEnabled)).unique().traverse().addAllTo(explicitlyEnabled);
    }
    BuildNumber buildNumber = getBuildNumber();
    for (IdeaPluginDescriptor descriptor : allDescriptors) {
      String errorSuffix;
      if (descriptor == coreDescriptor) {
        errorSuffix = null;
      }
      else if (!descriptor.isEnabled()) {
        errorSuffix = "is not enabled";
      }
      else if (explicitlyEnabled != null) {
        if (!explicitlyEnabled.contains(descriptor.getPluginId())) {
          errorSuffix = "";
          getLogger().info("Plugin " + toPresentableName(descriptor) + " " +
                           (selectedIds != null
                            ? "is not in 'idea.load.plugins.id' system property"
                            : "category doesn't match 'idea.load.plugins.category' system property"));
        }
        else {
          errorSuffix = null;
        }
      }
      else if (!shouldLoadPlugins) {
        errorSuffix = "is skipped (plugins loading disabled)";
      }
      else if (checkModuleDependencies && !hasModuleDependencies(descriptor)) {
        // http://www.jetbrains.org/intellij/sdk/docs/basics/getting_started/plugin_compatibility.html
        // If a plugin does not include any module dependency tags in its plugin.xml,
        // it's assumed to be a legacy plugin and is loaded only in IntelliJ IDEA.
        errorSuffix = "defines no module dependencies (supported only in IntelliJ IDEA)";
      }
      else if (isDisabled(descriptor.getPluginId().getIdString())) {
        // do not log disabled plugins on each start
        errorSuffix = "";
      }
      else if (isIncompatible(buildNumber, descriptor.getSinceBuild(), descriptor.getUntilBuild()) != null) {
        String since = StringUtil.notNullize(descriptor.getSinceBuild(), "0.0");
        String until = StringUtil.notNullize(descriptor.getUntilBuild(), "*.*");
        errorSuffix = "is incompatible (target build " +
                      (since.equals(until) ? "is " + since
                                           : "range is " + since + " to " + until) + ")";
      }
      else if (isBrokenPlugin(descriptor)) {
        errorSuffix = "version was marked as incompatible";
        brokenIds.add(descriptor.getPluginId());
      }
      else {
        errorSuffix = null;
      }
      if (errorSuffix != null) {
        descriptor.setEnabled(false);
        if (StringUtil.isNotEmpty(errorSuffix)) {
          errors.add("Plugin " + toPresentableName(descriptor) + " " + errorSuffix);
        }
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

    String message = isIncompatible(buildNumber, descriptor.getSinceBuild(), descriptor.getUntilBuild());
    if (message != null) {
      getLogger().warn("Plugin " + toPresentableName(descriptor) + " is incompatible (" + message + ")");
      return true;
    }
    return false;
  }

  @Nullable
  static String isIncompatible(@NotNull BuildNumber buildNumber,
                               @Nullable String sinceBuild,
                               @Nullable String untilBuild) {
    try {
      String message = "";
      BuildNumber sinceBuildNumber = StringUtil.isEmpty(sinceBuild) ? null : BuildNumber.fromString(sinceBuild, null, null);
      if (sinceBuildNumber != null && sinceBuildNumber.compareTo(buildNumber) > 0) {
        message += "since build " + sinceBuildNumber + " > " + buildNumber;
      }

      BuildNumber untilBuildNumber = StringUtil.isEmpty(untilBuild) ? null : BuildNumber.fromString(untilBuild, null, null);
      if (untilBuildNumber != null && untilBuildNumber.compareTo(buildNumber) < 0) {
        if (!message.isEmpty()) message += ", ";
        message += "until build " + untilBuildNumber + " < " + buildNumber;
      }
      return StringUtil.nullize(message);
    }
    catch (Exception e) {
      getLogger().error(e);
      return "version check failed";
    }
  }

  private static void checkEssentialPluginsAreAvailable(@NotNull Collection<? extends IdeaPluginDescriptor> plugins) {
    if (isUnitTestMode) return;
    Set<String> available = ContainerUtil.map2Set(plugins, plugin -> plugin.getPluginId().getIdString());
    List<String> required = ((ApplicationInfoImpl)ApplicationInfoImpl.getShadowInstance()).getEssentialPluginsIds();

    List<String> missing = new SmartList<>();
    for (String id : required) {
      if (!available.contains(id)) {
        missing.add(id);
      }
    }
    if (!missing.isEmpty()) {
      throw new EssentialPluginMissingException(missing);
    }
  }

  @ApiStatus.Internal
  @NotNull
  static List<List<IdeaPluginDescriptorImpl>> initializePlugins(@NotNull List<IdeaPluginDescriptorImpl> descriptors,
                                                                @NotNull ClassLoader coreLoader,
                                                                @Nullable BiConsumer<? super Set<String>, ? super Set<String>> disabledAndPossibleToEnableConsumer) {
    List<String> errors = new ArrayList<>();
    Map<PluginId, IdeaPluginDescriptorImpl> idMap = buildPluginIdMap(descriptors, errors);

    Map<PluginId, String> disabledIds = new LinkedHashMap<>();
    Set<PluginId> disabledRequiredIds = new LinkedHashSet<>();
    Set<PluginId> brokenIds = new LinkedHashSet<>();
    Set<PluginId> enabledIds = new LinkedHashSet<>();

    JBTreeTraverser<PluginId> requiredDepsTraverser = new PluginTraverser(idMap, false, false);

    disableIncompatiblePlugins(requiredDepsTraverser, idMap, brokenIds, errors);
    checkPluginCycles(idMap, errors);

    // topological sort based on required dependencies only
    JBIterable<PluginId> sortedRequired = requiredDepsTraverser
      .withRoots(idMap.keySet())
      .unique()
      .traverse(TreeTraversal.POST_ORDER_DFS);

    // one plugin descriptor for several plugin ids (e.g. com.intellij.modules.xml for idea core), so, check by descriptor and not by id
    Set<IdeaPluginDescriptorImpl> uniqueCheck = new HashSet<>(sortedRequired.size());
    for (PluginId pluginId : sortedRequired) {
      IdeaPluginDescriptorImpl descriptor = idMap.get(pluginId);
      if (descriptor == null || !uniqueCheck.add(descriptor)) {
        continue;
      }

      boolean wasEnabled = descriptor.isEnabled();
      if (wasEnabled && computePluginEnabled(descriptor, enabledIds, idMap, disabledRequiredIds, errors)) {
        enabledIds.add(descriptor.getPluginId());
        for (String module : descriptor.getModules()) {
          enabledIds.add(PluginId.getId(module));
        }
      }
      else {
        descriptor.setEnabled(false);
        if (wasEnabled) {
          disabledIds.put(descriptor.getPluginId(), descriptor.getName());
        }
        descriptor.setLoader(createPluginClassLoader(descriptor.getClassPath(), new ClassLoader[]{coreLoader}, descriptor));
      }
    }
    uniqueCheck.clear();

    // topological sort based on all (required and optional) dependencies
    JBTreeTraverser<PluginId> allDepsTraverser = new PluginTraverser(idMap, true, false);
    JBIterable<PluginId> sortedAll = allDepsTraverser
      .withRoots(sortedRequired)
      .unique()
      .traverse(TreeTraversal.POST_ORDER_DFS);

    prepareLoadingPluginsErrorMessage(disabledIds, disabledRequiredIds, idMap, errors);

    IdeaPluginDescriptor coreDescriptor = idMap.get(PluginId.getId(CORE_PLUGIN_ID));

    List<IdeaPluginDescriptorImpl> allPlugins = new ArrayList<>(sortedAll.size());
    List<IdeaPluginDescriptorImpl> enabledPlugins = new ArrayList<>(sortedAll.size());
    for (PluginId id : sortedAll) {
      IdeaPluginDescriptorImpl descriptor = idMap.get(id);
      if (descriptor == null || !uniqueCheck.add(descriptor)) {
        continue;
      }

      allPlugins.add(descriptor);
      if (descriptor.isEnabled()) {
        enabledPlugins.add(descriptor);
        if (descriptor != coreDescriptor) {
          descriptor.insertDependency(coreDescriptor);
        }
      }
    }

    fixOptionalConfigs(enabledPlugins, idMap);
    mergeOptionalConfigs(enabledPlugins, idMap);

    for (IdeaPluginDescriptorImpl pluginDescriptor : enabledPlugins) {
      if (pluginDescriptor == coreDescriptor || pluginDescriptor.isUseCoreClassLoader()) {
        pluginDescriptor.setLoader(coreLoader);
      }
      else {
        initClassLoader(pluginDescriptor, coreLoader, idMap);
      }
    }
    if (disabledAndPossibleToEnableConsumer != null) {
      Set<String> disabledIdSet = new HashSet<>(disabledIds.size());
      for (PluginId id : disabledIds.keySet()) {
        disabledIdSet.add(id.getIdString());
      }
      Set<String> disabledRequiredIdSet = new HashSet<>(disabledRequiredIds.size());
      for (PluginId id : disabledRequiredIds) {
        disabledRequiredIdSet.add(id.getIdString());
      }
      disabledAndPossibleToEnableConsumer.accept(disabledIdSet, disabledRequiredIdSet);
    }
    return Arrays.asList(allPlugins, enabledPlugins);
  }

  @NotNull
  @ApiStatus.Internal
  public static Map<PluginId, IdeaPluginDescriptorImpl> buildPluginIdMap(@NotNull List<IdeaPluginDescriptorImpl> descriptors,
                                                                          @Nullable List<? super String> errors) {
    Map<PluginId, IdeaPluginDescriptorImpl> idMap = new LinkedHashMap<>(descriptors.size());
    Map<PluginId, List<IdeaPluginDescriptorImpl>> duplicateMap = null;
    for (IdeaPluginDescriptorImpl descriptor : descriptors) {
      PluginId id = descriptor.getPluginId();
      if (id == null) {
        if (errors != null) {
          errors.add("No id is provided by " + toPresentableName(descriptor.getPluginPath().getFileName().toString()));
        }
        continue;
      }

      Map<PluginId, List<IdeaPluginDescriptorImpl>> newDuplicateMap = checkAndPut(descriptor, id, idMap, duplicateMap);
      if (newDuplicateMap != null) {
        duplicateMap = newDuplicateMap;
        continue;
      }

      for (String module : descriptor.getModules()) {
        newDuplicateMap = checkAndPut(descriptor, PluginId.getId(module), idMap, duplicateMap);
        if (newDuplicateMap != null) {
          duplicateMap = newDuplicateMap;
        }
      }
    }

    if (errors != null) {
      if (duplicateMap != null) {
        duplicateMap.forEach((id, values) -> {
          if (isModuleDependency(id)) {
            errors.add(toPresentableName(id.getIdString()) + " module is declared by plugins " +
                       StringUtil.join(values, PluginManagerCore::toPresentableName, ", "));
          }
          else {
            errors.add(toPresentableName(id.getIdString()) + " id is declared by plugins " +
                       StringUtil.join(values, o -> toPresentableName(o.getPath().getName()), ", "));
          }
        });
      }

      if (!idMap.containsKey(PluginId.getId(CORE_PLUGIN_ID))) {
        String message = SPECIAL_IDEA_PLUGIN_ID.getIdString() + " (platform prefix: " + System.getProperty(PlatformUtils.PLATFORM_PREFIX_KEY) + ")";
        throw new EssentialPluginMissingException(Collections.singletonList(message));
      }
    }
    return idMap;
  }

  @Nullable
  private static Map<PluginId, List<IdeaPluginDescriptorImpl>> checkAndPut(@NotNull IdeaPluginDescriptorImpl descriptor,
                                                                           @NotNull PluginId id,
                                                                           @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idMap,
                                                                           @Nullable Map<PluginId, List<IdeaPluginDescriptorImpl>> duplicateMap) {
    if (duplicateMap != null && duplicateMap.containsKey(id)) {
      ContainerUtilRt.putValue(id, descriptor, duplicateMap);
      return duplicateMap;
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
    ContainerUtilRt.putValue(id, descriptor, duplicateMap);
    return duplicateMap;
  }

  private static boolean computePluginEnabled(@NotNull IdeaPluginDescriptor descriptor,
                                              @NotNull Set<PluginId> loadedIds,
                                              @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idMap,
                                              @NotNull Set<? super PluginId> disabledRequiredIds,
                                              @NotNull List<? super String> errors) {
    if (descriptor.getPluginId().getIdString().equals(CORE_PLUGIN_ID)) return true;
    boolean result = true;
    for (PluginId depId : descriptor.getDependentPluginIds()) {
      if (loadedIds.contains(depId) ||
          ArrayUtil.indexOf(descriptor.getOptionalDependentPluginIds(), depId) != -1) {
        continue;
      }
      result = false;
      if (descriptor.isImplementationDetail()) continue;
      IdeaPluginDescriptor dep = idMap.get(depId);
      if (dep != null && isDisabled(depId.getIdString())) {
        // broken/incompatible plugins can be updated, add them anyway
        disabledRequiredIds.add(dep.getPluginId());
      }
      String name = descriptor.getName();
      String depName = dep == null ? null : dep.getName();
      if (depName == null) {
        errors.add("Plugin " + toPresentableName(name) + " requires missing " + toPresentableName(depId.getIdString()));
      }
      else {
        errors.add("Plugin " + toPresentableName(name) + " requires disabled " + toPresentableName(depName));
      }
    }
    return result;
  }

  private static void fixOptionalConfigs(@NotNull List<IdeaPluginDescriptorImpl> enabledPlugins,
                                         @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idMap) {
    if (!isRunningFromSources()) return;
    for (IdeaPluginDescriptorImpl descriptor : enabledPlugins) {
      if (!descriptor.isUseCoreClassLoader() || descriptor.getOptionalDescriptors() == null) continue;
      descriptor.getOptionalDescriptors().entrySet().removeIf(entry -> {
        IdeaPluginDescriptorImpl dependent = idMap.get(entry.getKey());
        return dependent != null && !dependent.isUseCoreClassLoader();
      });
    }
  }

  private static String toPresentableName(@Nullable IdeaPluginDescriptor descriptor) {
    return toPresentableName(descriptor == null ? null : descriptor.getName());
  }

  @NotNull
  private static String toPresentableName(@Nullable String s) {
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
   * @param area       area which extension points and extensions should be registered (e.g. {@link Extensions#getRootArea()} for application-level extensions)
   */
  public static void registerExtensionPointAndExtensions(@NotNull Path pluginRoot, @NotNull String fileName, @NotNull ExtensionsArea area) {
    IdeaPluginDescriptorImpl descriptor;
    try (LoadingContext context = new LoadingContext(null, true, true, disabledPlugins())) {
      if (Files.isDirectory(pluginRoot)) {
        descriptor = loadDescriptorFromDir(pluginRoot, fileName, null, context);
      }
      else {
        descriptor = loadDescriptorFromJar(pluginRoot, fileName, PathBasedJdomXIncluder.DEFAULT_PATH_RESOLVER, context, null);
      }
    }

    if (descriptor != null) {
      descriptor.registerExtensionPoints((ExtensionsAreaImpl)area, ApplicationManager.getApplication());
      descriptor.registerExtensions((ExtensionsAreaImpl)area, ApplicationManager.getApplication(), false);
    }
    else {
      getLogger().error("Cannot load " + fileName + " from " + pluginRoot);
    }
  }

  private static synchronized void initPlugins(@Nullable ClassLoader coreLoader) {
    if (coreLoader == null) {
      Class<?> callerClass = ReflectionUtil.findCallerClass(1);
      assert callerClass != null;
      coreLoader = callerClass.getClassLoader();
    }

    List<IdeaPluginDescriptorImpl> result;
    try {
      Activity loadPluginsActivity = StartUpMeasurer.startActivity("plugin initialization");
      result = loadDescriptors();
      List<List<IdeaPluginDescriptorImpl>> lists = initializePlugins(result, coreLoader, (d, e) -> {
        ourPlugins2Disable = d;
        ourPlugins2Enable = e;
      });
      ourPlugins = lists.get(0).toArray(IdeaPluginDescriptorImpl.EMPTY_ARRAY);
      ourLoadedPlugins = lists.get(1);
      checkEssentialPluginsAreAvailable(ourLoadedPlugins);
      int count = 0;
      ourId2Index.ensureCapacity(ourLoadedPlugins.size());
      for (IdeaPluginDescriptor descriptor : ourLoadedPlugins) {
        ourId2Index.put(descriptor.getPluginId(), count++);
      }
      loadPluginsActivity.end();
      loadPluginsActivity.setDescription("plugin count: " + result.size());
    }
    catch (ExtensionInstantiationException e) {
      throw new PluginException(e, e.getExtensionOwnerId());
    }
    catch (RuntimeException e) {
      getLogger().error(e);
      throw e;
    }
    logPlugins(result);
  }

  @NotNull
  public static Logger getLogger() {
    return Logger.getInstance("#com.intellij.ide.plugins.PluginManager");
  }

  public static final class EssentialPluginMissingException extends RuntimeException {
    public final List<String> pluginIds;

    EssentialPluginMissingException(@NotNull List<String> ids) {
      super("Missing essential plugins: " + StringUtil.join(ids, ", "));
      pluginIds = ids;
    }
  }

  @Nullable
  public static IdeaPluginDescriptor getPlugin(@Nullable PluginId id) {
    if (id != null) {
      for (IdeaPluginDescriptor plugin : getPlugins()) {
        if (id == plugin.getPluginId()) {
          return plugin;
        }
      }
    }
    return null;
  }

  public static boolean isPluginInstalled(PluginId id) {
    return getPlugin(id) != null;
  }

  @NotNull
  private static List<IdeaPluginDescriptorImpl> optionalDescriptorRecursively(@NotNull IdeaPluginDescriptorImpl rootDescriptor,
                                                                              @Nullable Predicate<? super PluginId> condition) {
    Map<PluginId, List<IdeaPluginDescriptorImpl>> optionalDescriptors = rootDescriptor.getOptionalDescriptors();
    if (optionalDescriptors == null || optionalDescriptors.isEmpty()) {
      return Collections.emptyList();
    }

    List<IdeaPluginDescriptorImpl> result = new ArrayList<>();

    int start = 0;
    addOptionalDescriptors(rootDescriptor, result, condition);
    int end = result.size();
    do {
      for (int i = start; i < end; i++) {
        addOptionalDescriptors(result.get(i), result, condition);
      }

      start = end;
      end = result.size();
    }
    while (start != end);
    return result;
  }

  private static void addOptionalDescriptors(@NotNull IdeaPluginDescriptorImpl descriptor,
                                             @NotNull List<IdeaPluginDescriptorImpl> result,
                                             @Nullable Predicate<? super PluginId> condition) {
    Map<PluginId, List<IdeaPluginDescriptorImpl>> optionalDescriptors = descriptor.getOptionalDescriptors();
    if (optionalDescriptors == null) {
      return;
    }

    optionalDescriptors.forEach((id, descriptors) -> {
      if (condition != null && !condition.test(id)) {
        return;
      }

      result.addAll(descriptors);
    });
  }

  @NotNull
  @ApiStatus.Internal
  public static Map<PluginId, IdeaPluginDescriptorImpl> buildPluginIdMap() {
    LoadingState.COMPONENTS_REGISTERED.checkOccurred();
    return buildPluginIdMap(Arrays.asList(ourPlugins), null);
  }

  /**
   * You must not use this method in cycle, in this case use {@link #processAllDependencies(IdeaPluginDescriptor, boolean, Map, Function)} instead
   * (to reuse result of {@link #buildPluginIdMap()}).
   *
   * {@link FileVisitResult#SKIP_SIBLINGS} is not supported.
   *
   * Returns {@code false} if processing was terminated because of {@link FileVisitResult#TERMINATE}, and {@code true} otherwise.
   */
  @SuppressWarnings("UnusedReturnValue")
  @ApiStatus.Internal
  public static boolean processAllDependencies(@NotNull IdeaPluginDescriptor rootDescriptor,
                                            boolean withOptionalDeps,
                                            @NotNull Function<IdeaPluginDescriptor, FileVisitResult> consumer) {
    return processAllDependencies(rootDescriptor, withOptionalDeps, buildPluginIdMap(), consumer);
  }

  @ApiStatus.Internal
  public static boolean processAllDependencies(@NotNull IdeaPluginDescriptor rootDescriptor,
                                               boolean withOptionalDeps,
                                               @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idToMap,
                                               @NotNull Function<IdeaPluginDescriptor, FileVisitResult> consumer) {
    return processAllDependencies(rootDescriptor, withOptionalDeps, idToMap, new HashSet<>(), consumer);
  }

  @ApiStatus.Internal
  private static boolean processAllDependencies(@NotNull IdeaPluginDescriptor rootDescriptor,
                                               boolean withOptionalDeps,
                                               @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idToMap,
                                               @NotNull Set<IdeaPluginDescriptor> depProcessed,
                                               @NotNull Function<IdeaPluginDescriptor, FileVisitResult> consumer) {
    loop:
    for (PluginId id : rootDescriptor.getDependentPluginIds()) {
      IdeaPluginDescriptorImpl descriptor = idToMap.get(id);
      if (descriptor == null) {
        continue;
      }

      if (!withOptionalDeps) {
        for (PluginId otherId : rootDescriptor.getOptionalDependentPluginIds()) {
          if (otherId == id) {
            continue loop;
          }
        }
      }

      switch (consumer.apply(descriptor)) {
        case TERMINATE:
          return false;
        case CONTINUE:
          if (depProcessed.add(descriptor)) {
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

  private static final class PluginTraverser extends JBTreeTraverser<PluginId> {
    final Map<PluginId, IdeaPluginDescriptorImpl> idMap;

    PluginTraverser(@NotNull Map<PluginId, IdeaPluginDescriptorImpl> idMap,
                    boolean withOptionalDeps,
                    boolean convertModulesToPlugins) {
      super(Meta.create(o -> {
        IdeaPluginDescriptorImpl rootDescriptor = idMap.get(o);
        if (rootDescriptor == null) {
          return JBIterable.empty();
        }
        return JBIterable.from(getPluginIds(rootDescriptor, withOptionalDeps, convertModulesToPlugins, idMap));
      }));

      this.idMap = idMap;
    }

    @NotNull
    private static List<PluginId> getPluginIds(@NotNull IdeaPluginDescriptorImpl rootDescriptor,
                                               boolean withOptionalDeps,
                                               boolean convertModulesToPlugins,
                                               @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idMap) {
      Set<PluginId> uniqueCheck = new HashSet<>();
      List<PluginId> result = new ArrayList<>();

      if (withOptionalDeps) {
        for (PluginId id : rootDescriptor.getDependentPluginIds()) {
          addResult(idMap, convertModulesToPlugins, rootDescriptor, uniqueCheck, result, id);
        }
      }
      else {
        for (PluginId pluginId : rootDescriptor.getDependentPluginIds()) {
          Map<PluginId, List<IdeaPluginDescriptorImpl>> optionalDescriptors = rootDescriptor.getOptionalDescriptors();
          if (optionalDescriptors == null || !optionalDescriptors.containsKey(pluginId)) {
            addResult(idMap, convertModulesToPlugins, rootDescriptor, uniqueCheck, result, pluginId);
          }
        }
      }

      addResult(idMap, convertModulesToPlugins, rootDescriptor, uniqueCheck, result, getImplicitDependency(rootDescriptor, idMap));

      if (withOptionalDeps) {
        for (IdeaPluginDescriptorImpl d : optionalDescriptorRecursively(rootDescriptor, null)) {
          for (PluginId id : d.getDependentPluginIds()) {
            addResult(idMap, convertModulesToPlugins, rootDescriptor, uniqueCheck, result, id);
          }
        }
      }
      return result;
    }

    private static void addResult(@NotNull Map<PluginId, IdeaPluginDescriptorImpl> idMap,
                                  boolean convertModulesToPlugins,
                                  @NotNull IdeaPluginDescriptorImpl rootDescriptor,
                                  @NotNull Set<PluginId> uniqueCheck,
                                  @NotNull List<PluginId> result, PluginId id) {
      IdeaPluginDescriptor plugin = idMap.get(id);
      if (plugin == rootDescriptor) {
        return;
      }

      PluginId finalId = plugin != null && convertModulesToPlugins && isModuleDependency(id) ? plugin.getPluginId() : id;
      if (uniqueCheck.add(finalId)) {
        result.add(finalId);
      }
    }

    private PluginTraverser(@NotNull Meta<PluginId> meta,
                            @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idMap) {
      super(meta);

      this.idMap = idMap;
    }

    @NotNull
    @Override
    protected JBTreeTraverser<PluginId> newInstance(@NotNull Meta<PluginId> meta) {
      return new PluginTraverser(meta, idMap);
    }
  }
}