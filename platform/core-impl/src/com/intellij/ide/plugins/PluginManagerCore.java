// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.ActivitySubNames;
import com.intellij.diagnostic.ParallelActivity;
import com.intellij.diagnostic.PluginException;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.JetBrainsProtocolHandler;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.components.ExtensionAreas;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.extensions.impl.PicoPluginExtensionInitializationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.serialization.SerializationException;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Interner;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.graph.*;
import com.intellij.util.io.URLUtil;
import com.intellij.util.lang.UrlClassLoader;
import com.intellij.util.text.VersionComparatorUtil;
import com.intellij.util.xmlb.JDOMXIncluder;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectIntHashMap;
import org.jdom.JDOMException;
import org.jetbrains.annotations.*;

import java.io.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.intellij.util.ObjectUtils.notNull;

public class PluginManagerCore {
  public static final String META_INF = "META-INF/";
  public static final String IDEA_IS_INTERNAL_PROPERTY = "idea.is.internal";

  public static final String DISABLED_PLUGINS_FILENAME = "disabled_plugins.txt";
  public static final String CORE_PLUGIN_ID = "com.intellij";
  public static final String PLUGIN_XML = "plugin.xml";
  public static final String PLUGIN_XML_PATH = META_INF + PLUGIN_XML;
  private static final String ALL_MODULES_MARKER = "com.intellij.modules.all";

  /** @noinspection StaticNonFinalField*/
  public static String BUILD_NUMBER;

  private static final TObjectIntHashMap<PluginId> ourId2Index = new TObjectIntHashMap<>();
  private static final String MODULE_DEPENDENCY_PREFIX = "com.intellij.module";
  private static final Map<String, IdeaPluginDescriptorImpl> ourModulesToContainingPlugins = new THashMap<>();
  private static final String SPECIAL_IDEA_PLUGIN = "IDEA CORE";
  private static final String PROPERTY_PLUGIN_PATH = "plugin.path";

  static final String DISABLE = "disable";
  static final String ENABLE = "enable";
  static final String EDIT = "edit";

  private static Set<String> ourDisabledPlugins;
  private static MultiMap<String, String> ourBrokenPluginVersions;
  private static final AtomicReference<IdeaPluginDescriptor[]> ourPlugins = new AtomicReference<>();
  private static List<IdeaPluginDescriptor> ourLoadedPlugins;

  @SuppressWarnings("StaticNonFinalField")
  public static volatile boolean isUnitTestMode = Boolean.getBoolean("idea.is.unit.test");

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private static boolean ourUnitTestWithBundledPlugins = Boolean.getBoolean("idea.run.tests.with.bundled.plugins");

  private static final String PLUGIN_IS_DISABLED_REASON = "Plugin is disabled";

  static String myPluginError;
  static List<String> myPlugins2Disable;
  static LinkedHashSet<String> myPlugins2Enable;

  private static class Holder {
    private static final BuildNumber ourBuildNumber = calcBuildNumber();
    private static final boolean ourIsRunningFromSources = new File(PathManager.getHomePath(), Project.DIRECTORY_STORE_FOLDER).isDirectory();

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
    IdeaPluginDescriptor[] result = ourPlugins.get();
    return result == null ? initPlugins() : result;
  }

  /**
   * Returns descriptors of plugins which are successfully loaded into IDE. The result is sorted in a way that if each plugin comes after
   * the plugins it depends on.
   */
  @NotNull
  public static synchronized List<IdeaPluginDescriptor> getLoadedPlugins() {
    if (ourLoadedPlugins == null) {
      initPlugins();
    }
    return ourLoadedPlugins;
  }

  static boolean arePluginsInitialized() {
    return ourPlugins.get() != null;
  }

  public static synchronized void setPlugins(@NotNull IdeaPluginDescriptor[] descriptors) {
    ourPlugins.set(descriptors);
    ourLoadedPlugins = Collections.unmodifiableList(ContainerUtil.findAll(descriptors, (p) -> !shouldSkipPlugin(p)));
  }

