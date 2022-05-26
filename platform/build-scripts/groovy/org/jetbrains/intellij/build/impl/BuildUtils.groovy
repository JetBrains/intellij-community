// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.util.XmlDomReader
import com.intellij.util.lang.UrlClassLoader
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.apache.tools.ant.AntClassLoader
import org.apache.tools.ant.BuildException
import org.apache.tools.ant.Main
import org.apache.tools.ant.Project
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.NoSuchFileException
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
      else if (classLoader instanceof UrlClassLoader) {
        classLoader.addFiles(List.of(Path.of(path)))
      }
      else if (classLoader.metaClass.respondsTo(classLoader, 'addURL', URL)) {
        classLoader.addURL(FileUtil.fileToUri(new File(path)).toURL())
      }
      else {
        throw new BuildException(
          "Cannot add to classpath: non-groovy or ant classloader $classLoader which doesn't have 'addURL' method\n" +
          "most likely you need to add -Djava.system.class.loader=org.jetbrains.intellij.build.impl.BuildScriptsSystemClassLoader to run configuration\n\n")
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

  static void replaceAll(Path file, String marker, String ...replacements) {
    String text = Files.readString(file)
    for (int i = 0; i < replacements.length; i += 2) {
      text = text.replace(marker + replacements[i] + marker, replacements[i + 1])
    }
    Files.writeString(file, text)
  }

  static String replaceAll(String text, String marker, String ...replacements) {
    for (int i = 0; i < replacements.length; i += 2) {
      text = text.replace(marker + replacements[i] + marker, replacements[i + 1])
    }
    return text
  }

  static void copyAndPatchFile(@NotNull Path sourcePath,
                               @NotNull Path targetPath,
                               Map<String, String> replacements,
                               String marker = "__",
                               String lineSeparator = "") {
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

  static List<String> propertiesToJvmArgs(Map<String, Object> properties) {
    List<String> result = new ArrayList<String>(properties.size())
    for (Map.Entry<String, Object> entry : properties.entrySet()) {
      addVmProperty(result, entry.key, entry.value.toString())
    }
    return result
  }

  static void addVmProperty(@NotNull List<String> args, @NotNull String key, @Nullable String value) {
    if (value != null) {
      args.add("-D" + key + "=" + value)
    }
  }

  static void convertLineSeparators(@NotNull Path file, @NotNull String newLineSeparator) {
    String data = Files.readString(file)
    String convertedData = StringUtilRt.convertLineSeparators(data, newLineSeparator)
    if (data != convertedData) {
      Files.writeString(file, convertedData)
    }
  }

  static List<Path> getPluginJars(Path pluginPath) {
    return Files.newDirectoryStream(pluginPath.resolve("lib"), "*.jar").withCloseable { it.toList() }
  }

  @Nullable
  static String readPluginId(Path pluginJar) {
    if (!pluginJar.toString().endsWith(".jar") || !Files.isRegularFile(pluginJar)) {
      return null
    }

    try {
      FileSystems.newFileSystem(pluginJar, null).withCloseable {
        return XmlDomReader.readXmlAsModel(Files.newInputStream(it.getPath("META-INF/plugin.xml"))).getChild("id")?.content
      }
    }
    catch (NoSuchFileException ignore) {
      return null
    }
  }

  static boolean isUnderJpsBootstrap() {
    return System.getenv("JPS_BOOTSTRAP_COMMUNITY_HOME") != null
  }
}