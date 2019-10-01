// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.PluginException;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.JetBrainsProtocolHandler;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionInstantiationException;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.reference.SoftReference;
import com.intellij.serialization.SerializationException;
import com.intellij.util.*;
import com.intellij.util.containers.*;
import com.intellij.util.execution.ParametersListUtil;
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
import java.lang.ref.Reference;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
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

  @SuppressWarnings("StaticNonFinalField")
  public static String BUILD_NUMBER;

  private static final TObjectIntHashMap<PluginId> ourId2Index = new TObjectIntHashMap<>();
  private static final String MODULE_DEPENDENCY_PREFIX = "com.intellij.module";
  private static final String SPECIAL_IDEA_PLUGIN = "IDEA CORE";
  private static final String PROPERTY_PLUGIN_PATH = "plugin.path";

  public static final String DISABLE = "disable";
  public static final String ENABLE = "enable";
  public static final String EDIT = "edit";

  private static Set<String> ourDisabledPlugins;
  private static Reference<MultiMap<String, String>> ourBrokenPluginVersions;
  private static volatile IdeaPluginDescriptorImpl[] ourPlugins;
  private static List<IdeaPluginDescriptorImpl> ourLoadedPlugins;

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
    IdeaPluginDescriptor[] result = ourPlugins;
    return result == null ? initPlugins(null) : result;
  }

  /**
   * Returns descriptors of plugins which are successfully loaded into IDE. The result is sorted in a way that if each plugin comes after
   * the plugins it depends on.
   */
  @NotNull
  public static List<IdeaPluginDescriptor> getLoadedPlugins() {
    return getLoadedPlugins(null);
  }

  @NotNull
  public static synchronized List<IdeaPluginDescriptor> getLoadedPlugins(@Nullable ClassLoader coreClassLoader) {
    if (ourLoadedPlugins == null) {
      initPlugins(coreClassLoader);
    }
    //noinspection unchecked
    return (List<IdeaPluginDescriptor>)(List<?>)ourLoadedPlugins;
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
    MultiMap<String, String> result = SoftReference.dereference(ourBrokenPluginVersions);
    if (result == null) {
      result = MultiMap.createSet();

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

            result.putValues(pluginId, versions);
          }
        }
        catch (IOException e) {
          throw new RuntimeException("Failed to read /brokenPlugins.txt", e);
        }
      }
      ourBrokenPluginVersions = new java.lang.ref.SoftReference<>(result);
    }

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

  /**
   * In 191.* and earlier builds Java plugin was part of the platform, so any plugin installed in IntelliJ IDEA might be able to use its
   * classes without declaring explicit dependency on the Java module. This method is intended to add implicit dependency on the Java plugin
   * for such plugins to avoid breaking compatibility with them.
   */
  @Nullable
  private static PluginId getImplicitDependency(@NotNull IdeaPluginDescriptor descriptor,
                                                @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idMap) {
    String id = descriptor.getPluginId().getIdString();
    // Skip our plugins as expected to be up-to-date whether bundled or not.
    if (id.equals(CORE_PLUGIN_ID) ||
        id.startsWith("com.intellij.") || id.startsWith("com.jetbrains.") ||
        id.startsWith("org.intellij.") || id.startsWith("org.jetbrains.")) {
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
  private static ClassLoader createPluginClassLoader(@NotNull File[] classPath,
                                                     @NotNull ClassLoader[] parentLoaders,
                                                     @NotNull IdeaPluginDescriptor descriptor) {
    if (isUnitTestMode && !ourUnitTestWithBundledPlugins) {
      return null;
    }
    else if (descriptor.getUseIdeaClassLoader()) {
      getLogger().warn(descriptor.getPluginId() + " uses deprecated `use-idea-classloader` attribute");
      ClassLoader loader = PluginManagerCore.class.getClassLoader();
      try {
        // `UrlClassLoader#addURL` can't be invoked directly, because the core classloader is created at bootstrap in a "lost" branch
        MethodHandle addURL = MethodHandles.lookup().findVirtual(loader.getClass(), "addURL", MethodType.methodType(void.class, URL.class));
        for (File pathElement : classPath) {
          addURL.invoke(loader, classpathElementToUrl(pathElement, descriptor));
        }
        return loader;
      }
      catch (Throwable t) {
        throw new IllegalStateException("An unexpected core classloader: " + loader.getClass(), t);
      }
    }
    else {
      List<URL> urls = new ArrayList<>(classPath.length);
      for (File pathElement : classPath) {
        urls.add(classpathElementToUrl(pathElement, descriptor));
      }
      return new PluginClassLoader(urls, parentLoaders, descriptor.getPluginId(), descriptor.getVersion(), descriptor.getPath());
    }
  }

  private static URL classpathElementToUrl(File cpElement, IdeaPluginDescriptor descriptor) {
    try {
      return cpElement.toPath().normalize().toUri().toURL();  // it is important not to have traversal elements in classpath
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
  private static ClassLoader[] getParentLoaders(@NotNull IdeaPluginDescriptorImpl descriptor,
                                                @NotNull PluginTraverser traverser) {
    if (isUnitTestMode && !ourUnitTestWithBundledPlugins) return new ClassLoader[0];
    JBIterable<PluginId> dependencies = traverser.children(descriptor.getPluginId());

    LinkedHashSet<ClassLoader> loaders = new LinkedHashSet<>();

    for (PluginId depId : dependencies) {
      IdeaPluginDescriptor dep = traverser.idMap.get(depId);
      if (dep == null) {
        // might be an optional dependency
        continue;
      }
      ClassLoader loader = dep.getPluginClassLoader();
      if (loader == null) {
        getLogger().error("Plugin \"" + toPresentableName(descriptor) + "\" requires missing class loader for " + toPresentableName(dep));
      }
      else {
        loaders.add(loader);
      }
    }
    return loaders.toArray(new ClassLoader[0]);
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

  private static void checkPluginCycles(@NotNull JBTreeTraverser<PluginId> traverser,
                                        @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idMap,
                                        @NotNull List<String> errors) {
    List<List<PluginId>> cycles = new ArrayList<>();
    Set<PluginId> visited = new HashSet<>();
    Set<PluginId> ignored = new HashSet<>();
    JBIterable<PluginId> dfs = traverser.withRoots(idMap.keySet())
      .forceIgnore(ignored::contains)
      .traverse();
    for (TreeTraversal.TracingIt<PluginId> it = dfs.typedIterator(); it.hasNext(); ) {
      PluginId id = it.next();
      if (visited.add(id)) continue;
      List<PluginId> list = it.backtrace().skip(1).toList();
      int idx = list.indexOf(id);
      if (idx == -1) continue;
      List<PluginId> cycle = list.subList(0, idx + 1);
      cycles.add(cycle);
      ignored.addAll(cycle);
    }
    if (cycles.isEmpty()) return;
    for (PluginId id : JBIterable.from(cycles).flatMap(o -> o)) {
      idMap.get(id).setEnabled(false);
    }

    for (List<PluginId> cycle : cycles) {
      JBIterable<String> names = JBIterable.from(cycle).map(o -> toPresentableName(idMap.get(o)));
      String cycleText = StringUtil.join(names.sort(String::compareTo), ", ");
      errors.add("Plugins " + cycleText + " form dependency cycle");
    }
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
      descriptor.loadFromFile(descriptorFile, loadingContext.getXmlFactory(), isUnitTestMode, loadingContext.ignoreDisabled);
      return descriptor;
    }
    catch (SerializationException | JDOMException | IOException e) {
      if (loadingContext.isEssential) ExceptionUtil.rethrow(e);
      getLogger().warn("Cannot load " + descriptorFile, e);
      prepareLoadingPluginsErrorMessage(Collections.singletonList("File '" + file.getName() + "' contains invalid plugin descriptor"));
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
        descriptor.readExternal(JDOMUtil.load(zipFile.getInputStream(entry), factory), jarURL, pathResolver, interner, context.ignoreDisabled);
        context.lastZipWithDescriptor = file;
        return descriptor;
      }
    }
    catch (SerializationException | InvalidDataException e) {
      if (context.isEssential) ExceptionUtil.rethrow(e);
      getLogger().info("Cannot load " + file + "!/META-INF/" + fileName, e);
      prepareLoadingPluginsErrorMessage(Collections.singletonList("File '" + file.getName() + "' contains invalid plugin descriptor"));
    }
    catch (Throwable e) {
      if (context.isEssential) ExceptionUtil.rethrow(e);
      getLogger().info("Cannot load " + file + "!/META-INF/" + fileName, e);
    }

    return null;
  }

  @Nullable
  public static IdeaPluginDescriptorImpl loadDescriptor(@NotNull File file, @NotNull String fileName) {
    return loadDescriptor(file, fileName, false);
  }

  @Nullable
  public static IdeaPluginDescriptorImpl loadDescriptor(@NotNull File file, @NotNull String fileName, boolean ignoreDisabled) {
    return loadDescriptor(file, fileName, false, false, ignoreDisabled, null);
  }

  @Nullable
  private static IdeaPluginDescriptorImpl loadDescriptor(@NotNull File file,
                                                         @NotNull String fileName,
                                                         boolean bundled,
                                                         boolean essential,
                                                         boolean ignoreDisabled,
                                                         @Nullable LoadDescriptorsContext parentContext) {
    try (LoadingContext context = new LoadingContext(parentContext, bundled, essential, ignoreDisabled)) {
      return loadDescriptor(file, fileName, context);
    }
  }

  private static class LoadingContext implements AutoCloseable {
    private final Map<File, ZipFile> myOpenedFiles = new THashMap<>();
    final @Nullable LoadDescriptorsContext parentContext;
    final boolean isBundled;
    final boolean isEssential;
    final boolean ignoreDisabled;
    File lastZipWithDescriptor;

    /**
     * parentContext is null only for CoreApplicationEnvironment - it is not valid otherwise because in this case XML is not interned.
     */
    LoadingContext(@Nullable LoadDescriptorsContext parentContext, boolean isBundled, boolean isEssential, boolean ignoreDisabled) {
      this.parentContext = parentContext;
      this.isBundled = isBundled;
      this.isEssential = isEssential;
      this.ignoreDisabled = ignoreDisabled;
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
      prepareLoadingPluginsErrorMessage(Collections.singletonList("'" + file.getName() + "' contains invalid plugin descriptor"));
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
          optionalDescriptor = loadDescriptorFromResource(resource, optPathName, context.isBundled, false, context.ignoreDisabled, context.parentContext);
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
                                                 @NotNull Function<? super String, IdeaPluginDescriptorImpl> optionalDescriptorLoader) {
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
      tasks.add(context.getExecutorService().submit(() -> loadDescriptor(file, PLUGIN_XML, bundled, false, false, context)));
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

  private static void prepareLoadingPluginsErrorMessage(@NotNull Map<PluginId, String> disabledIds,
                                                        @NotNull Set<PluginId> disabledRequiredIds,
                                                        @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idMap,
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
      tasks.add(context.getExecutorService().submit(() -> loadDescriptorFromResource(url, entry.getValue(), true, url.equals(platformPluginURL), false, context)));
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
                                                                     boolean ignoreDisabled,
                                                                     @Nullable LoadDescriptorsContext parentContext) {
    try {
      if (URLUtil.FILE_PROTOCOL.equals(resource.getProtocol())) {
        File descriptorFile = urlToFile(resource);
        String pathname = StringUtil.trimEnd(FileUtil.toSystemIndependentName(descriptorFile.getPath()), pathName);
        File pluginDir = new File(pathname).getParentFile();
        return loadDescriptor(pluginDir, pathName, bundled, essential, ignoreDisabled, parentContext);
      }
      else if (URLUtil.JAR_PROTOCOL.equals(resource.getProtocol())) {
        String path = resource.getFile();
        File pluginJar = urlToFile(new URL(path.substring(0, path.indexOf(URLUtil.JAR_SEPARATOR))));
        return loadDescriptor(pluginJar, pathName, bundled, essential, ignoreDisabled, parentContext);
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
      IdeaPluginDescriptorImpl ideaPluginDescriptor = loadDescriptor(new File(s), PLUGIN_XML, false, false, false, context);
      if (ideaPluginDescriptor != null) {
        result.add(ideaPluginDescriptor);
      }
    }
  }

  @NotNull
  public static IdeaPluginDescriptorImpl[] loadDescriptors() {
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
    Collections.sort(result, (o1, o2) -> Comparing.compare(String.valueOf(o1.getPluginId()),
                                                           String.valueOf(o2.getPluginId())));
    return result.toArray(IdeaPluginDescriptorImpl.EMPTY_ARRAY);
  }

  private static void mergeOptionalConfigs(@NotNull List<IdeaPluginDescriptorImpl> result,
                                           @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idMap) {
    Condition<PluginId> enabledCondition = depId -> {
      IdeaPluginDescriptorImpl dep = idMap.get(depId);
      return dep != null && dep.isEnabled();
    };
    for (IdeaPluginDescriptorImpl descriptor : result) {
      for (IdeaPluginDescriptorImpl dep : optionalDescriptorRecursively(descriptor, enabledCondition)) {
        boolean requiredDepMissing = false;
        for (PluginId depId : dep.getDependentPluginIds()) {
          if (!enabledCondition.value(depId) &&
              ArrayUtil.indexOf(dep.getOptionalDependentPluginIds(), depId) == -1) {
            requiredDepMissing = true;
            break;
          }
        }
        if (requiredDepMissing) continue;
        descriptor.mergeOptionalConfig(dep);
      }
    }
  }

  @ApiStatus.Internal
  public static void initClassLoader(@NotNull IdeaPluginDescriptorImpl descriptor,
                                     @NotNull ClassLoader coreLoader) {
    initClassLoader(descriptor, coreLoader, pluginIdTraverser());
  }

  @ApiStatus.Internal
  public static void initClassLoader(@NotNull IdeaPluginDescriptorImpl descriptor,
                                     @NotNull ClassLoader coreLoader,
                                     @NotNull JBTreeTraverser<PluginId> traverser) {
    File[] classPath = descriptor.getClassPath().toArray(ArrayUtilRt.EMPTY_FILE_ARRAY);
    ClassLoader[] parentLoaders = getParentLoaders(descriptor, (PluginTraverser)traverser);
    if (parentLoaders.length == 0) parentLoaders = new ClassLoader[]{coreLoader};
    descriptor.setLoader(createPluginClassLoader(classPath, parentLoaders, descriptor));
  }

  public static void initClassLoaderForDisabledPlugin(@NotNull ClassLoader parentLoader, @NotNull IdeaPluginDescriptorImpl descriptor) {
    File[] classPath = descriptor.getClassPath().toArray(ArrayUtilRt.EMPTY_FILE_ARRAY);
    descriptor.setLoader(createPluginClassLoader(classPath, new ClassLoader[]{parentLoader}, descriptor));
  }

  static BuildNumber getBuildNumber() {
    return Holder.ourBuildNumber;
  }

  private static void disableIncompatiblePlugins(@NotNull JBTreeTraverser<PluginId> traverser,
                                                 @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idMap,
                                                 @NotNull Set<PluginId> brokenIds,
                                                 @NotNull List<String> errors) {
    String selectedIds = System.getProperty("idea.load.plugins.id");
    String selectedCategory = System.getProperty("idea.load.plugins.category");
    boolean shouldLoadPlugins = shouldLoadPlugins();

    Set<IdeaPluginDescriptorImpl> allDescriptors = new LinkedHashSet<>(idMap.values());
    IdeaPluginDescriptorImpl coreDescriptor = idMap.get(PluginId.getId(CORE_PLUGIN_ID));
    boolean checkModuleDependencies = !coreDescriptor.getModules().isEmpty() &&
                                      !coreDescriptor.getModules().contains(ALL_MODULES_MARKER);

    LinkedHashSet<PluginId> explicitlyEnabled = null;
    if (selectedIds != null) {
      HashSet<String> set = new HashSet<>(StringUtil.split(selectedIds, ","));
      explicitlyEnabled = JBIterable.from(allDescriptors)
        .map(IdeaPluginDescriptorImpl::getPluginId)
        .filter(o -> set.contains(o.getIdString())).addAllTo(new LinkedHashSet<>());
    }
    else if (selectedCategory != null) {
      explicitlyEnabled = JBIterable.from(allDescriptors)
        .filter(o -> selectedCategory.equals(o.getCategory()))
        .map(IdeaPluginDescriptorImpl::getPluginId)
        .addAllTo(new LinkedHashSet<>());
    }
    if (explicitlyEnabled != null) {
      // add all required dependencies
      traverser.withRoots(new ArrayList<>(explicitlyEnabled)).unique().traverse().addAllTo(explicitlyEnabled);
    }
    BuildNumber buildNumber = getBuildNumber();
    for (IdeaPluginDescriptorImpl descriptor : allDescriptors) {
      String errorSuffix;
      if (descriptor == coreDescriptor) {
        errorSuffix = null;
      }
      else if (!descriptor.isEnabled()) {
        errorSuffix = "is not enabled";
      }
      else if (explicitlyEnabled != null) {
        if (!explicitlyEnabled.contains(descriptor.getPluginId())) {
          errorSuffix = selectedIds != null
                        ? "is not in 'idea.load.plugins.id' system property"
                        : "category doesn't match 'idea.load.plugins.category' system property";
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
        errorSuffix = "is incompatible (target build range is " +
                      StringUtil.notNullize(descriptor.getSinceBuild(), "0.0") + " to " +
                      StringUtil.notNullize(descriptor.getUntilBuild(), "*.*") + ")";
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

  private static void checkEssentialPluginsAreAvailable(@NotNull Collection<IdeaPluginDescriptorImpl> plugins) {
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
  static IdeaPluginDescriptorImpl[] initializePlugins(@NotNull IdeaPluginDescriptorImpl[] descriptors,
                                                      @NotNull ClassLoader coreLoader,
                                                      @Nullable PairConsumer<? super Set<String>, ? super Set<String>> disabledAndPossibleToEnableConsumer) {
    List<String> errors = new ArrayList<>();
    Map<PluginId, IdeaPluginDescriptorImpl> idMap = buildPluginIdMap(descriptors, errors);
    PluginId coreId = PluginId.getId(CORE_PLUGIN_ID);

    Map<PluginId, String> disabledIds = new LinkedHashMap<>();
    Set<PluginId> disabledRequiredIds = new LinkedHashSet<>();
    Set<PluginId> brokenIds = new LinkedHashSet<>();
    Set<PluginId> enabledIds = new LinkedHashSet<>();

    JBTreeTraverser<PluginId> requiredDepsTraverser = new PluginTraverser(idMap, false, false);
    JBTreeTraverser<PluginId> allDepsTraverser = new PluginTraverser(idMap, true, false);

    disableIncompatiblePlugins(requiredDepsTraverser, idMap, brokenIds, errors);
    checkPluginCycles(allDepsTraverser, idMap, errors);

    // topological sort based on required dependencies only
    JBIterable<PluginId> sortedRequired = requiredDepsTraverser
      .withRoots(idMap.keySet())
      .unique()
      .traverse(TreeTraversal.POST_ORDER_DFS);

    for (IdeaPluginDescriptorImpl descriptor : sortedRequired.filterMap(idMap::get).unique()) {
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
        initClassLoaderForDisabledPlugin(coreLoader, descriptor);
      }
    }

    // topological sort based on all (required and optional) dependencies
    JBIterable<PluginId> sortedAll = allDepsTraverser
      .withRoots(sortedRequired)
      .unique()
      .traverse(TreeTraversal.POST_ORDER_DFS);

    JBIterable<IdeaPluginDescriptorImpl> allPlugins = sortedAll
      .filterMap(idMap::get)
      .unique()
      .collect();
    List<IdeaPluginDescriptorImpl> enabledPlugins = allPlugins
      .filter(IdeaPluginDescriptorImpl::isEnabled)
      .addAllTo(new ArrayList<>());

    prepareLoadingPluginsErrorMessage(disabledIds, disabledRequiredIds, idMap, errors);

    fixDependencies(enabledPlugins, idMap);
    for (IdeaPluginDescriptorImpl pluginDescriptor : enabledPlugins) {
      if (pluginDescriptor.getPluginId().equals(coreId) || pluginDescriptor.isUseCoreClassLoader()) {
        pluginDescriptor.setLoader(coreLoader);
      }
      else {
        initClassLoader(pluginDescriptor, coreLoader, allDepsTraverser);
      }
    }
    if (disabledAndPossibleToEnableConsumer != null) {
      disabledAndPossibleToEnableConsumer.consume(
        JBIterable.from(disabledIds.keySet()).map(PluginId::getIdString).toSet(),
        JBIterable.from(disabledRequiredIds).map(PluginId::getIdString).toSet());
    }
    return allPlugins.toArray(IdeaPluginDescriptorImpl.EMPTY_ARRAY);
  }

  @NotNull
  private static Map<PluginId, IdeaPluginDescriptorImpl> buildPluginIdMap(@NotNull IdeaPluginDescriptorImpl[] descriptors,
                                                                          @NotNull List<String> errors) {
    MultiMap<PluginId, IdeaPluginDescriptorImpl> idMultiMap = new LinkedMultiMap<>();
    for (IdeaPluginDescriptorImpl o : descriptors) {
      idMultiMap.putValue(o.getPluginId(), o);
      for (String module : o.getModules()) {
        idMultiMap.putValue(PluginId.getId(module), o);
      }
    }
    if (idMultiMap.get(PluginId.getId(CORE_PLUGIN_ID)).isEmpty()) {
      getLogger().error(CORE_PLUGIN_ID + " not found; platform prefix is " + System.getProperty(PlatformUtils.PLATFORM_PREFIX_KEY));
    }
    Map<PluginId, IdeaPluginDescriptorImpl> idMap = new LinkedHashMap<>();
    for (PluginId id : idMultiMap.keySet()) {
      Collection<IdeaPluginDescriptorImpl> values = idMultiMap.get(id);
      if (id != null && values.size() == 1) {
        idMap.put(id, values.iterator().next());
      }
      if (id == null) {
        errors.add("No id is provided by " + StringUtil.join(values, o -> toPresentableName(o.getPath().getName()), ", "));
      }
      else if (values.size() > 1) {
        if (isModuleDependency(id)) {
          errors.add(toPresentableName(id.getIdString()) + " module is declared by plugins " +
                     StringUtil.join(values, PluginManagerCore::toPresentableName, ", "));
        }
        else {
          errors.add(toPresentableName(id.getIdString()) + " id is declared by plugins " +
                     StringUtil.join(values, o -> toPresentableName(o.getPath().getName()), ", "));
        }
      }
    }
    return idMap;
  }

  private static boolean computePluginEnabled(@NotNull IdeaPluginDescriptorImpl descriptor,
                                              @NotNull Set<PluginId> loadedIds,
                                              @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idMap,
                                              @NotNull Set<PluginId> disabledRequiredIds,
                                              @NotNull List<String> errors) {
    if (descriptor.getPluginId().getIdString().equals(CORE_PLUGIN_ID)) return true;
    boolean result = true;
    for (PluginId depId : descriptor.getDependentPluginIds()) {
      if (loadedIds.contains(depId) ||
          ArrayUtil.indexOf(descriptor.getOptionalDependentPluginIds(), depId) != -1) {
        continue;
      }
      result = false;
      if (descriptor.isImplementationDetail()) continue;
      IdeaPluginDescriptorImpl dep = idMap.get(depId);
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

  private static void fixDependencies(@NotNull List<IdeaPluginDescriptorImpl> result,
                                      @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idMap) {
    IdeaPluginDescriptor coreDescriptor = notNull(idMap.get(PluginId.getId(CORE_PLUGIN_ID)));
    for (IdeaPluginDescriptorImpl descriptor : result) {
      if (descriptor != coreDescriptor) {
        descriptor.insertDependency(coreDescriptor);
      }
    }

    fixOptionalConfigs(result, idMap);
    mergeOptionalConfigs(result, idMap);
  }

  private static void fixOptionalConfigs(@NotNull List<IdeaPluginDescriptorImpl> result,
                                         @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idMap) {
    if (!isRunningFromSources()) return;
    for (IdeaPluginDescriptorImpl descriptor : result) {
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
    try (LoadingContext context = new LoadingContext(null, true, true, false)) {
      if (pluginRoot.isDirectory()) {
        descriptor = loadDescriptorFromDir(pluginRoot, fileName, null, context);
      }
      else {
        descriptor = loadDescriptorFromJar(pluginRoot, fileName, JDOMXIncluder.DEFAULT_PATH_RESOLVER, context, null);
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

  @NotNull
  private static synchronized IdeaPluginDescriptorImpl[] initPlugins(@Nullable ClassLoader coreLoader) {
    if (coreLoader == null) {
      Class<?> callerClass = ReflectionUtil.findCallerClass(1);
      assert callerClass != null;
      coreLoader = callerClass.getClassLoader();
    }

    IdeaPluginDescriptorImpl[] result;
    try {
      Activity loadPluginsActivity = StartUpMeasurer.startActivity("plugin initialization");
      result = loadDescriptors();
      IdeaPluginDescriptorImpl[] sorted = initializePlugins(result, coreLoader, (d, e) -> {
        ourPlugins2Disable = d;
        ourPlugins2Enable = e;
      });
      ourPlugins = sorted;
      ourLoadedPlugins = JBIterable.of(sorted).filter(IdeaPluginDescriptorImpl::isEnabled).toList();
      checkEssentialPluginsAreAvailable(ourLoadedPlugins);
      int count = 0;
      for (IdeaPluginDescriptorImpl descriptor : ourLoadedPlugins) {
        ourId2Index.put(descriptor.getPluginId(), count ++);
      }
      loadPluginsActivity.end();
      loadPluginsActivity.setDescription("plugin count: " + result.length);
    }
    catch (ExtensionInstantiationException e) {
      throw new PluginException(e, e.getExtensionOwnerId());
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
  public static JBTreeTraverser<PluginId> pluginIdTraverser() {
    IdeaPluginDescriptorImpl[] plugins = ourPlugins;
    if (plugins == null) return new JBTreeTraverser<>(Functions.constant(null));
    return pluginIdTraverser(plugins);
  }

  @NotNull
  @ApiStatus.Internal
  public static JBTreeTraverser<PluginId> pluginIdTraverser(IdeaPluginDescriptorImpl[] plugins) {
    return new PluginTraverser(buildPluginIdMap(plugins, new ArrayList<>()), false, true).unique();
  }

  @NotNull
  private static JBIterable<IdeaPluginDescriptorImpl> optionalDescriptorRecursively(
    @NotNull IdeaPluginDescriptorImpl descriptor,
    @NotNull Condition<? super PluginId> condition) {
    Map<PluginId, List<IdeaPluginDescriptorImpl>> optMap = descriptor.getOptionalDescriptors();
    if (optMap == null || optMap.isEmpty()) return JBIterable.empty();

    return JBTreeTraverser.<IdeaPluginDescriptorImpl>from(d -> {
      Map<PluginId, List<IdeaPluginDescriptorImpl>> map = d.getOptionalDescriptors();
      if (map == null || map.isEmpty()) return JBIterable.empty();

      return JBIterable.from(map.entrySet())
        .filter(o -> condition.value(o.getKey()))
        .flatten(o -> o.getValue());
    })
      .withRoot(descriptor)
      .traverse()
      .skip(1);
  }

  private static class PluginTraverser extends JBTreeTraverser<PluginId> {
    final Map<PluginId, IdeaPluginDescriptorImpl> idMap;

    PluginTraverser(@NotNull Map<PluginId, IdeaPluginDescriptorImpl> idMap,
                    boolean withOptionalDeps,
                    boolean convertModulesToPlugins) {
      super(o -> {
        IdeaPluginDescriptorImpl descriptor = idMap.get(o);
        if (descriptor == null) return JBIterable.empty();
        PluginId implicitDep = getImplicitDependency(descriptor, idMap);
        JBIterable<PluginId> allDeps = JBIterable.of(descriptor.getDependentPluginIds()).append(implicitDep);
        JBIterable<PluginId> selectedDeps =
          withOptionalDeps
          ? allDeps
            .append(optionalDescriptorRecursively(descriptor, Conditions.alwaysTrue())
                      .flatten(d -> JBIterable.of(d.getDependentPluginIds())))
          : allDeps
            .filter(id -> ArrayUtil.indexOf(descriptor.getOptionalDependentPluginIds(), id) == -1);
        JBIterable<PluginId> convertedDeps = selectedDeps.filterMap(id -> {
          IdeaPluginDescriptorImpl plugin = idMap.get(id);
          if (plugin == descriptor) return null;
          return plugin != null && convertModulesToPlugins && isModuleDependency(id) ? plugin.getPluginId() : id;
        });
        return convertedDeps.unique();
      });
      this.idMap = idMap;
    }

    protected PluginTraverser(@NotNull Meta<PluginId> meta,
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