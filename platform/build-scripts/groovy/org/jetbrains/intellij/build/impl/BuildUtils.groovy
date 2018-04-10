/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.apache.tools.ant.AntClassLoader
import org.apache.tools.ant.BuildException
import org.apache.tools.ant.Main
import org.apache.tools.ant.Project
import org.apache.tools.ant.types.Path
import org.apache.tools.ant.util.SplitClassLoader
import org.codehaus.groovy.tools.RootLoader
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.jps.model.library.JpsOrderRootType

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
      else if (classLoader instanceof AntClassLoader) {
        classLoader.addPathElement(path)
      }
      else if (classLoader instanceof RootLoader) {
        classLoader.addURL(new File(path).toURI().toURL())
      }
      else {
        throw new BuildException("Cannot add to classpath: non-groovy or ant classloader $classLoader")
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

  static void defineFtpTask(BuildContext context) {
    List<File> commonsNetJars = context.project.libraryCollection.findLibrary("commons-net").getFiles(JpsOrderRootType.COMPILED) +
      [new File(context.paths.communityHome, "lib/ant/lib/ant-commons-net.jar")]
    defineFtpTask(context.ant, commonsNetJars)
  }

    /**
   * Defines ftp task using libraries from IntelliJ IDEA project sources.
   */
  @CompileDynamic
  static void defineFtpTask(AntBuilder ant, List<File> commonsNetJars) {
    def ftpTaskLoaderRef = "FTP_TASK_CLASS_LOADER"
    if (ant.project.hasReference(ftpTaskLoaderRef)) return

    /*
      We need this to ensure that FTP task class isn't loaded by the main Ant classloader, otherwise Ant will try to load FTPClient class
      by the main Ant classloader as well and fail because 'commons-net-*.jar' isn't included to Ant classpath.
      Probably we could call FTPClient directly to avoid this hack.
     */
    Path ftpPath = new Path(ant.project)
    commonsNetJars.each {
      ftpPath.createPathElement().setLocation(it)
    }
    ant.project.addReference(ftpTaskLoaderRef, new SplitClassLoader(ant.project.getClass().getClassLoader(), ftpPath, ant.project,
                                                                    ["FTP", "FTPTaskConfig"] as String[]))
    ant.taskdef(name: "ftp", classname: "org.apache.tools.ant.taskdefs.optional.net.FTP", loaderRef: ftpTaskLoaderRef)
  }

  /**
   * Defines sshexec task using libraries from IntelliJ IDEA project sources.
   */
  @CompileDynamic
  static void defineSshTask(BuildContext context) {
    List<File> jschJars = context.project.libraryCollection.findLibrary("JSch").getFiles(JpsOrderRootType.COMPILED) +
                                [new File(context.paths.communityHome, "lib/ant/lib/ant-jsch.jar")]
    def ant = context.ant
    def sshTaskLoaderRef = "SSH_TASK_CLASS_LOADER"
    if (ant.project.hasReference(sshTaskLoaderRef)) return
    
    Path pathSsh = new Path(ant.project)
    jschJars.each {
      pathSsh.createPathElement().setLocation(it)
    }
    ant.project.addReference(sshTaskLoaderRef, new SplitClassLoader(ant.project.getClass().getClassLoader(), pathSsh, ant.project,
                                                                    ["SSHExec", "SSHBase", "LogListener", "SSHUserInfo"] as String[]))
    ant.taskdef(name: "sshexec", classname: "org.apache.tools.ant.taskdefs.optional.ssh.SSHExec", loaderRef: sshTaskLoaderRef)
  }
}