/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.plugins;

import com.intellij.ide.ClassUtilCore;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.StartupProgress;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExtensionAreas;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.*;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.xmlb.XmlSerializationException;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TIntProcedure;
import gnu.trove.TObjectIntHashMap;
import org.jdom.Document;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class PluginManagerCore {
  @NonNls private static final String DISABLED_PLUGINS_FILENAME = "disabled_plugins.txt";
  @NonNls public static final String CORE_PLUGIN_ID = "com.intellij";
  @NonNls private static final String META_INF = "META-INF";
  @NonNls public static final String PLUGIN_XML = "plugin.xml";
  private static final float PLUGINS_PROGRESS_MAX_VALUE = 0.3f;
  private static final TObjectIntHashMap<PluginId> ourId2Index = new TObjectIntHashMap<PluginId>();
  @NonNls static final String MODULE_DEPENDENCY_PREFIX = "com.intellij.module";
  private static final Map<String, IdeaPluginDescriptorImpl> ourModulesToContainingPlugins = new THashMap<String, IdeaPluginDescriptorImpl>();
  private static final PluginClassCache ourPluginClasses = new PluginClassCache();
  @NonNls private static final String SPECIAL_IDEA_PLUGIN = "IDEA CORE";
  static final String DISABLE = "disable";
  static final String ENABLE = "enable";
  static final String EDIT = "edit";
  @NonNls private static final String PROPERTY_PLUGIN_PATH = "plugin.path";
  private static List<String> ourDisabledPlugins;
  private static MultiMap<String, String> ourBrokenPluginVersions;
  private static IdeaPluginDescriptor[] ourPlugins;
  static String myPluginError;
  static List<String> myPlugins2Disable;
  static LinkedHashSet<String> myPlugins2Enable;
  public static String BUILD_NUMBER;
  private static class Holder {
    private static final BuildNumber ourBuildNumber = calcBuildNumber();

    private static BuildNumber calcBuildNumber() {
      BuildNumber ourBuildNumber = BuildNumber.fromString(System.getProperty("idea.plugins.compatible.build"));
      if (ourBuildNumber == null) {
        ourBuildNumber = BUILD_NUMBER == null ? null : BuildNumber.fromString(BUILD_NUMBER);
        if (ourBuildNumber == null) {
          ourBuildNumber = BuildNumber.fallback();
        }
      }
      return ourBuildNumber;
    }
  }

  /**
   * do not call this method during bootstrap, should be called in a copy of PluginManager, loaded by IdeaClassLoader
   */
  @NotNull
  public static synchronized IdeaPluginDescriptor[] getPlugins() {
    if (ourPlugins == null) {
      initPlugins(null);
    }
    return ourPlugins;
  }
  @NotNull
  public static synchronized IdeaPluginDescriptor[] getPlugins(StartupProgress progress) {
    if (ourPlugins == null) {
      initPlugins(progress);
    }
    return ourPlugins;
  }

  public static synchronized void setPlugins(@NotNull IdeaPluginDescriptor[] desciptors) {
    ourPlugins = desciptors;
  }

  public static void loadDisabledPlugins(@NotNull String configPath, @NotNull Collection<String> disabledPlugins) {
    final File file = new File(configPath, DISABLED_PLUGINS_FILENAME);
    if (file.isFile()) {
      try {
        BufferedReader reader = new BufferedReader(new FileReader(file));
        try {
          String id;
          while ((id = reader.readLine()) != null) {
            disabledPlugins.add(id.trim());
          }
        }
        finally {
          reader.close();
        }
      }
      catch (IOException ignored) { }
    }
  }

  @NotNull
  public static List<String> getDisabledPlugins() {
    if (ourDisabledPlugins == null) {
      ourDisabledPlugins = new ArrayList<String>();
      if (System.getProperty("idea.ignore.disabled.plugins") == null && !isUnitTestMode()) {
        loadDisabledPlugins(PathManager.getConfigPath(), ourDisabledPlugins);
      }
    }
    return ourDisabledPlugins;
  }

  public static boolean isBrokenPlugin(@NotNull IdeaPluginDescriptor descriptor) {
    return getBrokenPluginVersions().get(descriptor.getPluginId().getIdString()).contains(descriptor.getVersion());
  }

  @NotNull
  private static MultiMap<String, String> getBrokenPluginVersions() {
    if (ourBrokenPluginVersions == null) {
      ourBrokenPluginVersions = MultiMap.createSet();

      if (System.getProperty("idea.ignore.disabled.plugins") == null && !isUnitTestMode()) {
        BufferedReader br = new BufferedReader(new InputStreamReader(PluginManagerCore.class.getResourceAsStream("/brokenPlugins.txt")));
        try {
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
        finally {
          StreamUtil.closeStream(br);
        }
      }
    }
    return ourBrokenPluginVersions;
  }

  private static boolean isUnitTestMode() {
    final Application app = ApplicationManager.getApplication();
    return app != null && app.isUnitTestMode();
  }

  public static void savePluginsList(@NotNull Collection<String> ids, boolean append, @NotNull File plugins) throws IOException {
    if (!plugins.isFile()) {
      FileUtil.ensureCanCreateFile(plugins);
    }
    writePluginsList(ids, new BufferedWriter(new FileWriter(plugins, append)));
  }

  public static void writePluginsList(@NotNull Collection<String> ids, @NotNull Writer writer) throws IOException {
    try {
      for (String id : ids) {
        writer.write(id);
        writer.write(LineSeparator.getSystemLineSeparator().getSeparatorString());
      }
    }
    finally {
      writer.close();
    }
  }

  public static boolean disablePlugin(@NotNull String id) {
    List<String> disabledPlugins = getDisabledPlugins();
    if (disabledPlugins.contains(id)) {
      return false;
    }
    disabledPlugins.add(id);
    try {
      saveDisabledPlugins(disabledPlugins, false);
    }
    catch (IOException e) {
      return false;
    }
    return true;
  }

  public static boolean enablePlugin(@NotNull String id) {
    if (!getDisabledPlugins().contains(id)) return false;
    getDisabledPlugins().remove(id);
    try {
      saveDisabledPlugins(getDisabledPlugins(), false);
    }
    catch (IOException e) {
      return false;
    }
    return true;
  }

  public static void saveDisabledPlugins(@NotNull Collection<String> ids, boolean append) throws IOException {
    File plugins = new File(PathManager.getConfigPath(), DISABLED_PLUGINS_FILENAME);
    savePluginsList(ids, append, plugins);
    ourDisabledPlugins = null;
  }

  public static Logger getLogger() {
    return LoggerHolder.ourLogger;
  }

  public static int getPluginLoadingOrder(@NotNull PluginId id) {
    return ourId2Index.get(id);
  }

  static boolean isModuleDependency(@NotNull PluginId dependentPluginId) {
    return dependentPluginId.getIdString().startsWith(MODULE_DEPENDENCY_PREFIX);
  }

  public static void checkDependants(@NotNull IdeaPluginDescriptor pluginDescriptor,
                                     @NotNull Function<PluginId, IdeaPluginDescriptor> pluginId2Descriptor,
                                     @NotNull Condition<PluginId> check) {
    checkDependants(pluginDescriptor, pluginId2Descriptor, check, new THashSet<PluginId>());
  }

  private static boolean checkDependants(@NotNull IdeaPluginDescriptor pluginDescriptor,
                                         @NotNull Function<PluginId, IdeaPluginDescriptor> pluginId2Descriptor,
                                         @NotNull Condition<PluginId> check,
                                         @NotNull Set<PluginId> processed) {
    processed.add(pluginDescriptor.getPluginId());
    final PluginId[] dependentPluginIds = pluginDescriptor.getDependentPluginIds();
    final Set<PluginId> optionalDependencies = new THashSet<PluginId>(Arrays.asList(pluginDescriptor.getOptionalDependentPluginIds()));
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

  public static void addPluginClass(@NotNull String className, PluginId pluginId, boolean loaded) {
    ourPluginClasses.addPluginClass(className, pluginId, loaded);
  }

  @Nullable
  public static PluginId getPluginByClassName(@NotNull String className) {
    return ourPluginClasses.getPluginByClassName(className);
  }

  public static void dumpPluginClassStatistics() {
    ourPluginClasses.dumpPluginClassStatistics();
  }

  private static boolean isDependent(@NotNull IdeaPluginDescriptor descriptor,
                                     @NotNull PluginId on,
                                     @NotNull Map<PluginId, IdeaPluginDescriptor> map,
                                     final boolean checkModuleDependencies) {
    for (PluginId id: descriptor.getDependentPluginIds()) {
      if (ArrayUtil.contains(id, (Object[])descriptor.getOptionalDependentPluginIds())) {
        continue;
      }
      if (!checkModuleDependencies && isModuleDependency(id)) {
        continue;
      }
      if (id.equals(on)) {
        return true;
      }
      final IdeaPluginDescriptor depDescriptor = map.get(id);
      if (depDescriptor != null && isDependent(depDescriptor, on, map, checkModuleDependencies)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasModuleDependencies(@NotNull IdeaPluginDescriptor descriptor) {
    final PluginId[] dependentPluginIds = descriptor.getDependentPluginIds();
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
    //noinspection HardCodedStringLiteral
    final String loadPlugins = System.getProperty("idea.load.plugins");
    return loadPlugins == null || Boolean.TRUE.toString().equals(loadPlugins);
  }

  // used in upsource
  public static void configureExtensions() {
    Extensions.setLogProvider(new IdeaLogProvider());
    Extensions.registerAreaClass(ExtensionAreas.IDEA_PROJECT, null);
    Extensions.registerAreaClass(ExtensionAreas.IDEA_MODULE, ExtensionAreas.IDEA_PROJECT);
  }

  private static Method getAddUrlMethod(@NotNull ClassLoader loader) {
    return ReflectionUtil.getDeclaredMethod(loader instanceof URLClassLoader ? URLClassLoader.class : loader.getClass(), "addURL", URL.class);
  }

  @Nullable
  private static ClassLoader createPluginClassLoader(@NotNull File[] classPath,
                                                     @NotNull ClassLoader[] parentLoaders,
                                                     @NotNull IdeaPluginDescriptor pluginDescriptor) {

    if (pluginDescriptor.getUseIdeaClassLoader()) {
      try {
        final ClassLoader loader = PluginManagerCore.class.getClassLoader();
        final Method addUrlMethod = getAddUrlMethod(loader);


        for (File aClassPath : classPath) {
          final File file = aClassPath.getCanonicalFile();
          addUrlMethod.invoke(loader, file.toURI().toURL());
        }

        return loader;
      }
      catch (IOException e) {
        e.printStackTrace();
      }
      catch (IllegalAccessException e) {
        e.printStackTrace();
      }
      catch (InvocationTargetException e) {
        e.printStackTrace();
      }
    }

    PluginId pluginId = pluginDescriptor.getPluginId();
    File pluginRoot = pluginDescriptor.getPath();

    //if (classPath.length == 0) return null;
    if (isUnitTestMode()) return null;
    try {
      final List<URL> urls = new ArrayList<URL>(classPath.length);
      for (File aClassPath : classPath) {
        final File file = aClassPath.getCanonicalFile(); // it is critical not to have "." and ".." in classpath elements
        urls.add(file.toURI().toURL());
      }
      return new PluginClassLoader(urls, parentLoaders, pluginId, pluginDescriptor.getVersion(), pluginRoot);
    }
    catch (MalformedURLException e) {
      e.printStackTrace();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  public static void invalidatePlugins() {
    ourPlugins = null;
    ourDisabledPlugins = null;
  }

  public static boolean isPluginClass(@NotNull String className) {
    return ourPlugins != null && getPluginByClassName(className) != null;
  }

  private static void logPlugins() {
    List<String> loadedBundled = new ArrayList<String>();
    List<String> disabled = new ArrayList<String>();
    List<String> loadedCustom = new ArrayList<String>();

    for (IdeaPluginDescriptor descriptor : ourPlugins) {
      final String version = descriptor.getVersion();
      String s = descriptor.getName() + (version != null ? " (" + version + ")" : "");
      if (descriptor.isEnabled()) {
        if (descriptor.isBundled() || SPECIAL_IDEA_PLUGIN.equals(descriptor.getName())) loadedBundled.add(s);
        else loadedCustom.add(s);
      }
      else {
        disabled.add(s);
      }
    }

    Collections.sort(loadedBundled);
    Collections.sort(loadedCustom);
    Collections.sort(disabled);

    getLogger().info("Loaded bundled plugins: " + StringUtil.join(loadedBundled, ", "));
    if (!loadedCustom.isEmpty()) {
      getLogger().info("Loaded custom plugins: " + StringUtil.join(loadedCustom, ", "));
    }
    if (!disabled.isEmpty()) {
      getLogger().info("Disabled plugins: " + StringUtil.join(disabled, ", "));
    }
  }

  @NotNull
  private static ClassLoader[] getParentLoaders(@NotNull Map<PluginId, ? extends IdeaPluginDescriptor> idToDescriptorMap, @NotNull PluginId[] pluginIds) {
    if (isUnitTestMode()) return new ClassLoader[0];
    final List<ClassLoader> classLoaders = new ArrayList<ClassLoader>();
    for (final PluginId id : pluginIds) {
      IdeaPluginDescriptor pluginDescriptor = idToDescriptorMap.get(id);
      if (pluginDescriptor == null) {
        continue; // Might be an optional dependency
      }

      final ClassLoader loader = pluginDescriptor.getPluginClassLoader();
      if (loader == null) {
        getLogger().error("Plugin class loader should be initialized for plugin " + id);
      }
      classLoaders.add(loader);
    }
    return classLoaders.toArray(new ClassLoader[classLoaders.size()]);
  }

  private static int countPlugins(@NotNull String pluginsPath) {
    File configuredPluginsDir = new File(pluginsPath);
    if (configuredPluginsDir.exists()) {
      String[] list = configuredPluginsDir.list();
      if (list != null) {
        return list.length;
      }
    }
    return 0;
  }

  @NotNull
  private static Collection<URL> getClassLoaderUrls() {
    final ClassLoader classLoader = PluginManagerCore.class.getClassLoader();
    final Class<? extends ClassLoader> aClass = classLoader.getClass();
    try {
      @SuppressWarnings("unchecked") List<URL> urls = (List<URL>)aClass.getMethod("getUrls").invoke(classLoader);
      return urls;
    }
    catch (IllegalAccessException ignored) { }
    catch (InvocationTargetException ignored) { }
    catch (NoSuchMethodException ignored) { }

    if (classLoader instanceof URLClassLoader) {
      return Arrays.asList(((URLClassLoader)classLoader).getURLs());
    }

    return Collections.emptyList();
  }

  private static void prepareLoadingPluginsErrorMessage(@NotNull String errorMessage) {
    if (!StringUtil.isEmptyOrSpaces(errorMessage)) {
      if (ApplicationManager.getApplication() != null
          && !ApplicationManager.getApplication().isHeadlessEnvironment()
          && !ApplicationManager.getApplication().isUnitTestMode()) {
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
  private static Comparator<IdeaPluginDescriptor> getPluginDescriptorComparator(@NotNull final Map<PluginId, ? extends IdeaPluginDescriptor> idToDescriptorMap) {
    final Graph<PluginId> graph = createPluginIdGraph(idToDescriptorMap);
    final DFSTBuilder<PluginId> builder = new DFSTBuilder<PluginId>(graph);
    if (!builder.isAcyclic()) {
      builder.getSCCs().forEach(new TIntProcedure() {
        private int myTNumber;
        @Override
        public boolean execute(int size) {
          if (size > 1) {
            for (int j = 0; j < size; j++) {
              idToDescriptorMap.get(builder.getNodeByTNumber(myTNumber + j)).setEnabled(false);
            }
          }
          myTNumber += size;
          return true;
        }
      });
    }

    final Comparator<PluginId> idComparator = builder.comparator();
    return new Comparator<IdeaPluginDescriptor>() {
      @Override
      public int compare(@NotNull IdeaPluginDescriptor o1, @NotNull IdeaPluginDescriptor o2) {
        final PluginId pluginId1 = o1.getPluginId();
        final PluginId pluginId2 = o2.getPluginId();
        if (pluginId1.getIdString().equals(CORE_PLUGIN_ID)) return -1;
        if (pluginId2.getIdString().equals(CORE_PLUGIN_ID)) return 1;
        return idComparator.compare(pluginId1, pluginId2);
      }
    };
  }

  @NotNull
  private static Graph<PluginId> createPluginIdGraph(@NotNull final Map<PluginId, ? extends IdeaPluginDescriptor> idToDescriptorMap) {
    final List<PluginId> ids = new ArrayList<PluginId>(idToDescriptorMap.keySet());
    // this magic ensures that the dependent plugins always follow their dependencies in lexicographic order
    // needed to make sure that extensions are always in the same order
    Collections.sort(ids, new Comparator<PluginId>() {
      @Override
      public int compare(@NotNull PluginId o1, @NotNull PluginId o2) {
        return o2.getIdString().compareTo(o1.getIdString());
      }
    });
    return GraphGenerator.create(CachingSemiGraph.create(new GraphGenerator.SemiGraph<PluginId>() {
      @Override
      public Collection<PluginId> getNodes() {
        return ids;
      }

      @Override
      public Iterator<PluginId> getIn(PluginId pluginId) {
        final IdeaPluginDescriptor descriptor = idToDescriptorMap.get(pluginId);
        List<PluginId> plugins = new ArrayList<PluginId>();
        for (PluginId dependentPluginId : descriptor.getDependentPluginIds()) {
          // check for missing optional dependency
          IdeaPluginDescriptor dep = idToDescriptorMap.get(dependentPluginId);
          if (dep != null) {
            plugins.add(dep.getPluginId());
          }
        }
        return plugins.iterator();
      }
    }));
  }

  @Nullable
  static IdeaPluginDescriptorImpl loadDescriptorFromDir(@NotNull File file, @NotNull String fileName) {
    File descriptorFile = new File(file, META_INF + File.separator + fileName);
    if (descriptorFile.exists()) {
      try {
        IdeaPluginDescriptorImpl descriptor = new IdeaPluginDescriptorImpl(file);
        descriptor.readExternal(descriptorFile.toURI().toURL());
        return descriptor;
      }
      catch (XmlSerializationException e) {
        getLogger().info("Cannot load " + file, e);
        prepareLoadingPluginsErrorMessage("File '" + file.getName() + "' contains invalid plugin descriptor.");
      }
      catch (Throwable e) {
        getLogger().info("Cannot load " + file, e);
      }
    }

    return null;
  }

  @Nullable
  private static IdeaPluginDescriptorImpl loadDescriptorFromJar(@NotNull File file, @NotNull String fileName) {
    try {
      String fileURL = StringUtil.replace(file.toURI().toASCIIString(), "!", "%21");
      URL jarURL = new URL("jar:" + fileURL + "!/META-INF/" + fileName);

      ZipFile zipFile = new ZipFile(file);
      try {
        ZipEntry entry = zipFile.getEntry("META-INF/" + fileName);
        if (entry != null) {
          Document document = JDOMUtil.loadDocument(zipFile.getInputStream(entry));
          IdeaPluginDescriptorImpl descriptor = new IdeaPluginDescriptorImpl(file);
          descriptor.readExternal(document, jarURL);
          return descriptor;
        }
      }
      finally {
        zipFile.close();
      }
    }
    catch (XmlSerializationException e) {
      getLogger().info("Cannot load " + file, e);
      prepareLoadingPluginsErrorMessage("File '" + file.getName() + "' contains invalid plugin descriptor.");
    }
    catch (Throwable e) {
      getLogger().info("Cannot load " + file, e);
    }

    return null;
  }

  @Nullable
  public static IdeaPluginDescriptorImpl loadDescriptorFromJar(@NotNull File file) {
    return loadDescriptorFromJar(file, PLUGIN_XML);
  }

  @Nullable
  public static IdeaPluginDescriptorImpl loadDescriptor(@NotNull final File file, @NotNull String fileName) {
    IdeaPluginDescriptorImpl descriptor = null;

    if (file.isDirectory()) {
      descriptor = loadDescriptorFromDir(file, fileName);

      if (descriptor == null) {
        File libDir = new File(file, "lib");
        if (!libDir.isDirectory()) {
          return null;
        }
        final File[] files = libDir.listFiles();
        if (files == null || files.length == 0) {
          return null;
        }
        Arrays.sort(files, new Comparator<File>() {
          @Override
          public int compare(@NotNull File o1, @NotNull File o2) {
            if (o2.getName().startsWith(file.getName())) return Integer.MAX_VALUE;
            if (o1.getName().startsWith(file.getName())) return -Integer.MAX_VALUE;
            if (o2.getName().startsWith("resources")) return -Integer.MAX_VALUE;
            if (o1.getName().startsWith("resources")) return Integer.MAX_VALUE;
            return 0;
          }
        });
        for (final File f : files) {
          if (FileUtil.isJarOrZip(f)) {
            descriptor = loadDescriptorFromJar(f, fileName);
            if (descriptor != null) {
              descriptor.setPath(file);
              break;
            }
            //           getLogger().warn("Cannot load descriptor from " + f.getName() + "");
          }
          else if (f.isDirectory()) {
            IdeaPluginDescriptorImpl descriptor1 = loadDescriptorFromDir(f, fileName);
            if (descriptor1 != null) {
              if (descriptor != null) {
                getLogger().info("Cannot load " + file + " because two or more plugin.xml's detected");
                return null;
              }
              descriptor = descriptor1;
              descriptor.setPath(file);
            }
          }
        }
      }
    }
    else if (StringUtil.endsWithIgnoreCase(file.getName(), ".jar") && file.exists()) {
      descriptor = loadDescriptorFromJar(file, fileName);
    }

    if (descriptor != null) {
      resolveOptionalDescriptors(fileName, descriptor, new Function<String, IdeaPluginDescriptorImpl>() {
        @Override
        public IdeaPluginDescriptorImpl fun(String optionalDescriptorName) {
          IdeaPluginDescriptorImpl optionalDescriptor = loadDescriptor(file, optionalDescriptorName);
          if (optionalDescriptor == null && !FileUtil.isJarOrZip(file)) {
            for (URL url : getClassLoaderUrls()) {
              if ("file".equals(url.getProtocol())) {
                optionalDescriptor = loadDescriptor(new File(decodeUrl(url.getFile())), optionalDescriptorName);
                if (optionalDescriptor != null) {
                  break;
                }
              }
            }
          }
          return optionalDescriptor;
        }
      });
    }

    return descriptor;
  }

  // used in upsource
  public static void resolveOptionalDescriptors(@NotNull String fileName,
                                                 @NotNull IdeaPluginDescriptorImpl descriptor,
                                                 @NotNull Function<String, IdeaPluginDescriptorImpl> optionalDescriptorLoader) {
    Map<PluginId, String> optionalConfigs = descriptor.getOptionalConfigs();
    if (optionalConfigs != null && !optionalConfigs.isEmpty()) {
      Map<PluginId, IdeaPluginDescriptorImpl> descriptors = new THashMap<PluginId, IdeaPluginDescriptorImpl>(optionalConfigs.size());
      for (Map.Entry<PluginId, String> entry : optionalConfigs.entrySet()) {
        String optionalDescriptorName = entry.getValue();
        assert !Comparing.equal(fileName, optionalDescriptorName) : "recursive dependency: " + fileName;

        IdeaPluginDescriptorImpl optionalDescriptor = optionalDescriptorLoader.fun(optionalDescriptorName);

        if (optionalDescriptor == null) {
          getLogger().info("Cannot find optional descriptor " + optionalDescriptorName);
        }
        else {
          descriptors.put(entry.getKey(), optionalDescriptor);
        }
      }
      descriptor.setOptionalDescriptors(descriptors);
    }
  }

  private static void loadDescriptors(@NotNull String pluginsPath,
                                      @NotNull List<IdeaPluginDescriptorImpl> result,
                                      @Nullable StartupProgress progress,
                                      int pluginsCount) {
    loadDescriptors(new File(pluginsPath), result, progress, pluginsCount);
  }

  public static void loadDescriptors(@NotNull File pluginsHome,
                                     @NotNull List<IdeaPluginDescriptorImpl> result,
                                     @Nullable StartupProgress progress,
                                     int pluginsCount) {
    final File[] files = pluginsHome.listFiles();
    if (files != null) {
      int i = result.size();
      for (File file : files) {
        final IdeaPluginDescriptorImpl descriptor = loadDescriptor(file, PLUGIN_XML);
        if (descriptor == null) continue;
        if (progress != null) {
          progress.showProgress(descriptor.getName(), PLUGINS_PROGRESS_MAX_VALUE * ((float)++i / pluginsCount));
        }
        int oldIndex = result.indexOf(descriptor);
        if (oldIndex >= 0) {
          final IdeaPluginDescriptorImpl oldDescriptor = result.get(oldIndex);
          if (StringUtil.compareVersionNumbers(oldDescriptor.getVersion(), descriptor.getVersion()) < 0) {
            result.set(oldIndex, descriptor);
          }
        }
        else {
          result.add(descriptor);
        }
      }
    }
  }

  @NotNull
  private static String filterBadPlugins(@NotNull List<? extends IdeaPluginDescriptor> result, @NotNull final Map<String, String> disabledPluginNames) {
    final Map<PluginId, IdeaPluginDescriptor> idToDescriptorMap = new THashMap<PluginId, IdeaPluginDescriptor>();
    final StringBuilder message = new StringBuilder();
    boolean pluginsWithoutIdFound = false;
    for (Iterator<? extends IdeaPluginDescriptor> it = result.iterator(); it.hasNext();) {
      final IdeaPluginDescriptor descriptor = it.next();
      final PluginId id = descriptor.getPluginId();
      if (id == null) {
        pluginsWithoutIdFound = true;
      }
      else if (idToDescriptorMap.containsKey(id)) {
        message.append("<br>");
        message.append(IdeBundle.message("message.duplicate.plugin.id"));
        message.append(id);
        it.remove();
      }
      else if (descriptor.isEnabled()) {
        idToDescriptorMap.put(id, descriptor);
      }
    }
    addModulesAsDependents(idToDescriptorMap);
    final List<String> disabledPluginIds = new SmartList<String>();
    final LinkedHashSet<String> faultyDescriptors = new LinkedHashSet<String>();
    for (final Iterator<? extends IdeaPluginDescriptor> it = result.iterator(); it.hasNext();) {
      final IdeaPluginDescriptor pluginDescriptor = it.next();
      checkDependants(pluginDescriptor, new Function<PluginId, IdeaPluginDescriptor>() {
        @Override
        public IdeaPluginDescriptor fun(final PluginId pluginId) {
          return idToDescriptorMap.get(pluginId);
        }
      }, new Condition<PluginId>() {
        @Override
        public boolean value(final PluginId pluginId) {
          if (!idToDescriptorMap.containsKey(pluginId)) {
            pluginDescriptor.setEnabled(false);
            if (!pluginId.getIdString().startsWith(MODULE_DEPENDENCY_PREFIX)) {
              faultyDescriptors.add(pluginId.getIdString());
              disabledPluginIds.add(pluginDescriptor.getPluginId().getIdString());
              message.append("<br>");
              final String name = pluginDescriptor.getName();
              final IdeaPluginDescriptor descriptor = idToDescriptorMap.get(pluginId);
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

              message.append(getDisabledPlugins().contains(pluginId.getIdString())
                             ? IdeBundle.message("error.required.plugin.disabled", name, pluginName)
                             : IdeBundle.message("error.required.plugin.not.installed", name, pluginName));
            }
            it.remove();
            return false;
          }
          return true;
        }
      });
    }
    if (!disabledPluginIds.isEmpty()) {
      myPlugins2Disable = disabledPluginIds;
      myPlugins2Enable = faultyDescriptors;
      message.append("<br>");
      message.append("<br>").append("<a href=\"" + DISABLE + "\">Disable ");
      if (disabledPluginIds.size() == 1) {
        final PluginId pluginId2Disable = PluginId.getId(disabledPluginIds.iterator().next());
        message.append(idToDescriptorMap.containsKey(pluginId2Disable) ? idToDescriptorMap.get(pluginId2Disable).getName() : pluginId2Disable.getIdString());
      }
      else {
        message.append("not loaded plugins");
      }
      message.append("</a>");
      boolean possibleToEnable = true;
      for (String descriptor : faultyDescriptors) {
        if (disabledPluginNames.get(descriptor) == null) {
          possibleToEnable = false;
          break;
        }
      }
      if (possibleToEnable) {
        message.append("<br>").append("<a href=\"" + ENABLE + "\">Enable ").append(faultyDescriptors.size() == 1 ? disabledPluginNames.get(faultyDescriptors.iterator().next()) : " all necessary plugins").append("</a>");
      }
      message.append("<br>").append("<a href=\"" + EDIT + "\">Open plugin manager</a>");
    }
    if (pluginsWithoutIdFound) {
      message.append("<br>");
      message.append(IdeBundle.message("error.plugins.without.id.found"));
    }
    if (message.length() > 0) {
      message.insert(0, IdeBundle.message("error.problems.found.loading.plugins"));
      return message.toString();
    }
    return "";
  }

  private static void loadDescriptorsFromClassPath(@NotNull List<IdeaPluginDescriptorImpl> result, @Nullable StartupProgress progress) {
    Collection<URL> urls = getClassLoaderUrls();
    String platformPrefix = System.getProperty(PlatformUtils.PLATFORM_PREFIX_KEY);
    int i = 0;
    for (URL url : urls) {
      i++;
      if ("file".equals(url.getProtocol())) {
        File file = new File(decodeUrl(url.getFile()));

        IdeaPluginDescriptorImpl platformPluginDescriptor = null;
        if (platformPrefix != null) {
          platformPluginDescriptor = loadDescriptor(file, platformPrefix + "Plugin.xml");
          if (platformPluginDescriptor != null && !result.contains(platformPluginDescriptor)) {
            platformPluginDescriptor.setUseCoreClassLoader(true);
            result.add(platformPluginDescriptor);
          }
        }

        IdeaPluginDescriptorImpl pluginDescriptor = loadDescriptor(file, PLUGIN_XML);
        if (platformPrefix != null && pluginDescriptor != null && pluginDescriptor.getName().equals(SPECIAL_IDEA_PLUGIN)) {
          continue;
        }
        if (pluginDescriptor != null && !result.contains(pluginDescriptor)) {
          if (platformPluginDescriptor != null) {
            // if we found a regular plugin.xml in the same .jar/root as a platform-prefixed descriptor, use the core loader for it too
            pluginDescriptor.setUseCoreClassLoader(true);
          }
          result.add(pluginDescriptor);
          if (progress != null) {
            progress.showProgress("Plugin loaded: " + pluginDescriptor.getName(), PLUGINS_PROGRESS_MAX_VALUE * ((float)i / urls.size()));
          }
        }
      }
    }
  }

  @NotNull
  private static String decodeUrl(@NotNull String file) {
    String quotePluses = StringUtil.replace(file, "+", "%2B");
    //noinspection deprecation
    return URLDecoder.decode(quotePluses);
  }

  private static void loadDescriptorsFromProperty(@NotNull List<IdeaPluginDescriptorImpl> result) {
    final String pathProperty = System.getProperty(PROPERTY_PLUGIN_PATH);
    if (pathProperty == null) return;

    for (StringTokenizer t = new StringTokenizer(pathProperty, File.pathSeparator + ","); t.hasMoreTokens();) {
      String s = t.nextToken();
      final IdeaPluginDescriptorImpl ideaPluginDescriptor = loadDescriptor(new File(s), PLUGIN_XML);
      if (ideaPluginDescriptor != null) {
        result.add(ideaPluginDescriptor);
      }
    }
  }

  @NotNull
  public static IdeaPluginDescriptorImpl[] loadDescriptors(@Nullable StartupProgress progress) {
    if (ClassUtilCore.isLoadingOfExternalPluginsDisabled()) {
      return IdeaPluginDescriptorImpl.EMPTY_ARRAY;
    }

    final List<IdeaPluginDescriptorImpl> result = new ArrayList<IdeaPluginDescriptorImpl>();

    int pluginsCount = countPlugins(PathManager.getPluginsPath()) + countPlugins(PathManager.getPreInstalledPluginsPath());
    loadDescriptors(PathManager.getPluginsPath(), result, progress, pluginsCount);
    Application application = ApplicationManager.getApplication();
    boolean fromSources = false;
    if (application == null || !application.isUnitTestMode()) {
      int size = result.size();
      loadDescriptors(PathManager.getPreInstalledPluginsPath(), result, progress, pluginsCount);
      fromSources = size == result.size();
    }

    loadDescriptorsFromProperty(result);

    loadDescriptorsFromClassPath(result, fromSources ? progress : null);

    return topoSortPlugins(result);
  }

  @NotNull // used in upsource
  public static IdeaPluginDescriptorImpl[] topoSortPlugins(@NotNull List<IdeaPluginDescriptorImpl> result) {
    IdeaPluginDescriptorImpl[] pluginDescriptors = result.toArray(new IdeaPluginDescriptorImpl[result.size()]);
    final Map<PluginId, IdeaPluginDescriptorImpl> idToDescriptorMap = new THashMap<PluginId, IdeaPluginDescriptorImpl>();
    for (IdeaPluginDescriptorImpl descriptor : pluginDescriptors) {
      idToDescriptorMap.put(descriptor.getPluginId(), descriptor);
    }

    Arrays.sort(pluginDescriptors, getPluginDescriptorComparator(idToDescriptorMap));
    return pluginDescriptors;
  }

  private static void mergeOptionalConfigs(@NotNull Map<PluginId, IdeaPluginDescriptorImpl> descriptors) {
    final Map<PluginId, IdeaPluginDescriptorImpl> descriptorsWithModules = new THashMap<PluginId, IdeaPluginDescriptorImpl>(descriptors);
    addModulesAsDependents(descriptorsWithModules);
    for (IdeaPluginDescriptorImpl descriptor : descriptors.values()) {
      final Map<PluginId, IdeaPluginDescriptorImpl> optionalDescriptors = descriptor.getOptionalDescriptors();
      if (optionalDescriptors != null && !optionalDescriptors.isEmpty()) {
        for (Map.Entry<PluginId, IdeaPluginDescriptorImpl> entry: optionalDescriptors.entrySet()) {
          if (descriptorsWithModules.containsKey(entry.getKey())) {
            descriptor.mergeOptionalConfig(entry.getValue());
          }
        }
      }
    }
  }

  public static void initClassLoader(@NotNull ClassLoader parentLoader, @NotNull IdeaPluginDescriptorImpl descriptor) {
    final List<File> classPath = descriptor.getClassPath();
    final ClassLoader loader =
        createPluginClassLoader(classPath.toArray(new File[classPath.size()]), new ClassLoader[]{parentLoader}, descriptor);
    descriptor.setLoader(loader);
  }

  static BuildNumber getBuildNumber() {
    return Holder.ourBuildNumber;
  }

  private static boolean shouldLoadPlugin(@NotNull IdeaPluginDescriptor descriptor, @NotNull IdeaPluginDescriptor[] loaded) {
    final String idString = descriptor.getPluginId().getIdString();
    if (CORE_PLUGIN_ID.equals(idString)) {
      return true;
    }

    //noinspection HardCodedStringLiteral
    final String pluginId = System.getProperty("idea.load.plugins.id");
    if (pluginId == null) {
      if (descriptor instanceof IdeaPluginDescriptorImpl && !descriptor.isEnabled()) return false;

      if (!shouldLoadPlugins()) return false;
    }
    final List<String> pluginIds = pluginId == null ? null : StringUtil.split(pluginId, ",");

    final boolean checkModuleDependencies = !ourModulesToContainingPlugins.isEmpty() && !ourModulesToContainingPlugins.containsKey("com.intellij.modules.all");
    if (checkModuleDependencies && !hasModuleDependencies(descriptor)) {
      return false;
    }

    boolean shouldLoad;
    //noinspection HardCodedStringLiteral
    final String loadPluginCategory = System.getProperty("idea.load.plugins.category");
    if (loadPluginCategory != null) {
      shouldLoad = loadPluginCategory.equals(descriptor.getCategory());
    }
    else {
      if (pluginIds != null) {
        shouldLoad = pluginIds.contains(idString);
        if (!shouldLoad) {
          Map<PluginId,IdeaPluginDescriptor> map = new THashMap<PluginId, IdeaPluginDescriptor>();
          for (IdeaPluginDescriptor pluginDescriptor : loaded) {
            map.put(pluginDescriptor.getPluginId(), pluginDescriptor);
          }
          addModulesAsDependents(map);
          for (String id : pluginIds) {
            final IdeaPluginDescriptor descriptorFromProperty = map.get(PluginId.getId(id));
            if (descriptorFromProperty != null && isDependent(descriptorFromProperty, descriptor.getPluginId(), map, checkModuleDependencies)) {
              shouldLoad = true;
              break;
            }
          }
        }
      }
      else {
        shouldLoad = !getDisabledPlugins().contains(idString);
      }
      if (shouldLoad && descriptor instanceof IdeaPluginDescriptorImpl) {
        if (isIncompatible(descriptor)) return false;
      }
    }

    return shouldLoad;
  }

  static boolean isIncompatible(@NotNull IdeaPluginDescriptor descriptor) {
    return isIncompatible(descriptor, getBuildNumber());
  }

  public static boolean isIncompatible(@NotNull IdeaPluginDescriptor descriptor, @Nullable BuildNumber buildNumber) {
    if (buildNumber == null) {
      buildNumber = getBuildNumber();
    }

    try {
      if (!StringUtil.isEmpty(descriptor.getSinceBuild())) {
        BuildNumber sinceBuild = BuildNumber.fromString(descriptor.getSinceBuild(), descriptor.getName());
        if (sinceBuild.compareTo(buildNumber) > 0) {
          return true;
        }
      }

      if (!StringUtil.isEmpty(descriptor.getUntilBuild()) && !buildNumber.isSnapshot()) {
        BuildNumber untilBuild = BuildNumber.fromString(descriptor.getUntilBuild(), descriptor.getName());
        if (untilBuild.compareTo(buildNumber) < 0) {
          return true;
        }
      }
    }
    catch (RuntimeException ignored) { }

    return false;
  }

  public static boolean shouldSkipPlugin(@NotNull IdeaPluginDescriptor descriptor) {
    if (descriptor instanceof IdeaPluginDescriptorImpl) {
      IdeaPluginDescriptorImpl descriptorImpl = (IdeaPluginDescriptorImpl)descriptor;
      Boolean skipped = descriptorImpl.getSkipped();
      if (skipped != null) {
        return skipped.booleanValue();
      }
      boolean result = !shouldLoadPlugin(descriptor, ourPlugins) || isBrokenPlugin(descriptor);
      descriptorImpl.setSkipped(result);
      return result;
    }
    return !shouldLoadPlugin(descriptor, ourPlugins) || isBrokenPlugin(descriptor);
  }

  private static void initializePlugins(@Nullable StartupProgress progress) {
    configureExtensions();

    final IdeaPluginDescriptorImpl[] pluginDescriptors = loadDescriptors(progress);

    final Class callerClass = ReflectionUtil.findCallerClass(1);
    assert callerClass != null;
    final ClassLoader parentLoader = callerClass.getClassLoader();

    final List<IdeaPluginDescriptorImpl> result = new ArrayList<IdeaPluginDescriptorImpl>();
    final Map<PluginId, IdeaPluginDescriptorImpl> idToDescriptorMap = new THashMap<PluginId, IdeaPluginDescriptorImpl>();
    final Map<String, String> disabledPluginNames = new THashMap<String, String>();
    List<String> brokenPluginsList = new SmartList<String>();
    String errorMessage =
      fixDescriptors(pluginDescriptors, parentLoader, idToDescriptorMap, disabledPluginNames, brokenPluginsList, result);

    final Graph<PluginId> graph = createPluginIdGraph(idToDescriptorMap);
    final DFSTBuilder<PluginId> builder = new DFSTBuilder<PluginId>(graph);
    if (!builder.isAcyclic()) {
      if (!StringUtil.isEmptyOrSpaces(errorMessage)) {
        errorMessage += "<br>";
      }

      final String cyclePresentation;
      if (ApplicationManager.getApplication().isInternal()) {
        final List<String> cycles = new ArrayList<String>();
        builder.getSCCs().forEach(new TIntProcedure() {
          private int myTNumber;
          @Override
          public boolean execute(int size) {
            if (size > 1) {
              String cycle = "";
              for (int j = 0; j < size; j++) {
                cycle += builder.getNodeByTNumber(myTNumber + j).getIdString() + " ";
              }
              cycles.add(cycle);
            }
            myTNumber += size;
            return true;
          }
        });
        cyclePresentation = ": " + StringUtil.join(cycles, ";");
      }
      else {
        final Couple<PluginId> circularDependency = builder.getCircularDependency();
        final PluginId id = circularDependency.getFirst();
        final PluginId parentId = circularDependency.getSecond();
        cyclePresentation = id + "->" + parentId + "->...->" + id;
      }
      errorMessage += IdeBundle.message("error.plugins.should.not.have.cyclic.dependencies") + cyclePresentation;
    }

    prepareLoadingPluginsErrorMessage(errorMessage);

    final Comparator<PluginId> idComparator = builder.comparator();
    // sort descriptors according to plugin dependencies
    Collections.sort(result, new Comparator<IdeaPluginDescriptor>() {
      @Override
      public int compare(@NotNull IdeaPluginDescriptor o1, @NotNull IdeaPluginDescriptor o2) {
        return idComparator.compare(o1.getPluginId(), o2.getPluginId());
      }
    });

    for (int i = 0; i < result.size(); i++) {
      ourId2Index.put(result.get(i).getPluginId(), i);
    }

    int i = 0;
    for (final IdeaPluginDescriptorImpl pluginDescriptor : result) {
      if (pluginDescriptor.getPluginId().getIdString().equals(CORE_PLUGIN_ID) || pluginDescriptor.isUseCoreClassLoader()) {
        pluginDescriptor.setLoader(parentLoader);
      }
      else {
        final List<File> classPath = pluginDescriptor.getClassPath();
        final PluginId[] dependentPluginIds = pluginDescriptor.getDependentPluginIds();
        final ClassLoader[] parentLoaders = getParentLoaders(idToDescriptorMap, dependentPluginIds);

        ClassLoader pluginClassLoader = createPluginClassLoader(classPath.toArray(new File[classPath.size()]),
                                                                parentLoaders.length > 0 ? parentLoaders : new ClassLoader[] {parentLoader},
                                                                pluginDescriptor);
        pluginDescriptor.setLoader(pluginClassLoader);
      }

      if (progress != null) {
        progress.showProgress("", PLUGINS_PROGRESS_MAX_VALUE + (i++ / (float)result.size()) * 0.35f);
      }
    }

    registerExtensionPointsAndExtensions(Extensions.getRootArea(), result);
    Extensions.getRootArea().getExtensionPoint(Extensions.AREA_LISTENER_EXTENSION_POINT).registerExtension(new AreaListener() {
      @Override
      public void areaCreated(@NotNull String areaClass, @NotNull AreaInstance areaInstance) {
        registerExtensionPointsAndExtensions(Extensions.getArea(areaInstance), result);
      }

      @Override
      public void areaDisposing(@NotNull String areaClass, @NotNull AreaInstance areaInstance) {
      }
    });

    ourPlugins = pluginDescriptors;
  }

  @NotNull // used in upsource
  public static String fixDescriptors(@NotNull IdeaPluginDescriptorImpl[] pluginDescriptors,
                                      @NotNull ClassLoader parentLoader,
                                      @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idToDescriptorMap,
                                      @NotNull Map<String, String> disabledPluginNames,
                                      @NotNull List<String> brokenPluginsList,
                                      @NotNull List<IdeaPluginDescriptorImpl> result) {
    checkCanLoadPlugins(pluginDescriptors, parentLoader, disabledPluginNames, brokenPluginsList, result);

    String errorMessage = filterBadPlugins(result, disabledPluginNames);

    if (!brokenPluginsList.isEmpty()) {
      if (!StringUtil.isEmptyOrSpaces(errorMessage)) {
        errorMessage += "<br>";
      }
      errorMessage += "Following plugins are incompatible with current IDE build: " + StringUtil.join(brokenPluginsList, ", ")
                      + "<br>\n" + StringUtil.notNullize(errorMessage);
    }

    fixDependencies(result,idToDescriptorMap);
    return errorMessage;
  }

  private static void checkCanLoadPlugins(@NotNull IdeaPluginDescriptorImpl[] pluginDescriptors,
                                          @NotNull ClassLoader parentLoader,
                                          @NotNull Map<String, String> disabledPluginNames,
                                          @NotNull List<String> brokenPluginsList,
                                          @NotNull List<IdeaPluginDescriptorImpl> result) {
    for (IdeaPluginDescriptorImpl descriptor : pluginDescriptors) {
      boolean shouldLoad = shouldLoadPlugin(descriptor, pluginDescriptors);
      if (shouldLoad) {
        if (isBrokenPlugin(descriptor)) {
          brokenPluginsList.add(descriptor.getName());
          shouldLoad = false;
        }
      }

      if (shouldLoad) {
        final List<String> modules = descriptor.getModules();
        if (modules != null) {
          for (String module : modules) {
            if (!ourModulesToContainingPlugins.containsKey(module)) {
              ourModulesToContainingPlugins.put(module, descriptor);
            }
          }
        }
        result.add(descriptor);
      }
      else {
        descriptor.setEnabled(false);
        disabledPluginNames.put(descriptor.getPluginId().getIdString(), descriptor.getName());
        initClassLoader(parentLoader, descriptor);
      }
    }
  }

  private static void fixDependencies(@NotNull List<IdeaPluginDescriptorImpl> result,
                                      @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idToDescriptorMap) {
    for (IdeaPluginDescriptorImpl descriptor : result) {
      idToDescriptorMap.put(descriptor.getPluginId(), descriptor);
    }

    final IdeaPluginDescriptor corePluginDescriptor = idToDescriptorMap.get(PluginId.getId(CORE_PLUGIN_ID));
    assert corePluginDescriptor != null : CORE_PLUGIN_ID + " not found; platform prefix is " + System.getProperty(PlatformUtils.PLATFORM_PREFIX_KEY);
    for (IdeaPluginDescriptorImpl descriptor : result) {
      if (descriptor != corePluginDescriptor) {
        descriptor.insertDependency(corePluginDescriptor);
      }
    }

    mergeOptionalConfigs(idToDescriptorMap);
    addModulesAsDependents(idToDescriptorMap);
  }

  private static void registerExtensionPointsAndExtensions(@NotNull ExtensionsArea area, @NotNull List<IdeaPluginDescriptorImpl> loadedPlugins) {
    for (IdeaPluginDescriptorImpl descriptor : loadedPlugins) {
      descriptor.registerExtensionPoints(area);
    }

    ExtensionPoint[] extensionPoints = area.getExtensionPoints();
    Set<String> epNames = new THashSet<String>(extensionPoints.length);
    for (ExtensionPoint point : extensionPoints) {
      epNames.add(point.getName());
    }

    for (IdeaPluginDescriptorImpl descriptor : loadedPlugins) {
      for (String epName : epNames) {
        descriptor.registerExtensions(area, epName);
      }
    }
  }

  /**
   * Load extensions points and extensions from a configuration file in plugin.xml format
   * @param pluginRoot jar file or directory which contains the configuration file
   * @param fileName name of the configuration file located in 'META-INF' directory under {@code pluginRoot}
   * @param area area which extension points and extensions should be registered (e.g. {@link Extensions#getRootArea()} for application-level extensions)
   */
  public static void registerExtensionPointAndExtensions(@NotNull File pluginRoot, @NotNull String fileName, @NotNull ExtensionsArea area) {
    IdeaPluginDescriptorImpl descriptor;
    if (pluginRoot.isDirectory()) {
      descriptor = loadDescriptorFromDir(pluginRoot, fileName);
    }
    else {
      descriptor = loadDescriptorFromJar(pluginRoot, fileName);
    }
    if (descriptor != null) {
      registerExtensionPointsAndExtensions(area, Collections.singletonList(descriptor));
    }
    else {
      getLogger().error("Cannot load " + fileName + " from " + pluginRoot);
    }
  }

  private static void initPlugins(@Nullable StartupProgress progress) {
    long start = System.currentTimeMillis();
    try {
      initializePlugins(progress);
    }
    catch (RuntimeException e) {
      getLogger().error(e);
      throw e;
    }
    getLogger().info(ourPlugins.length + " plugins initialized in " + (System.currentTimeMillis() - start) + " ms");
    logPlugins();
    ClassUtilCore.clearJarURLCache();
  }

  private static class LoggerHolder {
    private static final Logger ourLogger = Logger.getInstance("#com.intellij.ide.plugins.PluginManager");
  }

  private static class IdeaLogProvider implements LogProvider {
    @Override
    public void error(String message) {
      getLogger().error(message);
    }

    @Override
    public void error(String message, Throwable t) {
      getLogger().error(message, t);
    }

    @Override
    public void error(Throwable t) {
      getLogger().error(t);
    }

    @Override
    public void warn(String message) {
      getLogger().info(message);
    }

    @Override
    public void warn(String message, Throwable t) {
      getLogger().info(message, t);
    }

    @Override
    public void warn(Throwable t) {
      getLogger().info(t);
    }
  }
}
