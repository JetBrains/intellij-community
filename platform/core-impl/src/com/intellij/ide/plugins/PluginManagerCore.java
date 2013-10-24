/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.PlatformUtilsCore;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.lang.JarMemoryLoader;
import com.intellij.util.xmlb.XmlSerializationException;
import gnu.trove.THashMap;
import org.jdom.Document;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class PluginManagerCore {
  @NonNls public static final String DISABLED_PLUGINS_FILENAME = "disabled_plugins.txt";
  @NonNls public static final String CORE_PLUGIN_ID = "com.intellij";
  @NonNls public static final String META_INF = "META-INF";
  @NonNls public static final String PLUGIN_XML = "plugin.xml";
  public static final float PLUGINS_PROGRESS_MAX_VALUE = 0.3f;
  static final Map<PluginId,Integer> ourId2Index = new THashMap<PluginId, Integer>();
  @NonNls static final String MODULE_DEPENDENCY_PREFIX = "com.intellij.module";
  static final List<String> ourAvailableModules = new ArrayList<String>();
  static final PluginClassCache ourPluginClasses = new PluginClassCache();
  @NonNls static final String SPECIAL_IDEA_PLUGIN = "IDEA CORE";
  static final String DISABLE = "disable";
  static final String ENABLE = "enable";
  static final String EDIT = "edit";
  @NonNls private static final String PROPERTY_PLUGIN_PATH = "plugin.path";
  static List<String> ourDisabledPlugins = null;
  static IdeaPluginDescriptor[] ourPlugins;
  static String myPluginError = null;
  static List<String> myPlugins2Disable = null;
  static LinkedHashSet<String> myPlugins2Enable = null;
  public static String BUILD_NUMBER;
  private static BuildNumber ourBuildNumber;

  /**
   * do not call this method during bootstrap, should be called in a copy of PluginManager, loaded by IdeaClassLoader
   */
  public static synchronized IdeaPluginDescriptor[] getPlugins() {
    if (ourPlugins == null) {
      initPlugins(null);
    }
    return ourPlugins;
  }

  public static void loadDisabledPlugins(final String configPath, final Collection<String> disabledPlugins) {
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

  static boolean isUnitTestMode() {
    final Application app = ApplicationManager.getApplication();
    return app != null && app.isUnitTestMode();
  }

  public static void savePluginsList(Collection<String> ids, boolean append, File plugins) throws IOException {
    if (!plugins.isFile()) {
      FileUtil.ensureCanCreateFile(plugins);
    }
    PrintWriter printWriter = new PrintWriter(new BufferedWriter(new FileWriter(plugins, append)));
    try {
      for (String id : ids) {
        printWriter.println(id);
      }
      printWriter.flush();
    }
    finally {
      printWriter.close();
    }
  }

  public static boolean disablePlugin(String id) {
    if (getDisabledPlugins().contains(id)) return false;
    getDisabledPlugins().add(id);
    try {
      saveDisabledPlugins(getDisabledPlugins(), false);
    }
    catch (IOException e) {
      return false;
    }
    return true;
  }

  public static boolean enablePlugin(String id) {
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

  public static void saveDisabledPlugins(Collection<String> ids, boolean append) throws IOException {
    File plugins = new File(PathManager.getConfigPath(), DISABLED_PLUGINS_FILENAME);
    savePluginsList(ids, append, plugins);
    ourDisabledPlugins = null;
  }

  public static Logger getLogger() {
    return LoggerHolder.ourLogger;
  }

  public static int getPluginLoadingOrder(PluginId id) {
    return ourId2Index.get(id);
  }

  public static boolean isModuleDependency(final PluginId dependentPluginId) {
    return dependentPluginId.getIdString().startsWith(MODULE_DEPENDENCY_PREFIX);
  }

  public static void checkDependants(final IdeaPluginDescriptor pluginDescriptor,
                                     final Function<PluginId, IdeaPluginDescriptor> pluginId2Descriptor,
                                     final Condition<PluginId> check) {
    checkDependants(pluginDescriptor, pluginId2Descriptor, check, new HashSet<PluginId>());
  }

  private static boolean checkDependants(final IdeaPluginDescriptor pluginDescriptor,
                                         final Function<PluginId, IdeaPluginDescriptor> pluginId2Descriptor,
                                         final Condition<PluginId> check,
                                         final Set<PluginId> processed) {
    processed.add(pluginDescriptor.getPluginId());
    final PluginId[] dependentPluginIds = pluginDescriptor.getDependentPluginIds();
    final Set<PluginId> optionalDependencies = new HashSet<PluginId>(Arrays.asList(pluginDescriptor.getOptionalDependentPluginIds()));
    for (final PluginId dependentPluginId : dependentPluginIds) {
      if (processed.contains(dependentPluginId)) continue;

      // TODO[yole] should this condition be a parameter?
      if (isModuleDependency(dependentPluginId) && (ourAvailableModules.isEmpty() || ourAvailableModules.contains(dependentPluginId.getIdString()))) {
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

  static boolean isDependent(final IdeaPluginDescriptor descriptor,
                             final PluginId on,
                             Map<PluginId, IdeaPluginDescriptor> map,
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

  static boolean hasModuleDependencies(final IdeaPluginDescriptor descriptor) {
    final PluginId[] dependentPluginIds = descriptor.getDependentPluginIds();
    for (PluginId dependentPluginId : dependentPluginIds) {
      if (isModuleDependency(dependentPluginId)) {
        return true;
      }
    }
    return false;
  }

  static boolean shouldLoadPlugins() {
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

  static void configureExtensions() {
    Extensions.setLogProvider(new IdeaLogProvider());
    Extensions.registerAreaClass(ExtensionAreas.IDEA_PROJECT, null);
    Extensions.registerAreaClass(ExtensionAreas.IDEA_MODULE, ExtensionAreas.IDEA_PROJECT);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  static Method getAddUrlMethod(final ClassLoader loader) throws NoSuchMethodException {
    if (loader instanceof URLClassLoader) {
      final Method addUrlMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
      addUrlMethod.setAccessible(true);
      return addUrlMethod;
    }

    return loader.getClass().getDeclaredMethod("addURL", URL.class);
  }

  @Nullable
  static ClassLoader createPluginClassLoader(@NotNull File[] classPath,
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
      catch (NoSuchMethodException e) {
        e.printStackTrace();
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

  public static boolean isPluginClass(String className) {
    return ourPlugins != null && getPluginByClassName(className) != null;
  }

  static void logPlugins() {
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

  static ClassLoader[] getParentLoaders(Map<PluginId, ? extends IdeaPluginDescriptor> idToDescriptorMap, PluginId[] pluginIds) {
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

  static int countPlugins(String pluginsPath) {
    File configuredPluginsDir = new File(pluginsPath);
    if (configuredPluginsDir.exists()) {
      String[] list = configuredPluginsDir.list();
      if (list != null) {
        return list.length;
      }
    }
    return 0;
  }

  static Collection<URL> getClassLoaderUrls() {
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

  static void prepareLoadingPluginsErrorMessage(final String errorMessage) {
    if (errorMessage != null) {
      if (!ApplicationManager.getApplication().isHeadlessEnvironment() && !ApplicationManager.getApplication().isUnitTestMode()) {
        if (myPluginError == null) {
          myPluginError = errorMessage;
        }
        else {
          myPluginError += "\n" + errorMessage;
        }
      } else {
        getLogger().error(errorMessage);
      }
    }
  }

  private static void addModulesAsDependents(Map<PluginId, ? super IdeaPluginDescriptorImpl> map) {
    for (String module : ourAvailableModules) {
      // fake plugin descriptors to satisfy dependencies
      map.put(PluginId.getId(module), new IdeaPluginDescriptorImpl());
    }
  }

  static Comparator<IdeaPluginDescriptor> getPluginDescriptorComparator(Map<PluginId, IdeaPluginDescriptorImpl> idToDescriptorMap) {
    final Graph<PluginId> graph = createPluginIdGraph(idToDescriptorMap);
    final DFSTBuilder<PluginId> builder = new DFSTBuilder<PluginId>(graph);
    /*
    if (!builder.isAcyclic()) {
      final Pair<String,String> circularDependency = builder.getCircularDependency();
      throw new Exception("Cyclic dependencies between plugins are not allowed: \"" + circularDependency.getFirst() + "\" and \"" + circularDependency.getSecond() + "");
    }
    */
    final Comparator<PluginId> idComparator = builder.comparator();
    return new Comparator<IdeaPluginDescriptor>() {
      @Override
      public int compare(IdeaPluginDescriptor o1, IdeaPluginDescriptor o2) {
        return idComparator.compare(o1.getPluginId(), o2.getPluginId());
      }
    };
  }

  private static Graph<PluginId> createPluginIdGraph(final Map<PluginId, IdeaPluginDescriptorImpl> idToDescriptorMap) {
    final List<PluginId> ids = new ArrayList<PluginId>(idToDescriptorMap.keySet());
    // this magic ensures that the dependent plugins always follow their dependencies in lexicographic order
    // needed to make sure that extensions are always in the same order
    Collections.sort(ids, new Comparator<PluginId>() {
      @Override
      public int compare(PluginId o1, PluginId o2) {
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
        ArrayList<PluginId> plugins = new ArrayList<PluginId>();
        for (PluginId dependentPluginId : descriptor.getDependentPluginIds()) {
          // check for missing optional dependency
          if (idToDescriptorMap.containsKey(dependentPluginId)) {
            plugins.add(dependentPluginId);
          }
        }
        return plugins.iterator();
      }
    }));
  }

  static IdeaPluginDescriptorImpl[] findCorePlugin(IdeaPluginDescriptorImpl[] pluginDescriptors) {
    for (IdeaPluginDescriptorImpl descriptor : pluginDescriptors) {
      if (CORE_PLUGIN_ID.equals(descriptor.getPluginId().getIdString())) {
        return new IdeaPluginDescriptorImpl[] {descriptor};
      }
    }
    return IdeaPluginDescriptorImpl.EMPTY_ARRAY;
  }

  @Nullable
  static IdeaPluginDescriptorImpl loadDescriptorFromDir(final File file, @NonNls String fileName) {
    IdeaPluginDescriptorImpl descriptor = null;
    File descriptorFile = new File(file, META_INF + File.separator + fileName);
    if (descriptorFile.exists()) {
      descriptor = new IdeaPluginDescriptorImpl(file);

      try {
        descriptor.readExternal(descriptorFile.toURI().toURL());
      }
      catch (Exception e) {
        System.err.println("Cannot load: " + descriptorFile.getAbsolutePath());
        e.printStackTrace();
      }
    }
    return descriptor;
  }

  @Nullable
  static IdeaPluginDescriptorImpl loadDescriptorFromJar(File file, @NonNls String fileName) {
    try {
      URI fileURL = file.toURI();
      URL jarURL = new URL(
        "jar:" + StringUtil.replace(fileURL.toASCIIString(), "!", "%21") + "!/META-INF/" + fileName
      );

      IdeaPluginDescriptorImpl descriptor = new IdeaPluginDescriptorImpl(file);
      FileInputStream in = new FileInputStream(file);
      ZipInputStream zipStream = new ZipInputStream(in);
      try {
        ZipEntry entry = zipStream.getNextEntry();
        if (entry.getName().equals(JarMemoryLoader.SIZE_ENTRY)) {
          entry = zipStream.getNextEntry();
          if (entry.getName().equals("META-INF/" + fileName)) {
            byte[] content = FileUtil.loadBytes(zipStream, (int)entry.getSize());
            Document document = JDOMUtil.loadDocument(new ByteArrayInputStream(content));
            descriptor.readExternal(document, jarURL);
            return descriptor;
          }
        }
      }
      finally {
        zipStream.close();
        in.close();
      }

      descriptor.readExternal(jarURL);
      return descriptor;
    }
    catch (XmlSerializationException e) {
      getLogger().info("Cannot load " + file, e);
      prepareLoadingPluginsErrorMessage("Plugin file " + file.getName() + " contains invalid plugin descriptor file.");
    }
    catch (FileNotFoundException e) {
      return null;
    }
    catch (Exception e) {
      getLogger().info("Cannot load " + file, e);
    }
    catch (Throwable e) {
      getLogger().info("Cannot load " + file, e);
    }

    return null;
  }

  @Nullable
  public static IdeaPluginDescriptorImpl loadDescriptorFromJar(File file) {
    return loadDescriptorFromJar(file, PLUGIN_XML);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  @Nullable
  public static IdeaPluginDescriptorImpl loadDescriptor(final File file, @NonNls final String fileName) {
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
         public int compare(File o1, File o2) {
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

    if (descriptor != null && !descriptor.getOptionalConfigs().isEmpty()) {
      final Map<PluginId, IdeaPluginDescriptorImpl> descriptors = new HashMap<PluginId, IdeaPluginDescriptorImpl>(descriptor.getOptionalConfigs().size());
      for (Map.Entry<PluginId, String> entry: descriptor.getOptionalConfigs().entrySet()) {
        String optionalDescriptorName = entry.getValue();
        assert !Comparing.equal(fileName, optionalDescriptorName) : "recursive dependency: "+ fileName;

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
        if (optionalDescriptor != null) {
          descriptors.put(entry.getKey(), optionalDescriptor);
        }
        else {
          getLogger().info("Cannot find optional descriptor " + optionalDescriptorName);
        }
      }
      descriptor.setOptionalDescriptors(descriptors);
    }
    return descriptor;
  }

  public static void loadDescriptors(String pluginsPath,
                                     List<IdeaPluginDescriptorImpl> result,
                                     @Nullable StartupProgress progress,
                                     int pluginsCount) {
    final File pluginsHome = new File(pluginsPath);
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

  @Nullable
  static String filterBadPlugins(List<? extends IdeaPluginDescriptor> result, final Map<String, String> disabledPluginNames) {
    final Map<PluginId, IdeaPluginDescriptor> idToDescriptorMap = new HashMap<PluginId, IdeaPluginDescriptor>();
    final StringBuffer message = new StringBuffer();
    boolean pluginsWithoutIdFound = false;
    for (Iterator<? extends IdeaPluginDescriptor> it = result.iterator(); it.hasNext();) {
      final IdeaPluginDescriptor descriptor = it.next();
      final PluginId id = descriptor.getPluginId();
      if (id == null) {
        pluginsWithoutIdFound = true;
      }
      if (idToDescriptorMap.containsKey(id)) {
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
    final List<String> disabledPluginIds = new ArrayList<String>();
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
    return null;
  }

  static void loadDescriptorsFromClassPath(@NotNull List<IdeaPluginDescriptorImpl> result, @Nullable StartupProgress progress) {
    Collection<URL> urls = getClassLoaderUrls();
    String platformPrefix = System.getProperty(PlatformUtilsCore.PLATFORM_PREFIX_KEY);
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

  @SuppressWarnings("deprecation")
  private static String decodeUrl(String file) {
    String quotePluses = StringUtil.replace(file, "+", "%2B");
    return URLDecoder.decode(quotePluses);
  }

  static void loadDescriptorsFromProperty(final List<IdeaPluginDescriptorImpl> result) {
    final String pathProperty = System.getProperty(PROPERTY_PLUGIN_PATH);
    if (pathProperty == null) return;

    for (StringTokenizer t = new StringTokenizer(pathProperty, File.pathSeparator); t.hasMoreTokens();) {
      String s = t.nextToken();
      final IdeaPluginDescriptorImpl ideaPluginDescriptor = loadDescriptor(new File(s), PLUGIN_XML);
      if (ideaPluginDescriptor != null) {
        result.add(ideaPluginDescriptor);
      }
    }
  }

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

    IdeaPluginDescriptorImpl[] pluginDescriptors = result.toArray(new IdeaPluginDescriptorImpl[result.size()]);
    try {
      Arrays.sort(pluginDescriptors, new PluginDescriptorComparator(pluginDescriptors));
    }
    catch (Exception e) {
      prepareLoadingPluginsErrorMessage(IdeBundle.message("error.plugins.were.not.loaded", e.getMessage()));
      getLogger().info(e);
      return findCorePlugin(pluginDescriptors);
    }
    return pluginDescriptors;
  }

  static void mergeOptionalConfigs(Map<PluginId, IdeaPluginDescriptorImpl> descriptors) {
    final Map<PluginId, IdeaPluginDescriptorImpl> descriptorsWithModules = new HashMap<PluginId, IdeaPluginDescriptorImpl>(descriptors);
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
    if (ourBuildNumber == null) {
      ourBuildNumber = BuildNumber.fromString(System.getProperty("idea.plugins.compatible.build"));
      if (ourBuildNumber == null) {
        ourBuildNumber = BUILD_NUMBER == null ? null : BuildNumber.fromString(BUILD_NUMBER);
        if (ourBuildNumber == null) {
          ourBuildNumber = BuildNumber.fallback();
        }
      }
    }
    return ourBuildNumber;
  }

  static boolean shouldSkipPlugin(final IdeaPluginDescriptor descriptor, IdeaPluginDescriptor[] loaded) {
    final String idString = descriptor.getPluginId().getIdString();
    if (idString.equals(CORE_PLUGIN_ID)) {
      return false;
    }

    //noinspection HardCodedStringLiteral
    final String pluginId = System.getProperty("idea.load.plugins.id");
    if (pluginId == null) {
      if (descriptor instanceof IdeaPluginDescriptorImpl && !descriptor.isEnabled()) return true;

      if (!shouldLoadPlugins()) return true;
    }
    final List<String> pluginIds = pluginId == null ? null : StringUtil.split(pluginId, ",");

    final boolean checkModuleDependencies = !ourAvailableModules.isEmpty() && !ourAvailableModules.contains("com.intellij.modules.all");
    if (checkModuleDependencies && !hasModuleDependencies(descriptor)) {
      return true;
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
          Map<PluginId,IdeaPluginDescriptor> map = new HashMap<PluginId, IdeaPluginDescriptor>();
          for (final IdeaPluginDescriptor pluginDescriptor : loaded) {
            map.put(pluginDescriptor.getPluginId(), pluginDescriptor);
          }
          addModulesAsDependents(map);
          final IdeaPluginDescriptor descriptorFromProperty = map.get(PluginId.getId(pluginId));
          shouldLoad = descriptorFromProperty != null && isDependent(descriptorFromProperty, descriptor.getPluginId(), map,
                                                                     checkModuleDependencies);
        }
      } else {
        shouldLoad = !getDisabledPlugins().contains(idString);
      }
      if (shouldLoad && descriptor instanceof IdeaPluginDescriptorImpl) {
        if (isIncompatible(descriptor)) return true;
      }
    }

    return !shouldLoad;
  }

  public static boolean isIncompatible(final IdeaPluginDescriptor descriptor) {
    try {
      BuildNumber buildNumber = getBuildNumber();

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

  public static boolean shouldSkipPlugin(final IdeaPluginDescriptor descriptor) {
    if (descriptor instanceof IdeaPluginDescriptorImpl) {
      IdeaPluginDescriptorImpl descriptorImpl = (IdeaPluginDescriptorImpl)descriptor;
      Boolean skipped = descriptorImpl.getSkipped();
      if (skipped != null) {
        return skipped.booleanValue();
      }
      boolean result = shouldSkipPlugin(descriptor, ourPlugins);
      descriptorImpl.setSkipped(result);
      return result;
    }
    return shouldSkipPlugin(descriptor, ourPlugins);
  }

  static void initializePlugins(@Nullable StartupProgress progress) {
    configureExtensions();

    final IdeaPluginDescriptorImpl[] pluginDescriptors = loadDescriptors(progress);

    final Class callerClass = ReflectionUtil.findCallerClass(1);
    assert callerClass != null;
    final ClassLoader parentLoader = callerClass.getClassLoader();

    final List<IdeaPluginDescriptorImpl> result = new ArrayList<IdeaPluginDescriptorImpl>();
    final HashMap<String, String> disabledPluginNames = new HashMap<String, String>();
    for (IdeaPluginDescriptorImpl descriptor : pluginDescriptors) {
      if (descriptor.getPluginId().getIdString().equals(CORE_PLUGIN_ID)) {
        final List<String> modules = descriptor.getModules();
        if (modules != null) {
          ourAvailableModules.addAll(modules);
        }
      }

      if (!shouldSkipPlugin(descriptor, pluginDescriptors)) {
        result.add(descriptor);
      }
      else {
        descriptor.setEnabled(false);
        disabledPluginNames.put(descriptor.getPluginId().getIdString(), descriptor.getName());
        initClassLoader(parentLoader, descriptor);
      }
    }

    prepareLoadingPluginsErrorMessage(filterBadPlugins(result, disabledPluginNames));

    final Map<PluginId, IdeaPluginDescriptorImpl> idToDescriptorMap = new HashMap<PluginId, IdeaPluginDescriptorImpl>();
    for (final IdeaPluginDescriptorImpl descriptor : result) {
      idToDescriptorMap.put(descriptor.getPluginId(), descriptor);
    }

    final IdeaPluginDescriptor corePluginDescriptor = idToDescriptorMap.get(PluginId.getId(CORE_PLUGIN_ID));
    assert corePluginDescriptor != null : CORE_PLUGIN_ID + " not found; platform prefix is " + System.getProperty(PlatformUtilsCore.PLATFORM_PREFIX_KEY);
    for (IdeaPluginDescriptorImpl descriptor : result) {
      if (descriptor != corePluginDescriptor) {
        descriptor.insertDependency(corePluginDescriptor);
      }
    }

    mergeOptionalConfigs(idToDescriptorMap);

    // sort descriptors according to plugin dependencies
    Collections.sort(result, getPluginDescriptorComparator(idToDescriptorMap));

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

        final ClassLoader pluginClassLoader = createPluginClassLoader(classPath.toArray(new File[classPath.size()]),
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

  private static void registerExtensionPointsAndExtensions(ExtensionsArea area, List<IdeaPluginDescriptorImpl> loadedPlugins) {
    for (IdeaPluginDescriptorImpl descriptor : loadedPlugins) {
      descriptor.registerExtensionPoints(area);
    }

    Set<String> epNames = ContainerUtil.newHashSet();
    for (ExtensionPoint point : area.getExtensionPoints()) {
      epNames.add(point.getName());
    }

    for (IdeaPluginDescriptorImpl descriptor : loadedPlugins) {
      for (String epName : epNames) {
        descriptor.registerExtensions(area, epName);
      }
    }
  }

  public static void initPlugins(@Nullable StartupProgress progress) {
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
