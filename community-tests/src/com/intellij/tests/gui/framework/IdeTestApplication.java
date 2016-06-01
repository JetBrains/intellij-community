/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.tests.gui.framework;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.ide.BootstrapClassLoaderUtil;
import com.intellij.ide.WindowsCommandLineProcessor;
import com.intellij.ide.startup.StartupActionScriptManager;
import com.intellij.idea.Main;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.SystemProperties;
import com.intellij.util.lang.UrlClassLoader;
import com.intellij.util.text.StringTokenizer;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import static com.intellij.openapi.application.PathManager.PROPERTY_CONFIG_PATH;
import static com.intellij.openapi.util.io.FileUtil.*;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.tests.gui.framework.GuiTests.getProjectCreationDirPath;
import static com.intellij.util.ArrayUtil.EMPTY_STRING_ARRAY;
import static com.intellij.util.ui.UIUtil.initDefaultLAF;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.reflect.core.Reflection.method;
import static org.junit.Assert.assertNotNull;

public class IdeTestApplication implements Disposable {
  private static final Logger LOG = Logger.getInstance(IdeTestApplication.class);
  private static final String PROPERTY_IGNORE_CLASSPATH = "ignore.classpath";
  private static final String PROPERTY_ALLOW_BOOTSTRAP_RESOURCES = "idea.allow.bootstrap.resources";
  private static final String PROPERTY_ADDITIONAL_CLASSPATH = "idea.additional.classpath";

  private static IdeTestApplication ourInstance;

  @NotNull private final ClassLoader myIdeClassLoader;

  @NotNull
  public static synchronized IdeTestApplication getInstance() throws Exception {
    //System.setProperty(PLATFORM_PREFIX_KEY, "AndroidStudio");
    //System.setProperty(PLATFORM_PREFIX_KEY, "idea");
    File configDirPath = getConfigDirPath();
    System.setProperty(PROPERTY_CONFIG_PATH, configDirPath.getPath());

    // Force Swing FileChooser on Mac (instead of native one) to be able to use FEST to drive it.
    System.setProperty("native.mac.file.chooser.enabled", "false");

    if (!isLoaded()) {
      ourInstance = new IdeTestApplication();
      recreateDirectory(configDirPath);

      File newProjectsRootDirPath = getProjectCreationDirPath();
      recreateDirectory(newProjectsRootDirPath);

      ClassLoader ideClassLoader = ourInstance.getIdeClassLoader();
      Class<?> clazz = ideClassLoader.loadClass(GuiTests.class.getCanonicalName());
      method("waitForIdeToStart").in(clazz).invoke();
      method("setUpDefaultGeneralSettings").in(clazz).invoke();
    }

    return ourInstance;
  }

  @NotNull
  private static File getConfigDirPath() throws IOException {
    File dirPath = new File(getGuiTestRootDirPath(), "config");
    ensureExists(dirPath);
    return dirPath;
  }

  @NotNull
  public static File getFailedTestScreenshotDirPath() throws IOException {
    File dirPath = new File(getGuiTestRootDirPath(), "failures");
    ensureExists(dirPath);
    return dirPath;
  }

  @NotNull
  private static File getGuiTestRootDirPath() throws IOException {
    String guiTestRootDirPathProperty = System.getProperty("gui.tests.root.dir.path");
    if (isNotEmpty(guiTestRootDirPathProperty)) {
      File rootDirPath = new File(guiTestRootDirPathProperty);
      if (rootDirPath.isDirectory()) {
        return rootDirPath;
      }
    }
    String homeDirPath = toSystemDependentName(PathManager.getHomePath());
    assertThat(homeDirPath).isNotEmpty();
    File rootDirPath = new File(homeDirPath, join("androidStudio", "gui-tests"));
    ensureExists(rootDirPath);
    return rootDirPath;
  }

  private static void recreateDirectory(@NotNull File path) throws IOException {
    delete(path);
    ensureExists(path);
  }

  private IdeTestApplication() throws Exception {
    String[] args = EMPTY_STRING_ARRAY;

    LOG.assertTrue(ourInstance == null, "Only one instance allowed.");
    ourInstance = this;

    pluginManagerStart(args);
    mainMain();

    myIdeClassLoader = createClassLoader();
    //myIdeClassLoader = BootstrapClassLoaderUtil.initClassLoader(false);


    WindowsCommandLineProcessor.ourMirrorClass = Class.forName(WindowsCommandLineProcessor.class.getName(), true, myIdeClassLoader);

    // We turn on "GUI Testing Mode" right away, even before loading the IDE.
    //Class<?> androidPluginClass = Class.forName("org.jetbrains.android.AndroidPlugin", true, myIdeClassLoader);
    //method("setGuiTestingMode").withParameterTypes(boolean.class).in(androidPluginClass).invoke(true);

    Class<?> classUtilCoreClass = Class.forName("com.intellij.ide.ClassUtilCore", true, myIdeClassLoader);
    method("clearJarURLCache").in(classUtilCoreClass).invoke();

    Class<?> pluginManagerClass = Class.forName("com.intellij.ide.plugins.PluginManager", true, myIdeClassLoader);
    method("start").withParameterTypes(String.class, String.class, String[].class)
                   .in(pluginManagerClass)
                   .invoke("com.intellij.idea.MainImpl", "start", args);
  }

