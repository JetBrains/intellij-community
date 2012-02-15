/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.compiler.server.CompileServerPlugin;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class CompileServerClasspathManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.server.impl.CompileServerClasspathManager");
  private List<File> myCompileServerPluginsClasspath;

  public List<File> getCompileServerPluginsClasspath() {
    if (myCompileServerPluginsClasspath == null) {
      myCompileServerPluginsClasspath = computeCompileServerPluginsClasspath();
    }
    return myCompileServerPluginsClasspath;
  }

  private static List<File> computeCompileServerPluginsClasspath() {
    final List<File> classpath = new ArrayList<File>();
    for (CompileServerPlugin serverPlugin : CompileServerPlugin.EP_NAME.getExtensions()) {
      final PluginId pluginId = serverPlugin.getPluginDescriptor().getPluginId();
      final IdeaPluginDescriptor plugin = PluginManager.getPlugin(pluginId);
      LOG.assertTrue(plugin != null, pluginId);
      final File baseFile = plugin.getPath();
      if (baseFile.isFile()) {
        classpath.add(baseFile);
      }
      else if (baseFile.isDirectory()) {
        final String relativePath = serverPlugin.getJarPath();

        final File jarFile = relativePath.startsWith("../")
                             ? new File(baseFile, relativePath.substring(3))
                             : new File(new File(baseFile, "lib"), relativePath);
        if (jarFile.exists()) {
          classpath.add(jarFile);
        }
        else {
          //development mode: add directory out/classes/production/<jar-name> to classpath, assuming that jar-name is equal to module name
          final String moduleName = FileUtil.getNameWithoutExtension(PathUtil.getFileName(relativePath));
          final File dir = new File(baseFile.getParentFile(), moduleName);
          if (!dir.exists()) {
            LOG.warn("Cannot add plugin " + pluginId + " to compile server classpath: " + jarFile.getAbsolutePath() + " and " +
                     dir.getAbsolutePath() + " don't exist");
          }
          classpath.add(dir);
        }
      }
    }
    return classpath;
  }
}
