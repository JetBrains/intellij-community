package com.intellij.ide.plugins;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.ide.startup.StartupActionScriptManager;
import com.intellij.idea.Main;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.application.impl.PluginsFacade;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.LogProvider;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.lang.UrlClassLoader;
import com.intellij.util.text.StringTokenizer;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import sun.reflect.Reflection;

import javax.swing.*;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.util.*;
import java.util.regex.Pattern;

/**
 * @author mike
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"}) // No logger is loaded at this time so we have to use these.
public class PluginManager {
  @SuppressWarnings({"NonConstantLogger"}) //Logger is lasy-initialized in order not to use it outside the appClassLoader
  private static Logger ourLogger = null;
  @NonNls public static final String AREA_IDEA_PROJECT = "IDEA_PROJECT";
  @NonNls public static final String AREA_IDEA_MODULE = "IDEA_MODULE";
  @NonNls private static final String PROPERTY_IGNORE_CLASSPATH = "ignore.classpath";
  @NonNls private static final String PROPERTY_PLUGIN_PATH = "plugin.path";
  private static final Object PLUGIN_CLASSES_LOCK = new Object();
  private static String myPluginError = null;
  @NonNls private static final String CORE_PLUGIN_ID = "com.intellij";

  @NonNls public static final String DISABLED_PLUGINS_FILENAME = "disabled_plugins.txt";

  private static List<String> ourDisabledPlugins = null;

  static final Object lock = new Object();

  private static String ourBuildNumber;
  @NonNls private static final String PLUGIN_XML = "plugin.xml";
  @NonNls private static final String FILE_CACHE = "fileCache";
  @NonNls private static final String URL_CACHE = "urlCache";
  @NonNls private static final String META_INF = "META-INF";

  private static Logger getLogger() {
    if (ourLogger == null) {
      ourLogger = Logger.getInstance("#com.intellij.ide.plugins.PluginManager");
    }
    return ourLogger;
  }

  // these fields are accessed via Reflection, so their names must not be changed by the obfuscator
  // do not make them private cause in this case they will be scrambled
  @SuppressWarnings({"WeakerAccess"}) static String[] ourArguments;
  @SuppressWarnings({"WeakerAccess"}) static String ourMainClass;
  @SuppressWarnings({"WeakerAccess"}) static String ourMethodName;

  public static void initPluginClasses () {
    synchronized(lock) {
      File file = new File (getPluginClassesPath());
      if (file.exists())
        file.delete();
    }
  }

  @NonNls
  static String getPluginClassesPath() {
    return PathManagerEx.getPluginTempPath() + File.separator + "plugin.classes";
  }

  private static class Facade extends PluginsFacade {
    public IdeaPluginDescriptor getPlugin(PluginId id) {
      return PluginManager.getPlugin(id);
    }

    public IdeaPluginDescriptor[] getPlugins() {
      return PluginManager.getPlugins();
    }
  }

  private static IdeaPluginDescriptorImpl[] ourPlugins;
  private static Map<String, PluginId> ourPluginClasses;

  public static void main(final String[] args, final String mainClass, final String methodName) {
    main(args, mainClass, methodName, new ArrayList<URL>());
  }

  public static void main(final String[] args, final String mainClass, final String methodName, List<URL> classpathElements) {
    ourArguments = args;
    ourMainClass = mainClass;
    ourMethodName = methodName;

    final PluginManager pluginManager = new PluginManager();
    pluginManager.bootstrap(classpathElements);
  }

  /**
   * do not call this method during bootstrap, should be called in a copy of PluginManager, loaded by IdeaClassLoader
   */
  public synchronized static IdeaPluginDescriptor[] getPlugins() {
    if (ourPlugins == null) {
      initializePlugins();
      clearJarURLCache();
    }
    return ourPlugins;
  }

  private static void initializePlugins() {
    configureExtensions();
    
    final IdeaPluginDescriptorImpl[] pluginDescriptors = loadDescriptors();

    final Class callerClass = Reflection.getCallerClass(1);
    final ClassLoader parentLoader = callerClass.getClassLoader();

    final List<IdeaPluginDescriptorImpl> result = new ArrayList<IdeaPluginDescriptorImpl>();
    for (IdeaPluginDescriptorImpl descriptor : pluginDescriptors) {
      if (!shouldSkipPlugin(descriptor)) {
        result.add(descriptor);
      } else {
        descriptor.setEnabled(false);
        final List<File> classPath = descriptor.getClassPath();
        descriptor
          .setLoader(createPluginClassLoader(classPath.toArray(new File[classPath.size()]), new ClassLoader[]{parentLoader}, descriptor));
      }
    }

    prepareLoadingPluginsErrorMessage(filterBadPlugins(result));

    final Map<PluginId, IdeaPluginDescriptorImpl> idToDescriptorMap = new HashMap<PluginId, IdeaPluginDescriptorImpl>();
    for (final IdeaPluginDescriptorImpl descriptor : result) {
      idToDescriptorMap.put(descriptor.getPluginId(), descriptor);
    }

    final IdeaPluginDescriptor corePluginDescriptor = idToDescriptorMap.get(PluginId.getId(CORE_PLUGIN_ID));
    assert corePluginDescriptor != null;
    for (IdeaPluginDescriptorImpl descriptor : result) {
      if (descriptor != corePluginDescriptor) {
        descriptor.insertDependency(corePluginDescriptor);
      }
    }
    
    mergeOptionalConfigs(idToDescriptorMap);

    // sort descriptors according to plugin dependencies
    Collections.sort(result, getPluginDescriptorComparator(idToDescriptorMap));

    for (final IdeaPluginDescriptorImpl pluginDescriptor : result) {
      final List<File> classPath = pluginDescriptor.getClassPath();
      final PluginId[] dependentPluginIds = pluginDescriptor.getDependentPluginIds();
      final ClassLoader[] parentLoaders = getParentLoaders(idToDescriptorMap, dependentPluginIds);

      final ClassLoader pluginClassLoader = createPluginClassLoader(classPath.toArray(new File[classPath.size()]),
                                                                    parentLoaders.length > 0 ? parentLoaders : new ClassLoader[] {parentLoader},
                                                                    pluginDescriptor);
      pluginDescriptor.setLoader(pluginClassLoader);
      pluginDescriptor.registerExtensions();
    }

    ourPlugins = pluginDescriptors;
  }

  private static void mergeOptionalConfigs(Map<PluginId, IdeaPluginDescriptorImpl> descriptors) {
    for (IdeaPluginDescriptorImpl descriptor : descriptors.values()) {
      final Map<PluginId, IdeaPluginDescriptorImpl> optionalDescriptors = descriptor.getOptionalDescriptors();
      if (optionalDescriptors != null && !optionalDescriptors.isEmpty()) {
        for (Map.Entry<PluginId, IdeaPluginDescriptorImpl> entry: optionalDescriptors.entrySet()) {
          if (descriptors.containsKey(entry.getKey())) {
            descriptor.mergeOptionalConfig(entry.getValue());  
          }
        }
      }
    }
  }

  private static void prepareLoadingPluginsErrorMessage(final String errorMessage) {
    if (errorMessage != null) {
      if (!Main.isHeadless()) {
        myPluginError = errorMessage;
      } else {
        getLogger().error(errorMessage);
      }
    }
  }

  private static void configureExtensions() {
    Extensions.setLogProvider(new IdeaLogProvider());
    Extensions.registerAreaClass(AREA_IDEA_PROJECT, null);
    Extensions.registerAreaClass(AREA_IDEA_MODULE, AREA_IDEA_PROJECT);
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

  public static boolean shouldSkipPlugin(final IdeaPluginDescriptor descriptor) {
    final String idString = descriptor.getPluginId().getIdString();
    if (idString.equals(CORE_PLUGIN_ID)) {
      return false;
    }

    if (descriptor instanceof IdeaPluginDescriptorImpl && !((IdeaPluginDescriptorImpl)descriptor).isEnabled()) return true;

    if (!shouldLoadPlugins()) return true;

    boolean shouldLoad;
    //noinspection HardCodedStringLiteral
    final String loadPluginCategory = System.getProperty("idea.load.plugins.category");
    if (loadPluginCategory != null) {
      shouldLoad = loadPluginCategory.equals(descriptor.getCategory());
    }
    else {
      //noinspection HardCodedStringLiteral
      final String pluginId = System.getProperty("idea.load.plugins.id");
      shouldLoad = pluginId == null || (descriptor.getPluginId() != null &&
                                  pluginId.equals(idString));
      if (shouldLoad) {
        shouldLoad = !getDisabledPlugins().contains(idString);
      }
      if (shouldLoad && descriptor instanceof IdeaPluginDescriptorImpl) {
        if (isIncompatible(descriptor)) return true;
      }
    }


    return !shouldLoad;
  }

  public static boolean isIncompatible(final IdeaPluginDescriptor descriptor) {
    final String buildNumber = getBuildNumber();
    if (buildNumber != null) {
      final String sinceBuild = ((IdeaPluginDescriptorImpl)descriptor).getSinceBuild();
      try {
        Integer.parseInt(sinceBuild);
        if (sinceBuild.compareToIgnoreCase(buildNumber) > 0) {
          return true;
        }
      }
      catch (NumberFormatException e) {
        //skip invalid numbers
      }

      final String untilBuild = ((IdeaPluginDescriptorImpl)descriptor).getUntilBuild();
      try {
        Integer.parseInt(untilBuild);
        if (untilBuild.compareToIgnoreCase(buildNumber) < 0) {
          return true;
        }
      }
      catch (NumberFormatException e) {
        //skip invalid numbers
      }
    }
    return false;
  }

  private static Comparator<IdeaPluginDescriptor> getPluginDescriptorComparator(Map<PluginId, IdeaPluginDescriptorImpl> idToDescriptorMap) {
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
      public int compare(IdeaPluginDescriptor o1, IdeaPluginDescriptor o2) {
        return idComparator.compare(o1.getPluginId(), o2.getPluginId());
      }
    };
  }

  private static Graph<PluginId> createPluginIdGraph(final Map<PluginId, IdeaPluginDescriptorImpl> idToDescriptorMap) {
    final PluginId[] ids = idToDescriptorMap.keySet().toArray(new PluginId[idToDescriptorMap.size()]);
    return GraphGenerator.create(CachingSemiGraph.create(new GraphGenerator.SemiGraph<PluginId>() {
      public Collection<PluginId> getNodes() {
        return Arrays.asList(ids);
      }

      public Iterator<PluginId> getIn(PluginId pluginId) {
        final IdeaPluginDescriptor descriptor = idToDescriptorMap.get(pluginId);
        ArrayList<PluginId> plugins = new ArrayList<PluginId>();
        for(PluginId dependentPluginId: descriptor.getDependentPluginIds()) {
          // check for missing optional dependency
          if (idToDescriptorMap.containsKey(dependentPluginId)) {
            plugins.add(dependentPluginId);
          }
        }
        return plugins.iterator();
      }
    }));
  }

  private static ClassLoader[] getParentLoaders(Map<PluginId, IdeaPluginDescriptorImpl> idToDescriptorMap, PluginId[] pluginIds) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return new ClassLoader[0];
    final List<ClassLoader> classLoaders = new ArrayList<ClassLoader>();
    for (final PluginId id : pluginIds) {
      IdeaPluginDescriptor pluginDescriptor = idToDescriptorMap.get(id);
      if (pluginDescriptor == null) {
        continue; // Might be an optional dependency
      }

      final ClassLoader loader = pluginDescriptor.getPluginClassLoader();
      if (loader == null) {
        getLogger().assertTrue(false, "Plugin class loader should be initialized for plugin " + id);
      }
      classLoaders.add(loader);
    }
    return classLoaders.toArray(new ClassLoader[classLoaders.size()]);
  }

  // See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4167874
  private static void clearJarURLCache() {
    try {
      /*
      new URLConnection(null) {
        public void connect() throws IOException {
          throw new UnsupportedOperationException();
        }
      }.setDefaultUseCaches(false);
      */

      Class jarFileFactory = Class.forName("sun.net.www.protocol.jar.JarFileFactory");

      Field fileCache = jarFileFactory.getDeclaredField(FILE_CACHE);
      Field urlCache = jarFileFactory.getDeclaredField(URL_CACHE);

      fileCache.setAccessible(true);
      fileCache.set(null, new HashMap());

      urlCache.setAccessible(true);
      urlCache.set(null, new HashMap());
    }
    catch (Exception e) {
      System.out.println("Failed to clear URL cache");
      // Do nothing.
    }
  }

  /**
   * Called via reflection
   */
  @SuppressWarnings({"UnusedDeclaration"})
  protected static void start() {
    try {
      //noinspection HardCodedStringLiteral
      ThreadGroup threadGroup = new ThreadGroup("Idea Thread Group") {
        public void uncaughtException(Thread t, Throwable e) {
          getLogger().error(e);
        }
      };

      Runnable runnable = new Runnable() {
        public void run() {
          try {
            clearJarURLCache();

            //noinspection AssignmentToStaticFieldFromInstanceMethod
            PluginsFacade.INSTANCE = new Facade();

            Class aClass = Class.forName(ourMainClass);
            final Method method = aClass.getDeclaredMethod(ourMethodName, (ArrayUtil.EMPTY_STRING_ARRAY).getClass());
            method.setAccessible(true);

            //noinspection RedundantArrayCreation
            method.invoke(null, new Object[]{ourArguments});
          }
          catch (Exception e) {
            e.printStackTrace();
            getLogger().error("Error while accessing " + ourMainClass + "." + ourMethodName + " with arguments: " + Arrays.asList(ourArguments), e);
          }
        }
      };

      //noinspection HardCodedStringLiteral
      new Thread(threadGroup, runnable, "Idea Main Thread").start();
    }
    catch (Exception e) {
      getLogger().error(e);
    }
  }

  private static IdeaPluginDescriptorImpl[] loadDescriptors() {
    if (isLoadingOfExternalPluginsDisabled()) {
      return IdeaPluginDescriptorImpl.EMPTY_ARRAY;
    }

    final List<IdeaPluginDescriptorImpl> result = new ArrayList<IdeaPluginDescriptorImpl>();

    loadDescriptors(PathManager.getPluginsPath(), result);
    loadDescriptors(PathManager.getPreinstalledPluginsPath(), result);

    loadDescriptorsFromProperty(result);

    loadDescriptorsFromClassPath(result);

    IdeaPluginDescriptorImpl[] pluginDescriptors = result.toArray(new IdeaPluginDescriptorImpl[result.size()]);
    try {
      Arrays.sort(pluginDescriptors, new PluginDescriptorComparator(pluginDescriptors));
    }
    catch (Exception e) {
      prepareLoadingPluginsErrorMessage(IdeBundle.message("error.plugins.were.not.loaded", e.getMessage()));
      getLogger().info(e);
      pluginDescriptors = IdeaPluginDescriptorImpl.EMPTY_ARRAY;
    }
    return pluginDescriptors;
  }

  public static synchronized void reportPluginError() {
    if (myPluginError != null) {
      JOptionPane.showMessageDialog(null, myPluginError, IdeBundle.message("title.plugin.error"), JOptionPane.ERROR_MESSAGE);
      myPluginError = null;
    }
  }

  private static void loadDescriptorsFromProperty(final List<IdeaPluginDescriptorImpl> result) {
    final String pathProperty = System.getProperty(PROPERTY_PLUGIN_PATH);
    if (pathProperty == null) return;

    for (java.util.StringTokenizer t = new java.util.StringTokenizer(pathProperty, File.pathSeparator); t.hasMoreTokens();) {
      String s = t.nextToken();
      final IdeaPluginDescriptorImpl ideaPluginDescriptor = loadDescriptor(new File(s), PLUGIN_XML);
      if (ideaPluginDescriptor != null) {
        result.add(ideaPluginDescriptor);
      }
    }
  }

  @SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
  private static void loadDescriptorsFromClassPath(final List<IdeaPluginDescriptorImpl> result) {
    try {
      final Collection<URL> urls = getClassLoaderUrls();
      for (URL url : urls) {
        final String protocol = url.getProtocol();
        if ("file".equals(protocol)) {
          final File file = new File(URLDecoder.decode(url.getFile()));
          //final String canonicalPath = file.getCanonicalPath();
          //if (!canonicalPath.startsWith(homePath) || canonicalPath.endsWith(".jar")) continue;
          //if (!canonicalPath.startsWith(homePath)) continue;
          final IdeaPluginDescriptorImpl pluginDescriptor = loadDescriptor(file, PLUGIN_XML);
          if (pluginDescriptor != null && !result.contains(pluginDescriptor)) {
            result.add(pluginDescriptor);
          }
        }
      }
    }
    catch (Exception e) {
      System.err.println("Error loading plugins from classpath:");
      e.printStackTrace();
    }
  }

  private static Collection<URL> getClassLoaderUrls() {
    final ClassLoader classLoader = PluginManager.class.getClassLoader();
    final Class<? extends ClassLoader> aClass = classLoader.getClass();
    if (aClass.getName().equals(UrlClassLoader.class.getName())) {
      try {
        return (List<URL>)aClass.getDeclaredMethod("getUrls").invoke(classLoader);
      }
      catch (IllegalAccessException e) {
      }
      catch (InvocationTargetException e) {
      }
      catch (NoSuchMethodException e) {
      }
    }
    if (classLoader instanceof URLClassLoader) {
      return Arrays.asList(((URLClassLoader)classLoader).getURLs());
    }

    return Collections.emptyList();
  }

  @Nullable
  private static String filterBadPlugins(List<IdeaPluginDescriptorImpl> result) {
    final Map<PluginId, IdeaPluginDescriptorImpl> idToDescriptorMap = new HashMap<PluginId, IdeaPluginDescriptorImpl>();
    final StringBuffer message = new StringBuffer();
    boolean pluginsWithoutIdFound = false;
    for (Iterator<IdeaPluginDescriptorImpl> it = result.iterator(); it.hasNext();) {
      final IdeaPluginDescriptorImpl descriptor = it.next();
      final PluginId id = descriptor.getPluginId();
      if (id == null) {
        pluginsWithoutIdFound = true;
      }
      if (idToDescriptorMap.containsKey(id)) {
        if (message.length() > 0) {
          message.append("\n");
        }
        message.append(IdeBundle.message("message.duplicate.plugin.id"));
        message.append(id);
        it.remove();
      }
      else if (descriptor.isEnabled()) {
        idToDescriptorMap.put(id, descriptor);
      }
    }
    final List<String> disabledPluginIds = new ArrayList<String>();
    for (final Iterator<IdeaPluginDescriptorImpl> it = result.iterator(); it.hasNext();) {
      final IdeaPluginDescriptorImpl pluginDescriptor = it.next();
      checkDependants(pluginDescriptor, new Function<PluginId, IdeaPluginDescriptor>() {
        public IdeaPluginDescriptor fun(final PluginId pluginId) {
          return idToDescriptorMap.get(pluginId);
        }
      }, new Condition<PluginId>() {
        public boolean value(final PluginId pluginId) {
          if (!idToDescriptorMap.containsKey(pluginId)) {
            if (message.length() > 0) {
              message.append("\n");
            }
            pluginDescriptor.setEnabled(false);
            disabledPluginIds.add(pluginDescriptor.getPluginId().getIdString());
            message.append(getDisabledPlugins().contains(pluginId.getIdString())
                           ? IdeBundle.message("error.required.plugin.disabled", pluginDescriptor.getPluginId(), pluginId)
                           : IdeBundle.message("error.required.plugin.not.installed", pluginDescriptor.getPluginId(), pluginId));
            it.remove();
            return false;
          }
          return true;
        }
      });
    }
    if (!disabledPluginIds.isEmpty()) {
      try {
        saveDisabledPlugins(disabledPluginIds, true);
      }
      catch (IOException e) {
        getLogger().error(e);
      }
    }
    if (pluginsWithoutIdFound) {
      if (message.length() > 0) {
        message.append("\n");
      }
      message.append(IdeBundle.message("error.plugins.without.id.found"));
    }
    if (message.length() > 0) {
      message.insert(0, IdeBundle.message("error.problems.found.loading.plugins"));
      return message.toString();
    }
    return null;
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

  @Nullable
  private static String getBuildNumber() {
    if (ourBuildNumber == null) {
      try {
        ourBuildNumber = new String(FileUtil.loadFileText(new File(PathManager.getHomePath() + "/build.txt"))).trim();
      }
      catch (IOException e) {
        ourBuildNumber = null;
      }
    }
    return ourBuildNumber;
  }


  private static void loadDescriptors(String pluginsPath, List<IdeaPluginDescriptorImpl> result) {
    final File pluginsHome = new File(pluginsPath);
    final File[] files = pluginsHome.listFiles();
    if (files != null) {
      for (File file : files) {
        final IdeaPluginDescriptorImpl descriptor = loadDescriptor(file, PLUGIN_XML);
        if (descriptor != null && !result.contains(descriptor)) {
          result.add(descriptor);
        }
      }
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  @Nullable
  private static IdeaPluginDescriptorImpl loadDescriptor(final File file, final @NonNls String fileName) {
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
       for (final File f : files) {
         if (isJarOrZip(f)) {
           IdeaPluginDescriptorImpl descriptor1 = loadDescriptorFromJar(f, fileName);
           if (descriptor1 != null) {
             if (descriptor != null) {
               getLogger().info("Cannot load " + file + " because two or more plugin.xml's detected");
               return null;
             }
             descriptor = descriptor1;
             descriptor.setPath(file);
           }
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
    else if (StringUtil.endsWithIgnoreCase(file.getName(), ".jar")) {
      descriptor = loadDescriptorFromJar(file, fileName);
    }

    if (descriptor != null && !descriptor.getOptionalConfigs().isEmpty()) {
      final Map<PluginId, IdeaPluginDescriptorImpl> descriptors = new HashMap<PluginId, IdeaPluginDescriptorImpl>(descriptor.getOptionalConfigs().size());
      for (Map.Entry<PluginId, String> entry: descriptor.getOptionalConfigs().entrySet()) {
        final IdeaPluginDescriptorImpl optionalDescriptor = loadDescriptor(file, entry.getValue());
        if (optionalDescriptor != null) {
          descriptors.put(entry.getKey(), optionalDescriptor);
        }
      }
      descriptor.setOptionalDescriptors(descriptors);
    }
    return descriptor;
  }

  @Nullable
  private static IdeaPluginDescriptorImpl loadDescriptorFromDir(final File file, @NonNls String fileName) {
    IdeaPluginDescriptorImpl descriptor = null;
    File descriptorFile = new File(file, META_INF + File.separator + fileName);
    if (descriptorFile.exists()) {
      descriptor = new IdeaPluginDescriptorImpl(file);

      try {
        descriptor.readExternal(descriptorFile.toURL());
      }
      catch (Exception e) {
        System.err.println("Cannot load: " + descriptorFile.getAbsolutePath());
        e.printStackTrace();
      }
    }
    return descriptor;
  }

  @Nullable
  private static IdeaPluginDescriptorImpl loadDescriptorFromJar(File file, @NonNls String fileName) {
    try {

      IdeaPluginDescriptorImpl descriptor = new IdeaPluginDescriptorImpl(file);

      URL fileURL = file.toURL();
      URL jarURL = new URL(
        "jar:" + fileURL.toExternalForm() + "!/META-INF/" + fileName
      );

      descriptor.readExternal(jarURL);
      return descriptor;
    }
    catch (FileNotFoundException e) {
      return null;
    }
    catch (Exception e) {
      getLogger().info("Cannot load " + file, e);
    }

    return null;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  protected void bootstrap(List<URL> classpathElements) {
    UrlClassLoader newClassLoader = initClassloader(classpathElements);
    try {
      final Class mainClass = Class.forName(getClass().getName(), true, newClassLoader);
      Field field = mainClass.getDeclaredField("ourMainClass");
      field.setAccessible(true);
      field.set(null, ourMainClass);

      field = mainClass.getDeclaredField("ourMethodName");
      field.setAccessible(true);
      field.set(null, ourMethodName);

      field = mainClass.getDeclaredField("ourArguments");
      field.setAccessible(true);
      field.set(null, ourArguments);

      final Method startMethod = mainClass.getDeclaredMethod("start");
      startMethod.setAccessible(true);
      startMethod.invoke(null, ArrayUtil.EMPTY_OBJECT_ARRAY);
    }
    catch (Exception e) {
      Logger logger = getLogger();
      if (logger == null) {
        e.printStackTrace(System.err);
      }
      else {
        logger.error(e);
      }
    }
  }

  public UrlClassLoader initClassloader(final List<URL> classpathElements) {
    PathManager.loadProperties();

    try {
      addParentClasspath(classpathElements);
      addIDEALibraries(classpathElements);
      addAdditionalClassPath(classpathElements);
    }
    catch (IllegalArgumentException e) {
      if (Main.isHeadless()) {
        getLogger().error(e);
      } else {
        JOptionPane
          .showMessageDialog(JOptionPane.getRootFrame(), e.getMessage(), CommonBundle.getErrorTitle(), JOptionPane.INFORMATION_MESSAGE);
      }
      System.exit(1);
    }
    catch (MalformedURLException e) {
      if (Main.isHeadless()) {
        getLogger().error(e.getMessage());
      } else {
        JOptionPane
          .showMessageDialog(JOptionPane.getRootFrame(), e.getMessage(), CommonBundle.getErrorTitle(), JOptionPane.INFORMATION_MESSAGE);
      }
      System.exit(1);
    }

    filterClassPath(classpathElements);

    UrlClassLoader newClassLoader = null;
    try {
      newClassLoader = new UrlClassLoader(classpathElements, null, true, true);

      // prepare plugins
      if (!isLoadingOfExternalPluginsDisabled()) {
        initPluginClasses();
        StartupActionScriptManager.executeActionScript();
      }

      Thread.currentThread().setContextClassLoader(newClassLoader);

    }
    catch (Exception e) {
      Logger logger = getLogger();
      if (logger == null) {
        e.printStackTrace(System.err);
      }
      else {
        logger.error(e);
      }
    }
    return newClassLoader;
  }

  private static void filterClassPath(final List<URL> classpathElements) {
    final String ignoreProperty = System.getProperty(PROPERTY_IGNORE_CLASSPATH);
    if (ignoreProperty == null) return;

    final Pattern pattern = Pattern.compile(ignoreProperty);

    for (Iterator<URL> i = classpathElements.iterator(); i.hasNext();) {
      URL url = i.next();
      final String u = url.toExternalForm();
      if (pattern.matcher(u).matches()) {
        i.remove();
      }
    }
  }

  @Nullable
  private static ClassLoader createPluginClassLoader(final File[] classPath,
                                                         final ClassLoader[] parentLoaders,
                                                         IdeaPluginDescriptor pluginDescriptor) {

    if (pluginDescriptor.getUseIdeaClassLoader()) {
      try {
        final ClassLoader loader = PluginManager.class.getClassLoader();
        final Method addUrlMethod = getAddUrlMethod(loader);


        for (File aClassPath : classPath) {
          final File file = aClassPath.getCanonicalFile();
          addUrlMethod.invoke(loader,  file.toURL());
        }

        return loader;
      }
      catch (NoSuchMethodException e) {
        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      }
      catch (IOException e) {
        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      }
      catch (IllegalAccessException e) {
        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      }
      catch (InvocationTargetException e) {
        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      }
    }

    PluginId pluginId = pluginDescriptor.getPluginId();
    File pluginRoot = pluginDescriptor.getPath();

    if (ApplicationManager.getApplication().isUnitTestMode()) return null;
    try {
      final List<URL> urls = new ArrayList<URL>(classPath.length);
      for (File aClassPath : classPath) {
        final File file = aClassPath.getCanonicalFile(); // it is critical not to have "." and ".." in classpath elements
        urls.add(file.toURL());
      }
      return new PluginClassLoader(urls, parentLoaders, pluginId, pluginRoot);
    }
    catch (MalformedURLException e) {
      e.printStackTrace();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static Method getAddUrlMethod(final ClassLoader loader) throws NoSuchMethodException {
    if (loader instanceof URLClassLoader) {
      final Method addUrlMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
      addUrlMethod.setAccessible(true);
      return addUrlMethod;
    }

    return loader.getClass().getDeclaredMethod("addURL", URL.class);
  }


  private void addParentClasspath(List<URL> aClasspathElements) throws MalformedURLException {
    final ClassLoader loader = getClass().getClassLoader();
    if (loader instanceof URLClassLoader) {
      URLClassLoader urlClassLoader = (URLClassLoader)loader;
      aClasspathElements.addAll(Arrays.asList(urlClassLoader.getURLs()));
    }
    else {
      try {
        Class antClassLoaderClass = Class.forName("org.apache.tools.ant.AntClassLoader");
        if (antClassLoaderClass.isInstance(loader) ||
            loader.getClass().getName().equals("org.apache.tools.ant.AntClassLoader") ||
            loader.getClass().getName().equals("org.apache.tools.ant.loader.AntClassLoader2")) {
          //noinspection HardCodedStringLiteral
          final String classpath =
            (String)antClassLoaderClass.getDeclaredMethod("getClasspath", ArrayUtil.EMPTY_CLASS_ARRAY).invoke(loader, ArrayUtil.EMPTY_OBJECT_ARRAY);
          final StringTokenizer tokenizer = new StringTokenizer(classpath, File.separator, false);
          while (tokenizer.hasMoreTokens()) {
            final String token = tokenizer.nextToken();
            aClasspathElements.add(new File(token).toURL());
          }
        }
        else {
          getLogger().warn("Unknown classloader: " + loader.getClass().getName());
        }
      }
      catch (ClassCastException e) {
        getLogger().warn("Unknown classloader [CCE]: " + e.getMessage());
      }
      catch (ClassNotFoundException e) {
        getLogger().warn("Unknown classloader [CNFE]: " + loader.getClass().getName());
      }
      catch (NoSuchMethodException e) {
        getLogger().warn("Unknown classloader [NSME]: " + e.getMessage());
      }
      catch (IllegalAccessException e) {
        getLogger().warn("Unknown classloader [IAE]: " + e.getMessage());
      }
      catch (InvocationTargetException e) {
        getLogger().warn("Unknown classloader [ITE]: " + e.getMessage());
      }
    }
  }

  private static void addIDEALibraries(List<URL> classpathElements) {
    final String ideaHomePath = PathManager.getHomePath();
    addAllFromLibFolder(ideaHomePath, classpathElements);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static void addAllFromLibFolder(final String aFolderPath, List<URL> classPath) {
    try {
      final Class<PluginManager> aClass = PluginManager.class;
      final String selfRoot = PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");

      final URL selfRootUrl = new File(selfRoot).getAbsoluteFile().toURL();
      classPath.add(selfRootUrl);

      final File libFolder = new File(aFolderPath + File.separator + "lib");
      addLibraries(classPath, libFolder, selfRootUrl);

      final File antLib = new File(new File(libFolder, "ant"), "lib");
      addLibraries(classPath, antLib, selfRootUrl);
    }
    catch (MalformedURLException e) {
      getLogger().error(e);
    }
  }

  private static void addLibraries(List<URL> classPath, File fromDir, final URL selfRootUrl) throws MalformedURLException {
    final File[] files = fromDir.listFiles();
    if (files != null) {
      for (final File file : files) {
        if (!isJarOrZip(file)) {
          continue;
        }
        final URL url = file.toURL();
        if (selfRootUrl.equals(url)) {
          continue;
        }
        classPath.add(url);
      }
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static boolean isJarOrZip(File file) {
    if (file.isDirectory()) {
      return false;
    }
    final String name = file.getName();
    return StringUtil.endsWithIgnoreCase(name, ".jar") || StringUtil.endsWithIgnoreCase(name, ".zip");
  }

  private static void addAdditionalClassPath(List<URL> classPath) {
    try {
      //noinspection HardCodedStringLiteral
      final StringTokenizer tokenizer = new StringTokenizer(System.getProperty("idea.additional.classpath", ""), File.pathSeparator, false);
      while (tokenizer.hasMoreTokens()) {
        String pathItem = tokenizer.nextToken();
        classPath.add(new File(pathItem).toURL());
      }
    }
    catch (MalformedURLException e) {
      getLogger().error(e);
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static boolean isLoadingOfExternalPluginsDisabled() {
    return !"true".equalsIgnoreCase(System.getProperty("idea.plugins.load", "true"));
  }

  public static boolean isPluginInstalled(PluginId id) {
    return (getPlugin(id) != null);
  }

  @Nullable
  public static IdeaPluginDescriptor getPlugin(PluginId id) {
    final IdeaPluginDescriptor[] plugins = getPlugins();
    for (final IdeaPluginDescriptor plugin : plugins) {
      if (Comparing.equal(id, plugin.getPluginId())) {
        return plugin;
      }
    }
    return null;
  }

  public static void addPluginClass(String className, PluginId pluginId) {
    synchronized(PLUGIN_CLASSES_LOCK) {
      if (ourPluginClasses == null) {
        ourPluginClasses = new THashMap<String, PluginId>();
      }
      ourPluginClasses.put(className, pluginId);
    }
  }

  public static boolean isPluginClass(String className) {
    return getPluginByClassName(className) != null;
  }

  @Nullable
  public static PluginId getPluginByClassName(String className) {
    synchronized (PLUGIN_CLASSES_LOCK) {
      return ourPluginClasses != null ? ourPluginClasses.get(className) : null;
    }
  }

  public static void saveDisabledPlugins(List<String> ids, boolean append) throws IOException {
    File plugins = new File(PathManager.getConfigPath(), PluginManager.DISABLED_PLUGINS_FILENAME);
    if (!plugins.isFile()) {
      plugins.createNewFile();
    }
    PrintWriter printWriter = null;
    try {
      printWriter = new PrintWriter(new BufferedWriter(new FileWriter(plugins, append)));
      for (String id : ids) {
        printWriter.println(id);
      }
      printWriter.flush();
    }
    finally {
      if (printWriter != null) {
        printWriter.close();
      }
    }
  }

  public static List<String> getDisabledPlugins() {
    if (ourDisabledPlugins == null) {
      ourDisabledPlugins = new ArrayList<String>();
      final File file = new File(PathManager.getConfigPath(), DISABLED_PLUGINS_FILENAME);
      if (file.isFile()) {
        BufferedReader reader = null;
        try {
          reader = new BufferedReader(new FileReader(file));
          String id;
          while ((id = reader.readLine()) != null) {
            ourDisabledPlugins.add(id.trim());
          }
        }
        catch (IOException e) {
          //do nothing
        }
        finally {
          try {
            if (reader != null) {
              reader.close();
            }
          }
          catch (IOException e) {
            //do nothing
          }
        }
      }
    }
    return ourDisabledPlugins;
  }

  private static class IdeaLogProvider implements LogProvider {
    public void error(String message) {
      getLogger().error(message);
    }

    public void error(String message, Throwable t) {
      getLogger().error(message, t);
    }

    public void error(Throwable t) {
      getLogger().error(t);
    }

    public void warn(String message) {
      getLogger().info(message);
    }

    public void warn(String message, Throwable t) {
      getLogger().info(message, t);
    }

    public void warn(Throwable t) {
      getLogger().info(t);
    }
  }
}
