// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.util.lang.UrlClassLoader
import groovy.transform.CompileStatic
import org.codehaus.groovy.tools.RootLoader
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.jps.model.module.JpsModule

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import java.util.function.IntConsumer

@CompileStatic
final class BuildHelper {
  private static volatile BuildHelper instance
  private static final MethodHandles.Lookup lookup = MethodHandles.lookup()

  private final ClassLoader helperClassLoader
  private final MethodHandle zipHandle
  private final MethodHandle bulkZipWithPrefixHandle

  private final MethodHandle runJavaHandle
  private final MethodHandle runProcessHandle

  final MethodHandle brokenPluginsTask
  final MethodHandle reorderJars
  final MethodHandle buildJar
  final MethodHandle createZipSource
  final MethodHandle addModuleSources
  final MethodHandle isLibraryMergeable

  private final MethodHandle copyDirHandle

  private BuildHelper(ClassLoader helperClassLoader) {
    this.helperClassLoader = helperClassLoader
    Class<?> iterable = Iterable.class as Class<?>
    Class<?> voidClass = void.class as Class<?>
    Class<?> logger = System.Logger.class as Class<?>
    Class<?> path = Path.class as Class<?>
    Class<?> bool = boolean.class as Class<?>
    Class<?> long_ = long.class as Class<?>

    copyDirHandle = lookup.findStatic(helperClassLoader.loadClass("org.jetbrains.intellij.build.io.FileKt"),
                                      "copyDir",
                                      MethodType.methodType(voidClass, path, path))

    zipHandle = lookup.findStatic(helperClassLoader.loadClass("org.jetbrains.intellij.build.io.ZipKt"),
                                  "zip",
                                  MethodType.methodType(voidClass, path, Map.class as Class<?>, bool, bool, logger))

    Class<?> list = List.class as Class<?>
    bulkZipWithPrefixHandle = lookup.findStatic(helperClassLoader.loadClass("org.jetbrains.intellij.build.io.ZipKt"),
                                                "bulkZipWithPrefix",
                                                MethodType.methodType(voidClass, path, list, bool, logger))

    Class<?> string = String.class as Class<?>
    runJavaHandle = lookup.findStatic(helperClassLoader.loadClass("org.jetbrains.intellij.build.io.ProcessKt"),
                                      "runJava", MethodType.methodType(voidClass, string, iterable, iterable, iterable,
                                                                       logger, long_))
    runProcessHandle = lookup.findStatic(helperClassLoader.loadClass("org.jetbrains.intellij.build.io.ProcessKt"),
                                         "runProcess", MethodType.methodType(voidClass, list, path, logger))

    brokenPluginsTask = lookup.findStatic(helperClassLoader.loadClass("org.jetbrains.intellij.build.tasks.BrokenPluginsKt"),
                                          "buildBrokenPlugins",
                                          MethodType.methodType(voidClass, path, string, bool, logger))

    reorderJars = lookup.findStatic(helperClassLoader.loadClass("org.jetbrains.intellij.build.tasks.ReorderJarsKt"),
                                                     "reorderJars",
                                                     MethodType.methodType(voidClass,
                                                                           path, path, iterable, path,
                                                                           string, path,
                                                                           logger))


    Class<?> jarBuilder = helperClassLoader.loadClass("org.jetbrains.intellij.build.tasks.JarBuilder")
    buildJar = lookup.findStatic(jarBuilder, "buildJar", MethodType.methodType(voidClass, path, list, logger, bool))

    createZipSource = lookup.findStatic(jarBuilder,
                                        "createZipSource",
                                        MethodType.methodType(Object.class as Class<?>, path, IntConsumer.class as Class<?>))
    addModuleSources = lookup.findStatic(jarBuilder,
                                         "addModuleSources",
                                         MethodType.methodType(voidClass, string, Map.class as Class<?>, path,
                                                              Collection.class as Class<?>, Path, Collection.class as Class<?>, list,
                                                               logger))
    isLibraryMergeable = lookup.findStatic(jarBuilder, "isLibraryMergeable", MethodType.methodType(bool, string,))
  }

