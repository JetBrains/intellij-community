// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.text.StringUtilRt
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.apache.tools.ant.AntClassLoader
import org.apache.tools.ant.BuildException
import org.apache.tools.ant.Main
import org.apache.tools.ant.Project
import org.apache.tools.ant.util.SplitClassLoader
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.jps.model.library.JpsOrderRootType

import java.nio.file.Files
import java.nio.file.Path

@CompileStatic
final class BuildUtils {
  static void addUltimateBuildScriptsToClassPath(String home, AntBuilder ant) {
    addToClassPath("$home/build/groovy", ant)
    addToClassPath("$home/build/dependencies/groovy", ant)
  }

  static void addToClassPath(String path, AntBuilder ant) {
    addToClassLoaderClassPath(path, ant, BuildUtils.class.classLoader)
  }

  @CompileDynamic
  static void addToSystemClasspath(File file) {
    def classLoader = ClassLoader.getSystemClassLoader()
    if (!(classLoader instanceof URLClassLoader)) {
      throw new BuildException("Cannot add to system classpath: unsupported class loader $classLoader (${classLoader.getClass()})")
    }

    classLoader.addURL(file.toURI().toURL())
  }

  static void addToJpsClassPath(String path, AntBuilder ant) {
    //we need to add path to classloader of BuilderService to ensure that classes from that path will be returned by JpsServiceManager.getExtensions
    addToClassLoaderClassPath(path, ant, Class.forName("org.jetbrains.jps.incremental.BuilderService").classLoader)
  }

  @CompileDynamic
  private static void addToClassLoaderClassPath(String path, AntBuilder ant, ClassLoader classLoader) {
    if (new File(path).exists()) {
      if (classLoader instanceof GroovyClassLoader) {
        classLoader.addClasspath(path)
      }
      else if (classLoader instanceof AntClassLoader) {
        classLoader.addPathElement(path)
      }
      else if (classLoader.metaClass.respondsTo(classLoader, 'addURL', URL)) {
        classLoader.addURL(new File(path).toURI().toURL())
      }
      else {
        throw new BuildException("Cannot add to classpath: non-groovy or ant classloader $classLoader which doesn't have 'addURL' method")
      }
      ant.project.log("'$path' added to classpath", Project.MSG_INFO)
    }
    else {
      throw new BuildException("Cannot add to classpath: $path doesn't exist")
    }
  }

  static String replaceAll(String text, Map<String, String> replacements, String marker = "__") {
    replacements.each {
      text = text.replace("$marker$it.key$marker", it.value)
    }
    return text
  }

  static void copyAndPatchFile(@NotNull Path sourcePath, @NotNull Path targetPath, Map<String, String> replacements, String marker = "__", String lineSeparator = "") {
    Files.createDirectories(targetPath.parent)
    String content = replaceAll(Files.readString(sourcePath), replacements, marker)
    if (!lineSeparator.isEmpty()) {
      content = StringUtilRt.convertLineSeparators(content, lineSeparator)
    }
    Files.writeString(targetPath, content)
  }

  static PrintStream getRealSystemOut() {
    try {
      //if the build script is running under Ant or AntBuilder it may replace the standard System.out
      def field = Main.class.getDeclaredField("out")
      field.accessible = true
      return (PrintStream) field.get(null) // No longer works in recent Ant 1.9.x and 1.10
    }
    catch (Throwable ignored) {
    }
    try {
      def clazz = Class.forName("org.jetbrains.jps.gant.GantWithClasspathTask")
      def field = clazz.getDeclaredField("out")
      field.setAccessible(true)
      def out = field.get(null)
      if (out != null) return out as PrintStream
    }
    catch (Throwable ignored) {
    }
    return System.out
  }

  static void defineFtpTask(BuildContext context) {
    List<File> commonsNetJars = context.project.libraryCollection.findLibrary("commons-net").getFiles(JpsOrderRootType.COMPILED) +
      [context.paths.communityHomeDir.resolve("lib/ant/lib/ant-commons-net.jar").toFile()]
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
    org.apache.tools.ant.types.Path ftpPath = new org.apache.tools.ant.types.Path(ant.project)
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
                                [context.paths.communityHomeDir.resolve("lib/ant/lib/ant-jsch.jar").toFile()]
    def ant = context.ant
    def sshTaskLoaderRef = "SSH_TASK_CLASS_LOADER"
    if (ant.project.hasReference(sshTaskLoaderRef)) return

    org.apache.tools.ant.types.Path pathSsh = new org.apache.tools.ant.types.Path(ant.project)
    jschJars.each {
      pathSsh.createPathElement().setLocation(it)
    }
    ant.project.addReference(sshTaskLoaderRef, new SplitClassLoader(ant.project.getClass().getClassLoader(), pathSsh, ant.project,
                                                                    ["SSHExec", "SSHBase", "LogListener", "SSHUserInfo"] as String[]))
    ant.taskdef(name: "sshexec", classname: "org.apache.tools.ant.taskdefs.optional.ssh.SSHExec", loaderRef: sshTaskLoaderRef)
  }

  static List<String> propertiesToJvmArgs(Map<String, Object> properties) {
    List<String> result = new ArrayList<String>(properties.size())
    for (Map.Entry<String, Object> entry : properties.entrySet()) {
      result.add("-D" + entry.key + "=" + entry.value)
    }
    return result
  }

  static void convertLineSeparators(@NotNull Path file, @NotNull String newLineSeparator) {
    String data = Files.readString(file)
    String convertedData = StringUtilRt.convertLineSeparators(data, newLineSeparator)
    if (data != convertedData) {
      Files.writeString(file, convertedData)
    }
  }
}