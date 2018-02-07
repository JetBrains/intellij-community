/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.compiler.server.impl;

import com.intellij.compiler.server.BuildProcessParametersProvider;
import com.intellij.compiler.server.CompileServerPlugin;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public class BuildProcessClasspathManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.server.impl.BuildProcessClasspathManager");

  private List<String> myCompileServerPluginsClasspath;

  public List<String> getBuildProcessPluginsClasspath(Project project) {
    List<String> staticClasspath = getStaticClasspath();
    List<String> dynamicClasspath = getDynamicClasspath(project);

    if (dynamicClasspath.isEmpty()) {
      return staticClasspath;
    }
    else {
      dynamicClasspath.addAll(staticClasspath);
      return dynamicClasspath;
    }
  }

  private List<String> getStaticClasspath() {
    if (myCompileServerPluginsClasspath == null) {
      myCompileServerPluginsClasspath = computeCompileServerPluginsClasspath();
    }
    return myCompileServerPluginsClasspath;
  }

  private static List<String> computeCompileServerPluginsClasspath() {
    final List<String> classpath = ContainerUtil.newArrayList();
    for (CompileServerPlugin serverPlugin : CompileServerPlugin.EP_NAME.getExtensions()) {
      final PluginId pluginId = serverPlugin.getPluginDescriptor().getPluginId();
      final IdeaPluginDescriptor plugin = PluginManager.getPlugin(pluginId);
      LOG.assertTrue(plugin != null, pluginId);
      final File baseFile = plugin.getPath();
      if (baseFile.isFile()) {
        classpath.add(baseFile.getPath());
      }
      else if (baseFile.isDirectory()) {
        for (String relativePath : StringUtil.split(serverPlugin.getClasspath(), ";")) {
          final File jarFile = new File(new File(baseFile, "lib"), relativePath);
          File classesDir = new File(baseFile, "classes");
          if (jarFile.exists()) {
            classpath.add(jarFile.getPath());
          }
          else if (classesDir.isDirectory()) {
            //'plugin run configuration': all module output are copied to 'classes' folder
            classpath.add(classesDir.getPath());
          }
          else {
            //development mode: add directory out/classes/production/<jar-name> to classpath, assuming that jar-name is equal to module name
            String moduleName = FileUtil.getNameWithoutExtension(PathUtil.getFileName(relativePath));
            if (OLD_TO_NEW_MODULE_NAME.containsKey(moduleName)) {
              moduleName = OLD_TO_NEW_MODULE_NAME.get(moduleName);
            }
            File baseOutputDir = baseFile.getParentFile();
            if (baseOutputDir.getName().equals("test")) {
              baseOutputDir = new File(baseOutputDir.getParentFile(), "production");
            }
            final File dir = new File(baseOutputDir, moduleName);
            if (dir.exists()) {
              classpath.add(dir.getPath());
            }
            else {
              //looks like <jar-name> refers to a library, try to find it under <plugin-dir>/lib
              File pluginDir = getPluginDir(plugin);
              if (pluginDir != null) {
                File libraryFile = new File(pluginDir, "lib" + File.separator + PathUtil.getFileName(relativePath));
                if (libraryFile.exists()) {
                  classpath.add(libraryFile.getPath());
                }
                else {
                  LOG.error("Cannot add " + relativePath + " from '" + plugin.getName() + ' ' + plugin.getVersion() + "'" +
                            " to external compiler classpath: library " + libraryFile.getAbsolutePath() + " not found");
                }
              }
              else {
                LOG.error("Cannot add " + relativePath + " from '" + plugin.getName() + ' ' + plugin.getVersion() + "'" +
                          " to external compiler classpath: home directory of plugin not found");
              }
            }
          }
        }
      }
    }
    return classpath;
  }

  @Nullable
  private static File getPluginDir(IdeaPluginDescriptor plugin) {
    String pluginDirName = StringUtil.getShortName(plugin.getPluginId().getIdString());
    String extraDir = System.getProperty("idea.external.build.development.plugins.dir");
    if (extraDir != null) {
      File extraDirFile = new File(extraDir, pluginDirName);
      if (extraDirFile.isDirectory()) {
        return extraDirFile;
      }
    }
    File pluginHome = PluginPathManager.getPluginHome(pluginDirName);
    if (!pluginHome.isDirectory() && StringUtil.isCapitalized(pluginDirName)) {
      pluginHome = PluginPathManager.getPluginHome(StringUtil.decapitalize(pluginDirName));
    }
    return pluginHome.isDirectory() ? pluginHome : null;
  }

  private static List<String> getDynamicClasspath(Project project) {
    final List<String> classpath = ContainerUtil.newArrayList();
    for (BuildProcessParametersProvider provider : project.getExtensions(BuildProcessParametersProvider.EP_NAME)) {
      classpath.addAll(provider.getClassPath());
    }
    return classpath;
  }

  public static List<String> getLauncherClasspath(Project project) {
    final List<String> classpath = ContainerUtil.newArrayList();
    for (BuildProcessParametersProvider provider : project.getExtensions(BuildProcessParametersProvider.EP_NAME)) {
      classpath.addAll(provider.getLauncherClassPath());
    }
    return classpath;
  }

  //todo[nik] this is a temporary compatibility fix; we should update plugin layout so JAR names correspond to module names instead.
  private static final Map<String, String> OLD_TO_NEW_MODULE_NAME;
  static {
    OLD_TO_NEW_MODULE_NAME = new LinkedHashMap<>();
    OLD_TO_NEW_MODULE_NAME.put("android-jps-plugin", "intellij.android.jps");
    OLD_TO_NEW_MODULE_NAME.put("ant-jps-plugin", "intellij.ant.jps");
    OLD_TO_NEW_MODULE_NAME.put("aspectj-jps-plugin", "intellij.aspectj.jps");
    OLD_TO_NEW_MODULE_NAME.put("devkit-jps-plugin", "intellij.devkit.jps");
    OLD_TO_NEW_MODULE_NAME.put("eclipse-jps-plugin", "intellij.eclipse.jps");
    OLD_TO_NEW_MODULE_NAME.put("error-prone-jps-plugin", "intellij.errorProne.jps");
    OLD_TO_NEW_MODULE_NAME.put("flex-jps-plugin", "intellij.flex.jps");
    OLD_TO_NEW_MODULE_NAME.put("gradle-jps-plugin", "intellij.gradle.jps");
    OLD_TO_NEW_MODULE_NAME.put("grails-jps-plugin", "intellij.groovy.grails.jps");
    OLD_TO_NEW_MODULE_NAME.put("groovy-jps-plugin", "intellij.groovy.jps");
    OLD_TO_NEW_MODULE_NAME.put("gwt-jps-plugin", "intellij.gwt.jps");
    OLD_TO_NEW_MODULE_NAME.put("google-app-engine-jps-plugin", "intellij.java.googleAppEngine.jps");
    OLD_TO_NEW_MODULE_NAME.put("ui-designer-jps-plugin", "intellij.java.guiForms.jps");
    OLD_TO_NEW_MODULE_NAME.put("intellilang-jps-plugin", "intellij.java.langInjection.jps");
    OLD_TO_NEW_MODULE_NAME.put("dmServer-jps-plugin", "intellij.javaee.appServers.dmServer.jps");
    OLD_TO_NEW_MODULE_NAME.put("weblogic-jps-plugin", "intellij.javaee.appServers.weblogic.jps");
    OLD_TO_NEW_MODULE_NAME.put("webSphere-jps-plugin", "intellij.javaee.appServers.websphere.jps");
    OLD_TO_NEW_MODULE_NAME.put("jpa-jps-plugin", "intellij.javaee.jpa.jps");
    OLD_TO_NEW_MODULE_NAME.put("javaee-jps-plugin", "intellij.javaee.jps");
    OLD_TO_NEW_MODULE_NAME.put("javaFX-jps-plugin", "intellij.javaFX.jps");
    OLD_TO_NEW_MODULE_NAME.put("maven-jps-plugin", "intellij.maven.jps");
    OLD_TO_NEW_MODULE_NAME.put("osmorc-jps-plugin", "intellij.osgi.jps");
    OLD_TO_NEW_MODULE_NAME.put("ruby-chef-jps-plugin", "intellij.ruby.chef.jps");
    OLD_TO_NEW_MODULE_NAME.put("android-common", "intellij.android.common");
    OLD_TO_NEW_MODULE_NAME.put("build-common", "intellij.android.buildCommon");
    OLD_TO_NEW_MODULE_NAME.put("android-rt", "intellij.android.rt");
    OLD_TO_NEW_MODULE_NAME.put("sdk-common", "android.sdktools.sdk-common");
    OLD_TO_NEW_MODULE_NAME.put("sdklib", "android.sdktools.sdklib");
    OLD_TO_NEW_MODULE_NAME.put("layoutlib-api", "android.sdktools.layoutlib-api");
    OLD_TO_NEW_MODULE_NAME.put("repository", "android.sdktools.repository");
    OLD_TO_NEW_MODULE_NAME.put("manifest-merger", "android.sdktools.manifest-merger");
    OLD_TO_NEW_MODULE_NAME.put("common-eclipse-util", "intellij.eclipse.common");
    OLD_TO_NEW_MODULE_NAME.put("flex-shared", "intellij.flex.shared");
    OLD_TO_NEW_MODULE_NAME.put("groovy-rt-constants", "intellij.groovy.constants.rt");
    OLD_TO_NEW_MODULE_NAME.put("grails-compiler-patch", "intellij.groovy.grails.compilerPatch");
    OLD_TO_NEW_MODULE_NAME.put("appEngine-runtime", "intellij.java.googleAppEngine.runtime");
    OLD_TO_NEW_MODULE_NAME.put("common-javaFX-plugin", "intellij.javaFX.common");
  }
}