  static void copyDir(Path fromDir, Path targetDir, BuildContext buildContext) {
    getInstance(buildContext).copyDirHandle.invokeWithArguments(fromDir, targetDir)
  }

  static void copyFileToDir(Path file, Path targetDir) {
    Files.createDirectories(targetDir)
    Files.copy(file, targetDir.resolve(file.fileName), StandardCopyOption.COPY_ATTRIBUTES)
  }

  static void copyFile(Path file, Path target) {
    Files.createDirectories(target.parent)
    Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES)
  }

  static void moveFile(Path source, Path target) {
    Files.createDirectories(target.parent)
    Files.move(source, target)
  }

  static void zip(@NotNull BuildContext buildContext, @NotNull Path targetFile, @NotNull Path dir) {
    zipWithPrefix(buildContext, targetFile, Collections.singletonList(dir), null)
  }

  static void zipWithPrefix(@NotNull BuildContext buildContext, @NotNull Path targetFile, List<Path> dirs, @Nullable String prefix) {
    Map<Path, String> map = new LinkedHashMap<>(dirs.size())
    for (Path dir : dirs) {
      map.put(dir, prefix ?: "")
    }
    // invoke cannot be called reflectively (as Groovy does)
    getInstance(buildContext).zipHandle.invokeWithArguments(targetFile, map, true, false, buildContext.messages)
  }

  static void bulkZipWithPrefix(@NotNull BuildContext buildContext,
                                @NotNull Path commonSourceDir,
                                @NotNull List<Map.Entry<String, Path>> items,
                                boolean compress) {
    getInstance(buildContext).bulkZipWithPrefixHandle.invokeWithArguments(commonSourceDir, items, compress, buildContext.messages)
  }

  /**
   * Executes a Java class in a forked JVM
   */
  static void runJava(BuildContext buildContext,
                      String mainClass,
                      Iterable<String> args,
                      Iterable<String> jvmArgs,
                      Iterable<String> classPath,
                      long timeoutMillis = TimeUnit.MINUTES.toMillis(10L)) {
    getInstance(buildContext).runJavaHandle.invokeWithArguments(mainClass, args, jvmArgs, classPath, buildContext.messages, timeoutMillis)
  }

  static void runProcess(BuildContext buildContext, List<String> args) {
    getInstance(buildContext).runProcessHandle.invokeWithArguments(args, null, buildContext.messages)
  }

  static void runProcess(BuildContext buildContext, List<String> args, @Nullable Path workingDir) {
    getInstance(buildContext).runProcessHandle.invokeWithArguments(args, workingDir, buildContext.messages)
  }

  // LayoutBuilder sets AntClassLoader, so, we must restore previous context class loader
  static void executeWithHelper(@NotNull BuildContext buildContext, @NotNull Runnable task) {
    BuildHelper helper = getInstance(buildContext)
    if (helper.helperClassLoader instanceof RootLoader) {
      task.run()
      return
    }

    Thread thread = Thread.currentThread()
    ClassLoader old = thread.getContextClassLoader()
    try {
      thread.setContextClassLoader(helper.helperClassLoader)
      task.run()
    }
    finally {
      thread.setContextClassLoader(old)
    }
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
    List<String> classPaths = buildContext.getModuleRuntimeClasspath(helperModule, false)
    List<Path> classPathFiles = new ArrayList<Path>(classPaths.size())
    for (String filePath : classPaths) {
      Path file = Path.of(filePath).normalize()
      if (!file.endsWith("jrt-fs.jar")) {
        classPathFiles.add(file)
      }
    }

    ClassLoader classLoader = UrlClassLoader.build()
      .parent(ClassLoader.getSystemClassLoader())
      .usePersistentClasspathIndexForLocalClassDirectories()
      .useCache()
      .files(classPathFiles)
      .get()
    return new BuildHelper(classLoader)
  }
}