  public static void loadDisabledPlugins(@NotNull String configPath, @NotNull Collection<String> disabledPlugins) {
    File file = new File(configPath, DISABLED_PLUGINS_FILENAME);
    if (file.isFile()) {
      ApplicationInfoEx appInfo = ApplicationInfoImpl.getShadowInstance();
      List<String> requiredPlugins = StringUtil.split(System.getProperty(JetBrainsProtocolHandler.REQUIRED_PLUGINS_KEY, ""), ",");
      try {
        boolean updateDisablePluginsList = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
          String id;
          while ((id = reader.readLine()) != null) {
            id = id.trim();
            if (!requiredPlugins.contains(id) && !appInfo.isEssentialPlugin(id)) {
              disabledPlugins.add(id);
            }
            else {
              updateDisablePluginsList = true;
            }
          }
        }
        finally {
          if (updateDisablePluginsList) {
            savePluginsList(disabledPlugins, false, file);
            fireEditDisablePlugins();
          }
        }
      }
      catch (IOException e) {
        getLogger().info("Unable to load disabled plugins list from " + file, e);
      }
    }
  }

  /**
   * @deprecated Bad API, sorry. Please use {@link #isDisabled(String)} to check plugin's state,
   * {@link #enablePlugin(String)}/{@link #disablePlugin(String)} for state management,
   * {@link #disabledPlugins()} to get an unmodifiable collection of all disabled plugins (rarely needed).
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  public static @NotNull List<String> getDisabledPlugins() {
    loadDisabledPlugins();
    return new AbstractList<String>() {
      //<editor-fold desc="Just a ist-like immutable wrapper over a set; move along.">
      @Override
      public boolean contains(Object o) {
        return ourDisabledPlugins.contains(o);
      }

      @Override
      public int size() {
        return ourDisabledPlugins.size();
      }

      @Override
      public String get(int index) {
        if (index < 0 || index >= ourDisabledPlugins.size()) {
          throw new IndexOutOfBoundsException("index=" + index + " size=" + ourDisabledPlugins.size());
        }
        Iterator<String> iterator = ourDisabledPlugins.iterator();
        for (int i = 0; i < index; i++) iterator.next();
        return iterator.next();
      }
      //</editor-fold>
    };
  }

  private static void loadDisabledPlugins() {
    if (ourDisabledPlugins == null) {
      ourDisabledPlugins = new LinkedHashSet<>();  // to preserve the order of additions and removals
      if (System.getProperty("idea.ignore.disabled.plugins") == null) {
        loadDisabledPlugins(PathManager.getConfigPath(), ourDisabledPlugins);
      }
    }
  }

  @NotNull
  public static Collection<String> disabledPlugins() {
    loadDisabledPlugins();
    return Collections.unmodifiableCollection(ourDisabledPlugins);
  }

  public static boolean isDisabled(@NotNull String pluginId) {
    loadDisabledPlugins();
    return ourDisabledPlugins.contains(pluginId);
  }

  public static boolean isBrokenPlugin(@NotNull IdeaPluginDescriptor descriptor) {
    PluginId pluginId = descriptor.getPluginId();
    return pluginId == null || getBrokenPluginVersions().get(pluginId.getIdString()).contains(descriptor.getVersion());
  }

  @NotNull
  private static MultiMap<String, String> getBrokenPluginVersions() {
    if (ourBrokenPluginVersions == null) {
      ourBrokenPluginVersions = MultiMap.createSet();

      if (System.getProperty("idea.ignore.disabled.plugins") == null) {
        try (InputStream resource = PluginManagerCore.class.getResourceAsStream("/brokenPlugins.txt");
             BufferedReader br = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
          String s;
          while ((s = br.readLine()) != null) {
            s = s.trim();
            if (s.startsWith("//")) continue;

            List<String> tokens = ParametersListUtil.parse(s);
            if (tokens.isEmpty()) continue;

            if (tokens.size() == 1) {
              throw new RuntimeException("brokenPlugins.txt is broken. The line contains plugin name, but does not contains version: " + s);
            }

            String pluginId = tokens.get(0);
            List<String> versions = tokens.subList(1, tokens.size());

            ourBrokenPluginVersions.putValues(pluginId, versions);
          }
        }
        catch (IOException e) {
          throw new RuntimeException("Failed to read /brokenPlugins.txt", e);
        }
      }
    }

    return ourBrokenPluginVersions;
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
      FileUtil.ensureCanCreateFile(plugins);
    }
    try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(plugins, append), StandardCharsets.UTF_8))) {
      writePluginsList(ids, writer);
    }
  }

  public static void writePluginsList(@NotNull Collection<String> ids, @NotNull Writer writer) throws IOException {
    String[] sortedIds = ArrayUtilRt.toStringArray(ids);
    Arrays.sort(sortedIds);
    String separator = LineSeparator.getSystemLineSeparator().getSeparatorString();
    for (String id : sortedIds) {
      writer.write(id);
      writer.write(separator);
    }
  }

  public static boolean disablePlugin(@NotNull String id) {
    loadDisabledPlugins();
    return ourDisabledPlugins.add(id) && trySaveDisabledPlugins(ourDisabledPlugins);
  }

  public static boolean enablePlugin(@NotNull String id) {
    loadDisabledPlugins();
    return ourDisabledPlugins.remove(id) && trySaveDisabledPlugins(ourDisabledPlugins);
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

  public static void checkDependants(@NotNull IdeaPluginDescriptor pluginDescriptor,
                                     @NotNull Function<? super PluginId, ? extends IdeaPluginDescriptor> pluginId2Descriptor,
                                     @NotNull Condition<? super PluginId> check) {
    checkDependants(pluginDescriptor, pluginId2Descriptor, check, new THashSet<>());
  }

  private static boolean checkDependants(@NotNull IdeaPluginDescriptor pluginDescriptor,
                                         @NotNull Function<? super PluginId, ? extends IdeaPluginDescriptor> pluginId2Descriptor,
                                         @NotNull Condition<? super PluginId> check,
                                         @NotNull Set<? super PluginId> processed) {
    processed.add(pluginDescriptor.getPluginId());
    final PluginId[] dependentPluginIds = pluginDescriptor.getDependentPluginIds();
    final Set<PluginId> optionalDependencies = new THashSet<>(Arrays.asList(pluginDescriptor.getOptionalDependentPluginIds()));
    for (final PluginId dependentPluginId : dependentPluginIds) {
      if (processed.contains(dependentPluginId)) {
        continue;
      }

      if (isModuleDependency(dependentPluginId) && (ourModulesToContainingPlugins.isEmpty() || ourModulesToContainingPlugins.containsKey(
        dependentPluginId.getIdString()))) {
        continue;
      }
      if (!optionalDependencies.contains(dependentPluginId)) {
        if (!check.value(dependentPluginId)) {
          return false;
        }
        final IdeaPluginDescriptor dependantPluginDescriptor = pluginId2Descriptor.fun(dependentPluginId);
        if (dependantPluginDescriptor != null && !checkDependants(dependantPluginDescriptor, pluginId2Descriptor, check, processed)) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * This is an internal method, use {@link PluginException#createByClass(String, Throwable, Class)} instead.
   */
  @ApiStatus.Internal
  @NotNull
  public static PluginException createPluginException(@NotNull String errorMessage, @Nullable Throwable cause,
                                                      @NotNull Class pluginClass) {
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
    if (className.startsWith("java.") || className.startsWith("javax.") || className.startsWith("kotlin.") || className.startsWith("groovy.")) {
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
      String pluginPath = path == null ? null : FileUtil.toSystemIndependentName(path.getPath());
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
      return ((Boolean) loader.getClass().getMethod("hasLoadedClass", String.class).invoke(loader, className)).booleanValue();
    }
    catch (Exception e) {
      return false;
    }
  }

  private static boolean isDependent(@NotNull IdeaPluginDescriptor descriptor,
                                     @NotNull PluginId on,
                                     @NotNull Map<PluginId, IdeaPluginDescriptor> map,
                                     boolean checkModuleDependencies) {
    for (PluginId id: descriptor.getDependentPluginIds()) {
      if (ArrayUtil.contains(id, descriptor.getOptionalDependentPluginIds())) {
        continue;
      }
      if (!checkModuleDependencies && isModuleDependency(id)) {
        continue;
      }
      if (id.equals(on)) {
        return true;
      }
      IdeaPluginDescriptor depDescriptor = map.get(id);
      if (depDescriptor != null && isDependent(depDescriptor, on, map, checkModuleDependencies)) {
        return true;
      }
    }
    return false;
  }

  /**
   * In 191.* and earlier builds Java plugin was part of the platform, so any plugin installed in IntelliJ IDEA might be able to use its
   * classes without declaring explicit dependency on the Java module. This method is intended to add implicit dependency on the Java plugin
   * for such plugins to avoid breaking compatibility with them.
   */
  private static IdeaPluginDescriptor getImplicitDependency(@NotNull IdeaPluginDescriptor descriptor) {
    if (!ourModulesToContainingPlugins.containsKey(ALL_MODULES_MARKER) || descriptor.getPluginId().getIdString().equals(CORE_PLUGIN_ID)) {
      return null;
    }

    // If a plugin does not include any module dependency tags in its plugin.xml, it's assumed to be a legacy plugin
    // and is loaded only in IntelliJ IDEA, so it may use classes from Java plugin.
    boolean isLegacyPlugin = !hasModuleDependencies(descriptor);
    // Many custom plugins use classes from Java plugin and don't declare a dependency on the Java module (although they declare dependency
    // on some other platform modules). This is definitely a misconfiguration but lets temporary add the Java plugin to their dependencies
    // to avoid breaking compatibility.
    boolean isCustomPlugin = !descriptor.isBundled();
    return isLegacyPlugin || isCustomPlugin ? ourModulesToContainingPlugins.get("com.intellij.modules.java") : null;
  }

  private static boolean hasModuleDependencies(@NotNull IdeaPluginDescriptor descriptor) {
    PluginId[] dependentPluginIds = descriptor.getDependentPluginIds();
    for (PluginId dependentPluginId : dependentPluginIds) {
      if (isModuleDependency(dependentPluginId)) {
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

  public static void configureExtensions() {
    Extensions.registerAreaClass(ExtensionAreas.IDEA_PROJECT, null);
    Extensions.registerAreaClass(ExtensionAreas.IDEA_MODULE, ExtensionAreas.IDEA_PROJECT);
  }

  @Nullable
  private static ClassLoader createPluginClassLoader(@NotNull File[] classPath,
                                                     @NotNull ClassLoader[] parentLoaders,
                                                     @NotNull IdeaPluginDescriptor descriptor) {
    if (descriptor.getUseIdeaClassLoader()) {
      ClassLoader loader = PluginManagerCore.class.getClassLoader();
      try {
        // the method can't be invoked directly, because the core classloader is created at bootstrap in a "lost" branch
        MethodHandle addURL = MethodHandles.lookup().findVirtual(loader.getClass(), "addURL", MethodType.methodType(void.class, URL.class));
        for (File pathElement : classPath) {
          try {
            addURL.invoke(loader, classpathElementToUrl(pathElement));
          }
          catch (MalformedURLException e) {
            throw new PluginException("Corrupted path element: `" + pathElement + '`', e, descriptor.getPluginId());
          }
        }
        return loader;
      }
      catch (Throwable t) {
        //noinspection GraziInspection
        throw new IllegalStateException("Unexpected core classloader: " + loader + " (" + loader.getClass() + ")", t);
      }
    }
    else if (isUnitTestMode && !ourUnitTestWithBundledPlugins) {
      return null;
    }
    else {
      List<URL> urls = new ArrayList<>(classPath.length);
      for (File pathElement : classPath) {
        try {
          urls.add(classpathElementToUrl(pathElement));
        }
        catch (MalformedURLException e) {
          throw new PluginException("Corrupted path element: `" + pathElement + '`', e, descriptor.getPluginId());
        }
      }
      return new PluginClassLoader(urls, parentLoaders, descriptor.getPluginId(), descriptor.getVersion(), descriptor.getPath());
    }
  }

  private static URL classpathElementToUrl(File cpElement) throws MalformedURLException {
    return cpElement.toPath().normalize().toUri().toURL();  // it is important not to have "." and ".." in classpath elements
  }

  public static void invalidatePlugins() {
    ourPlugins.set(null);
    ourLoadedPlugins = null;
    ourDisabledPlugins = null;
  }

  public static boolean isPluginClass(@NotNull String className) {
    return ourPlugins.get() != null && getPluginByClassName(className) != null;
  }

  private static void logPlugins(@NotNull IdeaPluginDescriptorImpl[] plugins) {
    List<String> bundled = new ArrayList<>();
    List<String> disabled = new ArrayList<>();
    List<String> custom = new ArrayList<>();

    for (IdeaPluginDescriptor descriptor : plugins) {
      String version = descriptor.getVersion();
      String s = descriptor.getName() + (version != null ? " (" + version + ")" : "");

      if (!descriptor.isEnabled()) disabled.add(s);
      else if (SPECIAL_IDEA_PLUGIN.equals(descriptor.getName()) || descriptor.isBundled()) bundled.add(s);
      else custom.add(s);
    }

    Collections.sort(bundled);
    Collections.sort(custom);
    Collections.sort(disabled);

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
  private static ClassLoader[] getParentLoaders(@NotNull Map<PluginId, ? extends IdeaPluginDescriptor> idToDescriptorMap,
                                                @NotNull IdeaPluginDescriptorImpl pluginDescriptor) {
    if (isUnitTestMode && !ourUnitTestWithBundledPlugins) return new ClassLoader[0];

    PluginId[] pluginIds = pluginDescriptor.getDependentPluginIds();
    LinkedHashSet<ClassLoader> loaders = new LinkedHashSet<>(pluginIds.length);
    IdeaPluginDescriptor implicitDep = getImplicitDependency(pluginDescriptor);
    if (implicitDep != null) {
      loaders.add(implicitDep.getPluginClassLoader());
    }

    for (PluginId id : pluginIds) {
      IdeaPluginDescriptor depPluginDescriptor = idToDescriptorMap.get(id);
      if (depPluginDescriptor != null) {  // might be an optional dependency
        ClassLoader loader = depPluginDescriptor.getPluginClassLoader();
        if (loader == null) {
          getLogger().error("Plugin class loader should be initialized for plugin " + id);
        }
        else {
          loaders.add(loader);
        }
      }
    }
    return loaders.toArray(new ClassLoader[0]);
  }

  public static boolean isRunningFromSources() {
    return Holder.ourIsRunningFromSources;
  }

  private static void prepareLoadingPluginsErrorMessage(@NotNull List<String> errors) {
    if (!errors.isEmpty()) {
      String errorMessage = IdeBundle.message("error.problems.found.loading.plugins") + StringUtil.join(errors, "<p/>");
      Application app = ApplicationManager.getApplication();
      if (app != null && !app.isHeadlessEnvironment() && !isUnitTestMode) {
        if (myPluginError == null) {
          myPluginError = errorMessage;
        }
        else {
          myPluginError += "\n" + errorMessage;
        }
      }
      else {
        getLogger().error(errorMessage);
      }
    }
  }

  private static void addModulesAsDependents(@NotNull Map<PluginId, ? super IdeaPluginDescriptorImpl> map) {
    for (Map.Entry<String, IdeaPluginDescriptorImpl> entry : ourModulesToContainingPlugins.entrySet()) {
      map.put(PluginId.getId(entry.getKey()), entry.getValue());
    }
  }

  @NotNull
  private static String reportCycles(@NotNull DFSTBuilder<PluginId> builder,
                                     @NotNull Map<PluginId, ? extends IdeaPluginDescriptor> idToDescriptorMap) {
    List<Collection<PluginId>> cycles = ContainerUtil.filter(builder.getComponents(), c -> c.size() > 1);

    cycles.stream().flatMap(Collection::stream).forEach(id -> idToDescriptorMap.get(id).setEnabled(false));

    String cyclePresentation;
    Application app = ApplicationManager.getApplication();
    if (app != null ? app.isInternal() : SystemProperties.is(IDEA_IS_INTERNAL_PROPERTY)) {
      cyclePresentation = cycles.stream().map(c -> StringUtil.join(c, " ")).collect(Collectors.joining("; "));
    }
    else {
      Couple<PluginId> loop = builder.getCircularDependency();
      cyclePresentation = loop.first + "->" + loop.second + "->...->" + loop.first;
    }
    return IdeBundle.message("error.plugins.should.not.have.cyclic.dependencies") + " " + cyclePresentation;
  }

  @NotNull
  private static Comparator<IdeaPluginDescriptor> getPluginDescriptorComparator(@NotNull DFSTBuilder<PluginId> builder) {
    Comparator<PluginId> idComparator = builder.comparator();
    return (o1, o2) -> {
      PluginId pluginId1 = o1.getPluginId();
      PluginId pluginId2 = o2.getPluginId();
      if (pluginId1.getIdString().equals(CORE_PLUGIN_ID)) return -1;
      if (pluginId2.getIdString().equals(CORE_PLUGIN_ID)) return 1;
      return idComparator.compare(pluginId1, pluginId2);
    };
  }

  @NotNull
  private static Graph<PluginId> createPluginIdGraph(@NotNull Map<PluginId, ? extends IdeaPluginDescriptor> idToDescriptorMap) {
    List<PluginId> ids = new ArrayList<>(idToDescriptorMap.keySet());
    // this magic ensures that the dependent plugins always follow their dependencies in lexicographic order
    // needed to make sure that extensions are always in the same order
    ids.sort((o1, o2) -> o2.getIdString().compareTo(o1.getIdString()));
    return GraphGenerator.generate(CachingSemiGraph.cache(new InboundSemiGraph<PluginId>() {
      @NotNull
      @Override
      public Collection<PluginId> getNodes() {
        return ids;
      }

      @NotNull
      @Override
      public Iterator<PluginId> getIn(PluginId pluginId) {
        IdeaPluginDescriptor descriptor = idToDescriptorMap.get(pluginId);
        List<PluginId> plugins = new ArrayList<>();
        IdeaPluginDescriptor implicitDep = getImplicitDependency(descriptor);
        if (implicitDep != null) {
          plugins.add(implicitDep.getPluginId());
        }
        for (PluginId dependentPluginId : descriptor.getDependentPluginIds()) {
          // check for missing optional dependency
          IdeaPluginDescriptor dep = idToDescriptorMap.get(dependentPluginId);
          if (dep != null) {
            // if 'dep' refers to a module we need to add the real plugin containing this module only if it's still enabled,
            // otherwise the graph will be inconsistent
            PluginId realPluginId = dep.getPluginId();
            if (idToDescriptorMap.containsKey(realPluginId)) {
              plugins.add(realPluginId);
            }
          }
        }
        return plugins.iterator();
      }
    }));
  }

  @Nullable
  private static IdeaPluginDescriptorImpl loadDescriptorFromDir(@NotNull File file,
                                                                @NotNull String pathName,
                                                                @Nullable File pluginPath,
                                                                @NotNull LoadingContext loadingContext) {
    File descriptorFile = new File(file, META_INF + pathName);
    if (!descriptorFile.exists()) {
      return null;
    }

    try {
      IdeaPluginDescriptorImpl descriptor = new IdeaPluginDescriptorImpl(notNull(pluginPath, file), loadingContext.isBundled);
      descriptor.loadFromFile(descriptorFile, loadingContext.getXmlFactory(), isUnitTestMode);
      return descriptor;
    }
    catch (SerializationException | JDOMException | IOException e) {
      if (loadingContext.isEssential) ExceptionUtil.rethrow(e);
      getLogger().warn("Cannot load " + descriptorFile, e);
      prepareLoadingPluginsErrorMessage(Collections.singletonList("File '" + file.getName() + "' contains invalid plugin descriptor."));
    }
    catch (Throwable e) {
      if (loadingContext.isEssential) ExceptionUtil.rethrow(e);
      getLogger().warn("Cannot load " + descriptorFile, e);
    }
    return null;
  }

  @Nullable
  private static IdeaPluginDescriptorImpl loadDescriptorFromJar(@NotNull File file,
                                                                @NotNull String fileName,
                                                                @NotNull JDOMXIncluder.PathResolver pathResolver,
                                                                @NotNull LoadingContext context,
                                                                @Nullable File pluginPath) {
    try {
      String entryName = META_INF + fileName;
      URL jarURL = URLUtil.getJarEntryURL(file, FileUtil.toCanonicalPath(entryName, '/'));

      ZipFile zipFile = context.open(file);
      ZipEntry entry = zipFile.getEntry(entryName);
      if (entry != null) {
        IdeaPluginDescriptorImpl descriptor = new IdeaPluginDescriptorImpl(notNull(pluginPath, file), context.isBundled);
        SafeJdomFactory factory = context.getXmlFactory();
        Interner<String> interner = factory == null ? null : factory.stringInterner();
        descriptor.readExternal(JDOMUtil.load(zipFile.getInputStream(entry), factory), jarURL, pathResolver, interner);
        context.lastZipWithDescriptor = file;
        return descriptor;
      }
    }
    catch (SerializationException | InvalidDataException e) {
      if (context.isEssential) ExceptionUtil.rethrow(e);
      getLogger().info("Cannot load " + file + "!/META-INF/" + fileName, e);
      prepareLoadingPluginsErrorMessage(Collections.singletonList("File '" + file.getName() + "' contains invalid plugin descriptor."));
    }
    catch (Throwable e) {
      if (context.isEssential) ExceptionUtil.rethrow(e);
      getLogger().info("Cannot load " + file + "!/META-INF/" + fileName, e);
    }

    return null;
  }

  @Nullable
  public static IdeaPluginDescriptorImpl loadDescriptor(@NotNull File file, @NotNull String fileName) {
    return loadDescriptor(file, fileName, false, false, null);
  }

  @Nullable
  private static IdeaPluginDescriptorImpl loadDescriptor(@NotNull File file,
                                                         @NotNull String fileName,
                                                         boolean bundled,
                                                         boolean essential,
                                                         @Nullable LoadDescriptorsContext parentContext) {
    try (LoadingContext context = new LoadingContext(parentContext, bundled, essential)) {
      return loadDescriptor(file, fileName, context);
    }
  }

  private static class LoadingContext implements AutoCloseable {
    private final Map<File, ZipFile> myOpenedFiles = new THashMap<>();
    final @Nullable LoadDescriptorsContext parentContext;
    final boolean isBundled;
    final boolean isEssential;
    File lastZipWithDescriptor;

    /**
     * parentContext is null only for CoreApplicationEnvironment - it is not valid otherwise because in this case XML is not interned.
     */
    LoadingContext(@Nullable LoadDescriptorsContext parentContext, boolean isBundled, boolean isEssential) {
      this.parentContext = parentContext;
      this.isBundled = isBundled;
      this.isEssential = isEssential;
    }

    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    ZipFile open(File file) throws IOException {
      ZipFile zipFile = myOpenedFiles.get(file);
      if (zipFile == null) {
        myOpenedFiles.put(file, zipFile = new ZipFile(file));
      }
      return zipFile;
    }

    @Nullable
    SafeJdomFactory getXmlFactory() {
      return parentContext != null ? parentContext.getXmlFactory() : null;
    }

    @Override
    public void close() {
      for (ZipFile file : myOpenedFiles.values()) {
        try { file.close(); }
        catch (IOException ignore) { }
      }
    }
  }

  @Nullable
  private static IdeaPluginDescriptorImpl loadDescriptor(@NotNull File file, @NotNull String pathName, @NotNull LoadingContext context) {
    IdeaPluginDescriptorImpl descriptor = null;

    boolean isDirectory = file.isDirectory();
    if (isDirectory) {
      descriptor = loadDescriptorFromDir(file, pathName, null, context);

      if (descriptor == null) {
        File[] files = new File(file, "lib").listFiles();
        if (files == null || files.length == 0) {
          return null;
        }

        putMoreLikelyPluginJarsFirst(file, files);

        List<File> pluginJarFiles = null;
        for (File f : files) {
          if (f.isDirectory()) {
            IdeaPluginDescriptorImpl descriptor1 = loadDescriptorFromDir(f, pathName, file, context);
            if (descriptor1 != null) {
              if (descriptor != null) {
                getLogger().info("Cannot load " + file + " because two or more plugin.xml's detected");
                return null;
              }
              descriptor = descriptor1;
            }
          }
          else if (FileUtilRt.isJarOrZip(f, false)) {
            if (files.length == 1) {
              pluginJarFiles = Collections.singletonList(f);
            }
            else {
              if (pluginJarFiles == null) {
                pluginJarFiles = new ArrayList<>();
              }
              pluginJarFiles.add(f);
            }
          }
        }

        if (pluginJarFiles != null) {
          PluginXmlPathResolver pathResolver = new PluginXmlPathResolver(files);
          for (File jarFile : pluginJarFiles) {
            descriptor = loadDescriptorFromJar(jarFile, pathName, pathResolver, context, file);
            if (descriptor != null) {
              break;
            }
          }
        }
      }
    }
    else if (StringUtilRt.endsWithIgnoreCase(file.getPath(), ".jar")) {
      descriptor = loadDescriptorFromJar(file, pathName, JDOMXIncluder.DEFAULT_PATH_RESOLVER, context, null);
    }

    if (descriptor == null) {
      return null;
    }

    if (PLUGIN_XML.equals(pathName) && (descriptor.getPluginId() == null || descriptor.getName() == null)) {
      getLogger().info("Cannot load descriptor from " + file + ": ID or name missing");
      prepareLoadingPluginsErrorMessage(Collections.singletonList("'" + file.getName() + "' contains invalid plugin descriptor."));
      return null;
    }

    resolveOptionalDescriptors(pathName, descriptor, (@SystemIndependent String optPathName) -> {
      IdeaPluginDescriptorImpl optionalDescriptor = null;
      if (context.lastZipWithDescriptor != null) { // try last file that had the descriptor that worked
        optionalDescriptor = loadDescriptor(context.lastZipWithDescriptor, optPathName, context);
      }
      if (optionalDescriptor == null) {
        optionalDescriptor = loadDescriptor(file, optPathName, context);
      }
      if (optionalDescriptor == null && (isDirectory || resolveDescriptorsInResources())) {
        // JDOMXIncluder can find included descriptor files via classloading in URLUtil.openResourceStream
        // and here code supports the same behavior.
        // Note that this code is meant for IDE development / testing purposes
        URL resource = PluginManagerCore.class.getClassLoader().getResource(META_INF + optPathName);
        if (resource != null) {
          optionalDescriptor = loadDescriptorFromResource(resource, optPathName, context.isBundled, false, context.parentContext);
        }
      }
      return optionalDescriptor;
    });

    return descriptor;
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
  private static void putMoreLikelyPluginJarsFirst(File pluginDir, File[] filesInLibUnderPluginDir) {
    String pluginDirName = pluginDir.getName();

    Arrays.parallelSort(filesInLibUnderPluginDir, (o1, o2) -> {
      String o2Name = o2.getName();
      String o1Name = o1.getName();

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

  private static boolean fileNameIsLikeVersionedLibraryName(String name) {
    int i = name.lastIndexOf('-');
    if (i == -1) return false;
    if (i + 1 < name.length()) {
      char c = name.charAt(i + 1);
      if (Character.isDigit(c)) return true;
      if ((c == 'm' || c == 'M') && i + 2 < name.length() && Character.isDigit(name.charAt(i + 2))) {
        return true;
      }
    }
    return false;
  }

  public static void resolveOptionalDescriptors(@NotNull String fileName,
                                                 @NotNull IdeaPluginDescriptorImpl descriptor,
                                                 @NotNull Function<? super String, ? extends IdeaPluginDescriptorImpl> optionalDescriptorLoader) {
    Map<PluginId, List<String>> optionalConfigs = descriptor.getOptionalConfigs();
    if (optionalConfigs != null && !optionalConfigs.isEmpty()) {
      Map<PluginId, List<IdeaPluginDescriptorImpl>> descriptors = new LinkedHashMap<>(optionalConfigs.size());

      for (Map.Entry<PluginId, List<String>> entry : optionalConfigs.entrySet()) {
        for (String optionalDescriptorName : entry.getValue()) {
          if (fileName.equals(optionalDescriptorName)) {
            getLogger().info("recursive dependency (" + fileName + ") in " + descriptor);
            continue;
          }

          IdeaPluginDescriptorImpl optionalDescriptor = optionalDescriptorLoader.fun(optionalDescriptorName);
          if (optionalDescriptor == null) {
            getLogger().info("Cannot find optional descriptor " + optionalDescriptorName);
          }
          else {
            descriptors.computeIfAbsent(entry.getKey(), it -> new SmartList<>()).add(optionalDescriptor);
          }
        }
      }

      descriptor.setOptionalDescriptors(descriptors);
    }
  }

  private static void loadDescriptorsFromDir(@NotNull File dir,
                                             @NotNull List<IdeaPluginDescriptorImpl> result,
                                             boolean bundled,
                                             @NotNull LoadDescriptorsContext context) throws ExecutionException, InterruptedException {
    File[] files = dir.listFiles();
    if (files == null || files.length == 0) {
      return;
    }

    Set<IdeaPluginDescriptorImpl> existingResults = new THashSet<>(result);
    List<Future<IdeaPluginDescriptorImpl>> tasks = new ArrayList<>(files.length);
    for (File file : files) {
      tasks.add(context.getExecutorService().submit(() -> loadDescriptor(file, PLUGIN_XML, bundled, false, context)));
    }

    for (Future<IdeaPluginDescriptorImpl> task : tasks) {
      IdeaPluginDescriptorImpl descriptor = task.get();
      if (descriptor == null) {
        continue;
      }

      if (existingResults.add(descriptor)) {
        result.add(descriptor);
      }
      else {
        int prevIndex = result.indexOf(descriptor);
        IdeaPluginDescriptorImpl prevDescriptor = result.get(prevIndex);
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

  private static void filterBadPlugins(@NotNull List<? extends IdeaPluginDescriptorImpl> result,
                                       @NotNull Map<String, String> disabledPluginNames,
                                       @NotNull List<? super String> errors) {
    Map<PluginId, IdeaPluginDescriptor> idToDescriptorMap = new THashMap<>();
    boolean pluginsWithoutIdFound = false;
    for (Iterator<? extends IdeaPluginDescriptor> it = result.iterator(); it.hasNext();) {
      IdeaPluginDescriptor descriptor = it.next();
      PluginId id = descriptor.getPluginId();
      if (id == null) {
        pluginsWithoutIdFound = true;
      }
      else if (idToDescriptorMap.containsKey(id)) {
        errors.add(IdeBundle.message("message.duplicate.plugin.id") + id);
        it.remove();
      }
      else if (descriptor.isEnabled()) {
        idToDescriptorMap.put(id, descriptor);
      }
    }
    addModulesAsDependents(idToDescriptorMap);
    List<String> disabledPluginIds = new SmartList<>();
    LinkedHashSet<String> faultyDescriptors = new LinkedHashSet<>();
    for (Iterator<? extends IdeaPluginDescriptor> it = result.iterator(); it.hasNext();) {
      IdeaPluginDescriptor pluginDescriptor = it.next();
      checkDependants(pluginDescriptor, idToDescriptorMap::get, pluginId -> {
        if (!idToDescriptorMap.containsKey(pluginId)) {
          pluginDescriptor.setEnabled(false);
          if (!pluginId.getIdString().startsWith(MODULE_DEPENDENCY_PREFIX)) {
            if (pluginDescriptor.isImplementationDetail()) return true;

            faultyDescriptors.add(pluginId.getIdString());
            disabledPluginIds.add(pluginDescriptor.getPluginId().getIdString());
            String name = pluginDescriptor.getName();
            IdeaPluginDescriptor descriptor = idToDescriptorMap.get(pluginId);
            String pluginName;
            if (descriptor == null) {
              pluginName = pluginId.getIdString();
              if (disabledPluginNames.containsKey(pluginName)) {
                pluginName = disabledPluginNames.get(pluginName);
              }
            }
            else {
              pluginName = descriptor.getName();
            }

            boolean disabled = isDisabled(pluginId.getIdString());
            errors.add(IdeBundle.message(disabled ? "error.required.plugin.disabled" : "error.required.plugin.not.installed", name, pluginName));
          }
          it.remove();
          return false;
        }
        return true;
      });
    }
    if (!disabledPluginIds.isEmpty()) {
      myPlugins2Disable = disabledPluginIds;
      myPlugins2Enable = faultyDescriptors;
      String error = "<br><a href=\"" + DISABLE + "\">Disable ";
      if (disabledPluginIds.size() == 1) {
        PluginId pluginId2Disable = PluginId.getId(disabledPluginIds.iterator().next());
        error += idToDescriptorMap.containsKey(pluginId2Disable) ? idToDescriptorMap.get(pluginId2Disable).getName() : pluginId2Disable.getIdString();
      }
      else {
        error += "not loaded plugins";
      }
      errors.add(error + "</a>");
      boolean possibleToEnable = true;
      for (String descriptor : faultyDescriptors) {
        if (disabledPluginNames.get(descriptor) == null) {
          possibleToEnable = false;
          break;
        }
      }
      if (possibleToEnable) {
        String name = faultyDescriptors.size() == 1 ? disabledPluginNames.get(faultyDescriptors.iterator().next()) : " all necessary plugins";
        errors.add("<a href=\"" + ENABLE + "\">Enable " + name + "</a>");
      }
      errors.add("<a href=\"" + EDIT + "\">Open plugin manager</a>");
    }
    if (pluginsWithoutIdFound) {
      errors.add(IdeBundle.message("error.plugins.without.id.found"));
    }
  }

  @TestOnly
  public static List<? extends IdeaPluginDescriptor> testLoadDescriptorsFromClassPath(@NotNull ClassLoader loader)
    throws ExecutionException, InterruptedException {
    List<IdeaPluginDescriptorImpl> descriptors = ContainerUtil.newSmartList();
    LinkedHashMap<URL, String> urlsFromClassPath = new LinkedHashMap<>();
    URL platformPluginURL = computePlatformPluginUrlAndCollectPluginUrls(loader, urlsFromClassPath);
    loadDescriptorsFromClassPath(urlsFromClassPath, descriptors, new LoadDescriptorsContext(false), platformPluginURL);
    return descriptors;
  }

  private static void loadDescriptorsFromClassPath(@NotNull LinkedHashMap<URL, String> urls,
                                                   @NotNull List<IdeaPluginDescriptorImpl> result,
                                                   @NotNull LoadDescriptorsContext context,
                                                   @Nullable URL platformPluginURL)
    throws ExecutionException, InterruptedException {
    if (urls.isEmpty()) {
      return;
    }

    List<Future<IdeaPluginDescriptorImpl>> tasks = new ArrayList<>(urls.size());
    for (Map.Entry<URL, String> entry : urls.entrySet()) {
      URL url = entry.getKey();
      tasks.add(context.getExecutorService().submit(() -> loadDescriptorFromResource(url, entry.getValue(), true, url.equals(platformPluginURL), context)));
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
  private static IdeaPluginDescriptorImpl loadDescriptorFromResource(@NotNull URL resource,
                                                                     @NotNull String pathName,
                                                                     boolean bundled,
                                                                     boolean essential,
                                                                     @Nullable LoadDescriptorsContext parentContext) {
    try {
      if (URLUtil.FILE_PROTOCOL.equals(resource.getProtocol())) {
        File descriptorFile = urlToFile(resource);
        String pathname = StringUtil.trimEnd(FileUtil.toSystemIndependentName(descriptorFile.getPath()), pathName);
        File pluginDir = new File(pathname).getParentFile();
        return loadDescriptor(pluginDir, pathName, bundled, essential, parentContext);
      }
      else if (URLUtil.JAR_PROTOCOL.equals(resource.getProtocol())) {
        String path = resource.getFile();
        File pluginJar = urlToFile(new URL(path.substring(0, path.indexOf(URLUtil.JAR_SEPARATOR))));
        return loadDescriptor(pluginJar, pathName, bundled, essential, parentContext);
      }
    }
    catch (Throwable e) {
      if (essential) ExceptionUtil.rethrow(e);
      getLogger().info("Cannot load " + resource, e);
    }

    return null;
  }

  // work around corrupted URLs produced by File.getURL()
  private static File urlToFile(URL url) throws URISyntaxException, MalformedURLException {
    try {
      return new File(url.toURI());
    }
    catch (URISyntaxException e) {
      String str = url.toString();
      if (str.indexOf(' ') > 0) {
        return new File(new URL(StringUtil.replace(str, " ", "%20")).toURI());
      }
      throw e;
    }
  }

  private static void loadDescriptorsFromProperty(@NotNull List<? super IdeaPluginDescriptorImpl> result, @NotNull LoadDescriptorsContext context) {
    final String pathProperty = System.getProperty(PROPERTY_PLUGIN_PATH);
    if (pathProperty == null) return;

    for (StringTokenizer t = new StringTokenizer(pathProperty, File.pathSeparator + ","); t.hasMoreTokens();) {
      String s = t.nextToken();
      IdeaPluginDescriptorImpl ideaPluginDescriptor = loadDescriptor(new File(s), PLUGIN_XML, false, false, context);
      if (ideaPluginDescriptor != null) {
        result.add(ideaPluginDescriptor);
      }
    }
  }

  @NotNull
  public static IdeaPluginDescriptorImpl[] loadDescriptors(@NotNull List<? super String> errors) {
    Activity activity = ParallelActivity.PREPARE_APP_INIT.start(ActivitySubNames.LOAD_PLUGIN_DESCRIPTORS);

    List<IdeaPluginDescriptorImpl> result = new ArrayList<>();
    LinkedHashMap<URL, String> urlsFromClassPath = new LinkedHashMap<>();
    URL platformPluginURL = computePlatformPluginUrlAndCollectPluginUrls(PluginManagerCore.class.getClassLoader(), urlsFromClassPath);

    boolean parallel = SystemProperties.getBooleanProperty("parallel.pluginDescriptors.loading", true);
    try (LoadDescriptorsContext context = new LoadDescriptorsContext(parallel)) {
      loadDescriptorsFromDir(new File(PathManager.getPluginsPath()), result, false, context);
      if (!isUnitTestMode) {
        loadDescriptorsFromDir(new File(PathManager.getPreInstalledPluginsPath()), result, true, context);
      }

      loadDescriptorsFromProperty(result, context);
      loadDescriptorsFromClassPath(urlsFromClassPath, result, context, platformPluginURL);

      if (isUnitTestMode && result.size() <= 1) {
        // We're running in unit test mode but the classpath doesn't contain any plugins; try to load bundled plugins anyway
        ourUnitTestWithBundledPlugins = true;
        loadDescriptorsFromDir(new File(PathManager.getPreInstalledPluginsPath()), result, true, context);
      }
    }
    catch (InterruptedException | ExecutionException e) {
      ExceptionUtil.rethrow(e);
    }

    activity.end();

    return topoSortPlugins(result, errors);
  }

  @NotNull
  private static IdeaPluginDescriptorImpl[] topoSortPlugins(@NotNull List<IdeaPluginDescriptorImpl> result, @NotNull List<? super String> errors) {
    IdeaPluginDescriptorImpl[] pluginDescriptors = result.toArray(IdeaPluginDescriptorImpl.EMPTY_ARRAY);

    Map<PluginId, IdeaPluginDescriptorImpl> idToDescriptorMap = new THashMap<>();
    for (IdeaPluginDescriptorImpl descriptor : pluginDescriptors) {
      idToDescriptorMap.put(descriptor.getPluginId(), descriptor);
    }

    Graph<PluginId> graph = createPluginIdGraph(idToDescriptorMap);
    DFSTBuilder<PluginId> builder = new DFSTBuilder<>(graph);
    if (!builder.isAcyclic()) {
      errors.add(reportCycles(builder, idToDescriptorMap));
    }

    Arrays.sort(pluginDescriptors, getPluginDescriptorComparator(builder));

    return pluginDescriptors;
  }

  private static void mergeOptionalConfigs(@NotNull Map<PluginId, IdeaPluginDescriptorImpl> descriptors) {
    Map<PluginId, IdeaPluginDescriptorImpl> descriptorsWithModules = new THashMap<>(descriptors);
    addModulesAsDependents(descriptorsWithModules);
    for (IdeaPluginDescriptorImpl descriptor : descriptors.values()) {
      Map<PluginId, List<IdeaPluginDescriptorImpl>> optionalDescriptors = descriptor.getOptionalDescriptors();
      if (optionalDescriptors != null && !optionalDescriptors.isEmpty()) {
        descriptor.setOptionalDescriptors(null);

        for (Map.Entry<PluginId, List<IdeaPluginDescriptorImpl>> entry: optionalDescriptors.entrySet()) {
          if (descriptorsWithModules.containsKey(entry.getKey())) {
            for (IdeaPluginDescriptorImpl optionalDescriptor : entry.getValue()) {
              descriptor.mergeOptionalConfig(optionalDescriptor);
            }
          }
        }
      }
    }
  }

  public static void initClassLoader(@NotNull ClassLoader parentLoader, @NotNull IdeaPluginDescriptorImpl descriptor) {
    List<File> classPath = descriptor.getClassPath();
    ClassLoader loader = createPluginClassLoader(classPath.toArray(ArrayUtilRt.EMPTY_FILE_ARRAY), new ClassLoader[]{parentLoader}, descriptor);
    descriptor.setLoader(loader);
  }

  static BuildNumber getBuildNumber() {
    return Holder.ourBuildNumber;
  }

  /**
   * Checks if plugin should be loaded and return the reason why it should not
   * @param descriptor plugin to check
   * @return null if plugin should be loaded, string with the reason why plugin should not be loaded
   */
  @Nullable
  private static String detectReasonToNotLoad(@NotNull IdeaPluginDescriptor descriptor, @NotNull IdeaPluginDescriptor[] loaded) {
    String idString = descriptor.getPluginId().getIdString();
    if (CORE_PLUGIN_ID.equals(idString)) {
      return null;
    }

    String pluginId = System.getProperty("idea.load.plugins.id");
    List<String> pluginIds = null;
    if (pluginId == null) {
      if (descriptor instanceof IdeaPluginDescriptorImpl && !descriptor.isEnabled()) return "Plugin is not enabled";
      if (!shouldLoadPlugins()) return "Plugins should not be loaded";
    }
    else {
      pluginIds = StringUtil.split(pluginId, ",");
    }

    // http://www.jetbrains.org/intellij/sdk/docs/basics/getting_started/plugin_compatibility.html
    // If a plugin does not include any module dependency tags in its plugin.xml,
    // it's assumed to be a legacy plugin and is loaded only in IntelliJ IDEA.
    boolean checkModuleDependencies = !ourModulesToContainingPlugins.isEmpty() && !ourModulesToContainingPlugins.containsKey(ALL_MODULES_MARKER);
    if (checkModuleDependencies && !hasModuleDependencies(descriptor)) {
      return "Plugin does not include any module dependency tags in its plugin.xml therefore is assumed legacy and can be loaded only in IntelliJ IDEA";
    }

    String reasonToNotLoad;
    String loadPluginCategory = System.getProperty("idea.load.plugins.category");
    if (loadPluginCategory != null) {
      reasonToNotLoad = loadPluginCategory.equals(descriptor.getCategory()) ? null : "Plugin category doesn't match 'idea.load.plugins.category' value";
    }
    else if (pluginIds != null) {
      reasonToNotLoad = pluginIds.contains(idString) ? null : "'idea.load.plugins.id' doesn't contain this plugin id";
      if (reasonToNotLoad != null) {
        Map<PluginId, IdeaPluginDescriptor> map = new THashMap<>();
        for (IdeaPluginDescriptor pluginDescriptor : loaded) {
          map.put(pluginDescriptor.getPluginId(), pluginDescriptor);
        }
        addModulesAsDependents(map);
        for (String id : pluginIds) {
          IdeaPluginDescriptor descriptorFromProperty = map.get(PluginId.getId(id));
          if (descriptorFromProperty != null && isDependent(descriptorFromProperty, descriptor.getPluginId(), map, checkModuleDependencies)) {
            reasonToNotLoad = null;
            break;
          }
        }
      }
    }
    else {
      reasonToNotLoad = isDisabled(idString) ? PLUGIN_IS_DISABLED_REASON : null;
    }

    if (reasonToNotLoad == null && descriptor instanceof IdeaPluginDescriptorImpl && isIncompatible(descriptor)) {
      reasonToNotLoad = "Plugin since-build(" + descriptor.getSinceBuild() +
                        ") or until-build(" + descriptor.getUntilBuild() +
                        ") don't match this product's build number(" + getBuildNumber() + ").";
    }

    return reasonToNotLoad;
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

    try {
      return isIncompatible(buildNumber, descriptor.getSinceBuild(), descriptor.getUntilBuild(), descriptor.getName(), descriptor.toString());
    }
    catch (RuntimeException e) {
      getLogger().error(e);
    }

    return false;
  }

  static boolean isIncompatible(@NotNull BuildNumber buildNumber,
                                @Nullable String sinceBuild,
                                @Nullable String untilBuild,
                                @Nullable String descriptorName,
                                @Nullable String descriptorDebugString) {
    List<String> messages = null;
    BuildNumber sinceBuildNumber = StringUtil.isEmpty(sinceBuild) ? null : BuildNumber.fromString(sinceBuild, descriptorName, null);
    if (sinceBuildNumber != null && sinceBuildNumber.compareTo(buildNumber) > 0) {
      messages = new SmartList<>("since build " + sinceBuildNumber + " > " + buildNumber);
    }

    BuildNumber untilBuildNumber = StringUtil.isEmpty(untilBuild) ? null : BuildNumber.fromString(untilBuild, descriptorName, null);
    if (untilBuildNumber != null && untilBuildNumber.compareTo(buildNumber) < 0) {
      if (messages == null) {
        messages = new SmartList<>();
      }
      messages.add("until build " + untilBuildNumber + " < " + buildNumber);
    }
    if (messages != null) {
      getLogger().warn(ObjectUtils.chooseNotNull(descriptorName, descriptorDebugString) + " not loaded: " + StringUtil.join(messages, ", "));
      return true;
    }

    return false;
  }

  public static boolean shouldSkipPlugin(@NotNull IdeaPluginDescriptor descriptor) {
    if (descriptor instanceof IdeaPluginDescriptorImpl) {
      IdeaPluginDescriptorImpl descriptorImpl = (IdeaPluginDescriptorImpl)descriptor;
      Boolean skipped = descriptorImpl.getSkipped();
      if (skipped != null) {
        return skipped.booleanValue();
      }
      boolean result = detectReasonToNotLoad(descriptor, ourPlugins.get()) != null || isBrokenPlugin(descriptor);
      descriptorImpl.setSkipped(result);
      return result;
    }
    return detectReasonToNotLoad(descriptor, ourPlugins.get()) != null || isBrokenPlugin(descriptor);
  }

  private static void checkEssentialPluginsAreAvailable(IdeaPluginDescriptorImpl[] plugins) {
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

  @NotNull
  private static IdeaPluginDescriptorImpl[] initializePlugins() {
    Activity loadPluginsActivity = ParallelActivity.PREPARE_APP_INIT.start(ActivitySubNames.INIT_PLUGINS);
    configureExtensions();

    List<String> errors = new ArrayList<>();
    IdeaPluginDescriptorImpl[] pluginDescriptors = loadDescriptors(errors);
    if (!isUnitTestMode) {
      checkEssentialPluginsAreAvailable(pluginDescriptors);
    }

    Class callerClass = ReflectionUtil.findCallerClass(1);
    assert callerClass != null;
    ClassLoader coreLoader = callerClass.getClassLoader();

    List<IdeaPluginDescriptorImpl> result = new ArrayList<>();
    Map<PluginId, IdeaPluginDescriptorImpl> idToDescriptorMap = new THashMap<>();
    Map<String, String> disabledPluginNames = new THashMap<>();
    List<String> brokenPluginsList = new SmartList<>();
    fixDescriptors(pluginDescriptors, coreLoader, idToDescriptorMap, disabledPluginNames, brokenPluginsList, result, errors);

    Graph<PluginId> graph = createPluginIdGraph(idToDescriptorMap);
    DFSTBuilder<PluginId> builder = new DFSTBuilder<>(graph);

    prepareLoadingPluginsErrorMessage(errors);

    Comparator<PluginId> idComparator = builder.comparator();
    // sort descriptors according to plugin dependencies
    result.sort((o1, o2) -> idComparator.compare(o1.getPluginId(), o2.getPluginId()));

    for (int i = 0; i < result.size(); i++) {
      ourId2Index.put(result.get(i).getPluginId(), i);
    }

    for (IdeaPluginDescriptorImpl pluginDescriptor : result) {
      if (pluginDescriptor.getPluginId().getIdString().equals(CORE_PLUGIN_ID) || pluginDescriptor.isUseCoreClassLoader()) {
        pluginDescriptor.setLoader(coreLoader);
      }
      else {
        File[] classPath = pluginDescriptor.getClassPath().toArray(ArrayUtilRt.EMPTY_FILE_ARRAY);
        ClassLoader[] parentLoaders = getParentLoaders(idToDescriptorMap, pluginDescriptor);
        if (parentLoaders.length == 0) parentLoaders = new ClassLoader[]{coreLoader};
        pluginDescriptor.setLoader(createPluginClassLoader(classPath, parentLoaders, pluginDescriptor));
      }
    }

    loadPluginsActivity.end("plugin count: " + pluginDescriptors.length);
    Activity registerExtensionsActivity = ParallelActivity.PREPARE_APP_INIT.start(ActivitySubNames.REGISTER_EXTENSIONS);
    registerExtensionPointsAndExtensions((ExtensionsAreaImpl)Extensions.getRootArea(), result);
    //noinspection deprecation
    Extensions.AREA_LISTENER_EXTENSION_POINT.getPoint(null).registerExtension(new AreaListener() {
      @Override
      public void areaCreated(@NotNull String areaClass, @NotNull AreaInstance areaInstance) {
        registerExtensionPointsAndExtensions((ExtensionsAreaImpl)Extensions.getArea(areaInstance), result);
      }
    });
    registerExtensionsActivity.end();

    ourLoadedPlugins = Collections.unmodifiableList(result);
    ourPlugins.set(pluginDescriptors);
    return pluginDescriptors;
  }

  public static void fixDescriptors(@NotNull IdeaPluginDescriptorImpl[] pluginDescriptors,
                                    @NotNull ClassLoader parentLoader,
                                    @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idToDescriptorMap,
                                    @NotNull Map<String, String> disabledPluginNames,
                                    @NotNull List<? super String> brokenPluginsList,
                                    @NotNull List<IdeaPluginDescriptorImpl> result,
                                    @NotNull List<? super String> errors) {
    checkCanLoadPlugins(pluginDescriptors, parentLoader, disabledPluginNames, brokenPluginsList, result);

    filterBadPlugins(result, disabledPluginNames, errors);

    if (!brokenPluginsList.isEmpty()) {
      errors.add("The following plugins are incompatible with the current IDE build: " + StringUtil.join(brokenPluginsList, ", "));
    }

    fixDependencies(result, idToDescriptorMap);
  }

  private static void checkCanLoadPlugins(@NotNull IdeaPluginDescriptorImpl[] pluginDescriptors,
                                          @NotNull ClassLoader parentLoader,
                                          @NotNull Map<String, String> disabledPluginNames,
                                          @NotNull List<? super String> brokenPluginsList,
                                          @NotNull List<? super IdeaPluginDescriptorImpl> result) {
    for (IdeaPluginDescriptorImpl descriptor : pluginDescriptors) {
      String toNotLoadReason = detectReasonToNotLoad(descriptor, pluginDescriptors);
      if (toNotLoadReason == null) {
        if (isBrokenPlugin(descriptor)) {
          brokenPluginsList.add(descriptor.getName());
          toNotLoadReason = "This plugin version was marked as incompatible";
        }
      }

      if (toNotLoadReason == null) {
        List<String> modules = descriptor.getModules();
        for (String module : modules) {
          if (!ourModulesToContainingPlugins.containsKey(module)) {
            ourModulesToContainingPlugins.put(module, descriptor);
          }
        }
        result.add(descriptor);
      }
      else {
        descriptor.setEnabled(false);
        if (toNotLoadReason != PLUGIN_IS_DISABLED_REASON) {
          getLogger().info("Plugin '" + descriptor.getName() + "' can't be loaded because: " + toNotLoadReason);
        }
        disabledPluginNames.put(descriptor.getPluginId().getIdString(), descriptor.getName());
        initClassLoader(parentLoader, descriptor);
      }
    }
  }

  private static void fixDependencies(@NotNull List<? extends IdeaPluginDescriptorImpl> result,
                                      @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idToDescriptorMap) {
    for (IdeaPluginDescriptorImpl descriptor : result) {
      idToDescriptorMap.put(descriptor.getPluginId(), descriptor);
    }

    IdeaPluginDescriptor corePluginDescriptor = idToDescriptorMap.get(PluginId.getId(CORE_PLUGIN_ID));
    assert corePluginDescriptor != null : CORE_PLUGIN_ID + " not found; platform prefix is " + System.getProperty(PlatformUtils.PLATFORM_PREFIX_KEY);
    for (IdeaPluginDescriptorImpl descriptor : result) {
      if (descriptor != corePluginDescriptor) {
        descriptor.insertDependency(corePluginDescriptor);
      }
    }

    fixOptionalConfigs(idToDescriptorMap);
    mergeOptionalConfigs(idToDescriptorMap);
    addModulesAsDependents(idToDescriptorMap);
  }

  private static void fixOptionalConfigs(@NotNull Map<PluginId, IdeaPluginDescriptorImpl> idToDescriptorMap) {
    if (!isRunningFromSources()) return;
    for (IdeaPluginDescriptorImpl descriptor : idToDescriptorMap.values()) {
      if (!descriptor.isUseCoreClassLoader() || descriptor.getOptionalDescriptors() == null) continue;
      descriptor.getOptionalDescriptors().entrySet().removeIf(entry -> {
        IdeaPluginDescriptorImpl dependent = idToDescriptorMap.get(entry.getKey());
        return dependent != null && !dependent.isUseCoreClassLoader();
      });
    }
  }

  private static void registerExtensionPointsAndExtensions(@NotNull ExtensionsAreaImpl area,
                                                           @NotNull List<? extends IdeaPluginDescriptorImpl> loadedPlugins) {
    for (IdeaPluginDescriptorImpl descriptor : loadedPlugins) {
      descriptor.registerExtensionPoints(area);
    }

    ExtensionPointImpl[] extensionPoints = area.getExtensionPoints();
    for (IdeaPluginDescriptorImpl descriptor : loadedPlugins) {
      descriptor.registerExtensions(extensionPoints, area.getPicoContainer());
    }

    // to avoid clearing cache for each plugin on registration, cache is cleared only now
    // in general, on init extension point should be not initialized yet, but who knows
    // (later maybe revisited, for now preserve old behaviour to be sure)
    area.extensionsRegistered(extensionPoints);
  }

  /**
   * Load extensions points and extensions from a configuration file in plugin.xml format
   *
   * Use it only for CoreApplicationEnvironment. Do not use otherwise. For IntelliJ Platform application and tests plugins are loaded in parallel
   * (including other optimizations).
   *
   * @param pluginRoot jar file or directory which contains the configuration file
   * @param fileName name of the configuration file located in 'META-INF' directory under {@code pluginRoot}
   * @param area area which extension points and extensions should be registered (e.g. {@link Extensions#getRootArea()} for application-level extensions)
   */
  public static void registerExtensionPointAndExtensions(@NotNull File pluginRoot, @NotNull String fileName, @NotNull ExtensionsArea area) {
    IdeaPluginDescriptorImpl descriptor;
    try (LoadingContext context = new LoadingContext(null, true, true)) {
      if (pluginRoot.isDirectory()) {
        descriptor = loadDescriptorFromDir(pluginRoot, fileName, null, context);
      }
      else {
        descriptor = loadDescriptorFromJar(pluginRoot, fileName, JDOMXIncluder.DEFAULT_PATH_RESOLVER, context, null);
      }
    }

    if (descriptor != null) {
      registerExtensionPointsAndExtensions((ExtensionsAreaImpl)area, Collections.singletonList(descriptor));
    }
    else {
      getLogger().error("Cannot load " + fileName + " from " + pluginRoot);
    }
  }

  @NotNull
  private static synchronized IdeaPluginDescriptorImpl[] initPlugins() {
    IdeaPluginDescriptorImpl[] result;
    try {
      result = initializePlugins();
    }
    catch (PicoPluginExtensionInitializationException e) {
      throw new PluginException(e, e.getPluginId());
    }
    catch (RuntimeException e) {
      getLogger().error(e);
      throw e;
    }
    logPlugins(result);
    return result;
  }

  @NotNull
  public static Logger getLogger() {
    return Logger.getInstance("#com.intellij.ide.plugins.PluginManager");
  }

  static final class EssentialPluginMissingException extends RuntimeException {
    final List<String> pluginIds;

    EssentialPluginMissingException(@NotNull List<String> ids) {
      super("Missing essential plugins: " + StringUtil.join(ids, ", "));
      pluginIds = ids;
    }
  }
}