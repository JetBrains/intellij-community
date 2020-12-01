// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.util.lang.UrlClassLoader
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.jps.model.module.JpsModule

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.file.Path
import java.nio.file.Paths

@CompileStatic
final class BuildHelper {
  private static volatile BuildHelper instance
  private static final MethodHandles.Lookup lookup = MethodHandles.lookup()

  private final UrlClassLoader helperClassLoader
  private final MethodHandle zipForWindows
  private final MethodHandle runJavaHandle
  final MethodHandle brokenPluginsTask

  final MethodHandle reorderJars

  private BuildHelper(UrlClassLoader helperClassLoader) {
    this.helperClassLoader = helperClassLoader
    Class<?> iterable = Iterable.class as Class<?>
    Class<?> voidClass = void.class as Class<?>
    Class<?> logger = System.Logger.class as Class<?>
    Class<?> path = Path.class as Class<?>

    zipForWindows = lookup.findStatic(helperClassLoader.loadClass("org.jetbrains.intellij.build.io.ZipKt"),
                                      "zipForWindows",
                                      MethodType.methodType(voidClass, path, iterable))

    runJavaHandle = lookup.findStatic(helperClassLoader.loadClass("org.jetbrains.intellij.build.io.ProcessKt"),
                                      "runJava", MethodType.methodType(voidClass, String.class as Class<?>, iterable, iterable, iterable,
                                                                       logger))

    brokenPluginsTask = lookup.findStatic(helperClassLoader.loadClass("org.jetbrains.intellij.build.tasks.BrokenPluginsKt"),
                                          "buildBrokenPlugins",
                                          MethodType.methodType(voidClass,
                                                                path, String.class as Class<?>, boolean.class as Class<?>, logger))

    reorderJars = lookup.findStatic(helperClassLoader.loadClass("org.jetbrains.intellij.build.tasks.ReorderJarsKt"),
                                                     "reorderJars",
                                                     MethodType.methodType(voidClass, path, path, iterable, path, logger))
  }

  static void zipForWindows(@NotNull BuildContext buildContext, @NotNull Path targetFile, Iterable<Path> dirs) {
    // invoke cannot be called reflectively (as Groovy does)
    getInstance(buildContext).zipForWindows.invokeWithArguments(targetFile, dirs)
  }

  /**
   * Executes a Java class in a forked JVM
   */
  static void runJava(BuildContext buildContext,
                      String mainClass,
                      Iterable<String> args,
                      Iterable<String> jvmArgs,
                      Iterable<String> classPath) {
    getInstance(buildContext).runJavaHandle.invokeWithArguments(mainClass, args, jvmArgs, classPath, buildContext.messages)
  }

  static BuildHelper getInstance(@NotNull BuildContext buildContext) {
    BuildHelper result = instance
    if (result != null) {
      return result
    }

    synchronized (BuildHelper.class) {
      result = instance
      if (result == null) {
        result = loadHelper(buildContext)
        instance = result
      }
    }
    return result
  }

  private static synchronized BuildHelper loadHelper(BuildContext buildContext) {
    JpsModule helperModule = buildContext.findRequiredModule("intellij.idea.community.build.tasks")
    List<String> classPathFiles = buildContext.getModuleRuntimeClasspath(helperModule, false)
    List<URL> classPathUrls = new ArrayList<>(classPathFiles.size())
    for (String filePath : classPathFiles) {
      Path file = Paths.get(filePath).normalize()
      if (!file.endsWith("jrt-fs.jar")) {
        classPathUrls.add(file.toUri().toURL())
      }
    }
    UrlClassLoader classLoader = UrlClassLoader.build()
      .parent(ClassLoader.getSystemClassLoader())
      .usePersistentClasspathIndexForLocalClassDirectories()
      .useCache()
      .allowLock()
      .urls(classPathUrls)
      .get()
    return new BuildHelper(classLoader)
  }
}
