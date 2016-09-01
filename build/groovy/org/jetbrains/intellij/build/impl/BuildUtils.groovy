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
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import groovy.transform.CompileStatic
import org.apache.tools.ant.BuildException
import org.apache.tools.ant.Main
import org.apache.tools.ant.Project
import org.codehaus.groovy.tools.RootLoader

/**
 * @author nik
 */
@CompileStatic
class BuildUtils {
  static void addToClassPath(String path, AntBuilder ant) {
    addToClassLoaderClassPath(path, ant, BuildUtils.class.classLoader)
  }

  static void addToJpsClassPath(String path, AntBuilder ant) {
    //we need to add path to classloader of BuilderService to ensure that classes from that path will be returned by JpsServiceManager.getExtensions
    addToClassLoaderClassPath(path, ant, Class.forName("org.jetbrains.jps.incremental.BuilderService").classLoader)
  }

  private static void addToClassLoaderClassPath(String path, AntBuilder ant, ClassLoader classLoader) {
    if (new File(path).exists()) {
      if (classLoader instanceof GroovyClassLoader) {
        classLoader.addClasspath(path)
      }
      else if (classLoader instanceof RootLoader) {
        classLoader.addURL(new File(path).toURI().toURL())
      }
      else {
        throw new BuildException("Cannot add to classpath: non-groovy classloader $classLoader")
      }
      ant.project.log("'$path' added to classpath", Project.MSG_INFO)
    }
    else {
      throw new BuildException("Cannot add to classpath: $path doesn't exist")
    }
  }

  static String replaceAll(String text, Map<String, String> replacements, String marker = "__") {
    replacements.each {
      text = StringUtil.replace(text, "$marker$it.key$marker", it.value)
    }
    return text
  }

  static void copyAndPatchFile(String sourcePath, String targetPath, Map<String, String> replacements, String marker = "__") {
    FileUtil.createParentDirs(new File(targetPath))
    new File(targetPath).text = replaceAll(new File(sourcePath).text, replacements, marker)
  }

  static PrintStream getRealSystemOut() {
    try {
      //if the build script is running under Ant or AntBuilder it may replace the standard System.out
      def field = Main.class.getDeclaredField("out")
      field.accessible = true
      return (PrintStream) field.get(null)
    }
    catch (Throwable ignored) {
      return System.out
    }
  }
}