  // This method replaces BootstrapClassLoaderUtil.initClassLoader. The reason behind it is that when running UI tests the ClassLoader
  // containing the URLs for the plugin jars is loaded by a different ClassLoader and it gets ignored. The result is test failing because
  // classes like AndroidPlugin cannot be found.
  @NotNull
  private static ClassLoader createClassLoader() throws MalformedURLException, URISyntaxException {
    Collection<URL> classpath = Sets.newLinkedHashSet();
    addIdeaLibraries(classpath);
    addAdditionalClassPath(classpath);

    UrlClassLoader.Builder builder = UrlClassLoader.build()
                                                   .urls(filterClassPath(Lists.newArrayList(classpath)))
                                                   .parent(IdeTestApplication.class.getClassLoader())
                                                   .allowLock(false)
                                                   .usePersistentClasspathIndexForLocalClassDirectories();
    if (SystemProperties.getBooleanProperty(PROPERTY_ALLOW_BOOTSTRAP_RESOURCES, true)) {
      builder.allowBootstrapResources();
    }

    UrlClassLoader newClassLoader = builder.get();

    // prepare plugins
    try {
      StartupActionScriptManager.executeActionScript();
    }
    catch (IOException e) {
      Main.showMessage("Plugin Installation Error", e);
    }

    Thread.currentThread().setContextClassLoader(newClassLoader);
    return newClassLoader;
  }

  private static void addIdeaLibraries(@NotNull Collection<URL> classpath) throws MalformedURLException {
    Class<BootstrapClassLoaderUtil> aClass = BootstrapClassLoaderUtil.class;
    String selfRoot = PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
    assertNotNull(selfRoot);

    URL selfRootUrl = new File(selfRoot).getAbsoluteFile().toURI().toURL();
    classpath.add(selfRootUrl);

    File libFolder = new File(PathManager.getLibPath());
    addLibraries(classpath, libFolder, selfRootUrl);
    addLibraries(classpath, new File(libFolder, "ext"), selfRootUrl);
    addLibraries(classpath, new File(libFolder, "ant/lib"), selfRootUrl);
  }

  private static void addLibraries(@NotNull Collection<URL> classPath, @NotNull File fromDir, @NotNull URL selfRootUrl)
    throws MalformedURLException {
    for (File file : notNullize(fromDir.listFiles())) {
      if (isJarOrZip(file)) {
        URL url = file.toURI().toURL();
        if (!selfRootUrl.equals(url)) {
          classPath.add(url);
        }
      }
    }
  }

  private static void addAdditionalClassPath(@NotNull Collection<URL> classpath) throws MalformedURLException {
    StringTokenizer tokenizer = new StringTokenizer(System.getProperty(PROPERTY_ADDITIONAL_CLASSPATH, ""), File.pathSeparator, false);
    while (tokenizer.hasMoreTokens()) {
      String pathItem = tokenizer.nextToken();
      classpath.add(new File(pathItem).toURI().toURL());
    }
  }

  private static List<URL> filterClassPath(@NotNull List<URL> classpath) {
    String ignoreProperty = System.getProperty(PROPERTY_IGNORE_CLASSPATH);
    if (ignoreProperty != null) {
      Pattern pattern = Pattern.compile(ignoreProperty);
      for (Iterator<URL> i = classpath.iterator(); i.hasNext(); ) {
        String url = i.next().toExternalForm();
        if (pattern.matcher(url).matches()) {
          i.remove();
        }
      }
    }
    return classpath;
  }

  private static void pluginManagerStart(@NotNull String[] args) {
    // Duplicates what PluginManager#start does.
    Main.setFlags(args);
    initDefaultLAF();
  }

  private static void mainMain() {
    // Duplicates what Main#main does.
    method("installPatch").in(Main.class).invoke();
  }

  @NotNull
  public ClassLoader getIdeClassLoader() {
    return myIdeClassLoader;
  }

  @Override
  public void dispose() {
    disposeInstance();
  }

  public static synchronized void disposeInstance() {
    if (!isLoaded()) {
      return;
    }
    Application application = ApplicationManager.getApplication();
    if (application != null) {
      if (application instanceof ApplicationEx) {
        ((ApplicationEx)application).exit(true, true);
      }
      else {
        application.exit();
      }
    }
    ourInstance = null;
  }

  public static synchronized boolean isLoaded() {
    return ourInstance != null;
  }
}
