// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.ProductModulesLayout
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule

import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.Consumer
import java.util.function.Supplier
import java.util.regex.Matcher

/**
 * Creates files with modules and class loading order.
 */
@CompileStatic
final class ReorderJarTask {
  private ReorderJarTask() {
  }

  static BuildTaskRunnable<Void> createReorderJarTask(@NotNull Path loadingOrderFilePath, PlatformLayout platformLayout) {
    BuildTaskRunnable.task(BuildOptions.GENERATE_JAR_ORDER_STEP, "Build jar order file", new Consumer<BuildContext>() {
      @Override
      void accept(BuildContext buildContext) {
        Files.deleteIfExists(loadingOrderFilePath)

        Path modulesOrder = buildContext.paths.tempDir.resolve("modules-order.txt")
        Path classesOrder = buildContext.paths.tempDir.resolve("classpath-order.txt")
        Files.deleteIfExists(modulesOrder)
        Files.deleteIfExists(classesOrder)

        List<String> modulesToIndex =
          buildContext.productProperties.productLayout.mainModules + DistributionJARsBuilder.getModulesToCompile(buildContext)
        buildContext.messages.progress("Generating jar loading order for ${modulesToIndex.size()} modules")

        BuildTasksImpl.runApplicationStarter(buildContext, buildContext.paths.tempDir.resolve("jarOrder"), modulesToIndex,
                                             List.<String> of("jarOrder", modulesOrder.toString(), classesOrder.toString()),
                                             Collections.<String, Object> singletonMap("idea.log.classpath.info", true),
                                             List.of("-ea", "-Xmx1024m"))

        Map<String, String> libModulesToJar = DistributionJARsBuilder.getModuleToJarMap(platformLayout, buildContext)
        Map<String, String> pluginsToJar = getPluginModulesToJar(buildContext)
        Map<String, String> pathToToJarName = getLibraryPathToJarName(platformLayout, buildContext)
        Map<String, String> pathToModuleName = getModulePathToModuleName(libModulesToJar.keySet() + pluginsToJar.keySet(), buildContext)

        addClassesOrderFile(pathToModuleName, pathToToJarName, pluginsToJar, libModulesToJar, classesOrder, loadingOrderFilePath,
                            buildContext)
        addJarOrderFile(pathToModuleName, pathToToJarName, libModulesToJar, modulesOrder, classesOrder, buildContext)
      }
    })
  }

  private static Map<String, String> getLibraryPathToJarName(PlatformLayout platformLayout, BuildContext buildContext) {
    def libWithoutVersion = new HashSet(platformLayout.projectLibrariesWithRemovedVersionFromJarNames)
    def libraryJarPathToJarName = new HashMap()
    buildContext.project.libraryCollection.libraries.each {
      def name = it.getName()
      for (File libFile : it.getFiles(JpsOrderRootType.COMPILED)) {
        def fileName = libFile.getName()
        def jarName = fileName
        if (libWithoutVersion.contains(name)) {
          def candidate = getLibraryNameWithoutVersion(libFile)
          if (candidate != null) {
            jarName = candidate
          }
        }
        def jarPath = (SystemInfo.isWindows) ? '/' + FileUtil.toSystemIndependentName(libFile.getPath()) : libFile.getPath()
        libraryJarPathToJarName.put(jarPath, jarName)
      }
    }
    return libraryJarPathToJarName
  }

  private static String getLibraryNameWithoutVersion(File library) {
    Matcher matcher = library.name =~ LayoutBuilder.JAR_NAME_WITH_VERSION_PATTERN
    if (matcher.matches()) {
      return matcher.group(1) + ".jar"
    }
    return null
  }

  private static Map<String, String> getModulePathToModuleName(Set<String> allModules, BuildContext buildContext) {
    Map<String, String> pathToModuleName = new HashMap<String, String>()
    for (String moduleName in allModules) {
      JpsModule module = buildContext.findModule(moduleName)
      if (module == null) {
        continue
      }
      String classpath = SystemInfoRt.isWindows ? '/' + FileUtilRt.toSystemIndependentName(buildContext.getModuleOutputPath(module)) : buildContext.getModuleOutputPath(module)
      pathToModuleName.put(classpath, moduleName)
    }
    return pathToModuleName
  }

  private static Map<String, String> getPluginModulesToJar(@NotNull BuildContext buildContext) {
    Map<String, String> result = new HashMap<String, String>()
    ProductModulesLayout productLayout = buildContext.productProperties.productLayout
    List<PluginLayout> allPlugins = DistributionJARsBuilder.getPluginsByModules(buildContext, productLayout.bundledPluginModules + productLayout.pluginModulesToPublish)
    for (PluginLayout plugin : allPlugins) {
      String directory = DistributionJARsBuilder.getActualPluginDirectoryName(plugin, buildContext)
      DistributionJARsBuilder.getModuleToJarMap(plugin, buildContext, result, "${DistributionJARsBuilder.PLUGINS_DIRECTORY}/$directory/lib/")
    }
    return result
  }

  private static void addClassesOrderFile(Map<String, String> pathToModuleName,
                                          Map<String, String> pathToToJarName,
                                          Map<String, String> pluginModulesToJar,
                                          Map<String, String> libModulesToJar,
                                          @NotNull Path classesOrderFile,
                                          @NotNull Path loadingOrderFilePath,
                                          @NotNull BuildContext buildContext) {
    if (!Files.exists(classesOrderFile)) {
      buildContext.messages.warning("Failed to generate classes order file: $classesOrderFile doesn't exist")
      return
    }

    List<String> lines = Files.readAllLines(classesOrderFile)
    if (lines.isEmpty()) {
      buildContext.messages.warning("Failed to generate classes order file: $classesOrderFile is empty")
      return
    }

    List<String> resultLines = new ArrayList<String>()
    for (String line : lines) {
      def i = line.indexOf(':')
      if (-1 == i) {
        continue
      }
      def className = line.substring(0, i)
      def modulePath = line.substring(i + 1)
      if (modulePath.endsWith(".jar")) {
        String jarName = pathToToJarName.get(modulePath)
        //possible jar from a plugin
        if (jarName == null) continue
        resultLines.add(className + ":/lib/" + jarName)
      }
      else {
        def moduleName = pathToModuleName.get(modulePath)
        if (moduleName == null) {
          continue
        }
        def libJarName = libModulesToJar.get(moduleName)
        if (libJarName != null) {
          resultLines.add(className + ":/lib/" + libJarName)
        }
        else {
          String moduleJarName = pluginModulesToJar.get(moduleName)
          if (moduleName == null) {
            continue
          }
          resultLines.add(className + ":" + moduleJarName)
        }
      }
    }

    Files.writeString(loadingOrderFilePath, String.join("\n", resultLines))
    buildContext.messages.info("Completed generating classes order file. Before preparing: ${lines.size()} after: ${resultLines.size()}")
  }

  private static void addJarOrderFile(Map<String, String> pathToModuleName,
                                      Map<String, String> pathToToJarName,
                                      Map<String, String> libModulesToJar,
                                      @NotNull Path modulesOrderFile,
                                      @NotNull Path classesOrderFile,
                                      @NotNull BuildContext buildContext) {
    List<String> lines
    try {
      lines = Files.readAllLines(modulesOrderFile)
    }
    catch (NoSuchFileException ignored) {
      buildContext.messages.warning("Failed to generate jar loading order file: $modulesOrderFile doesn't exist")
      return
    }

    Set<String> jarFileNames = new LinkedHashSet<>()
    for (String line : lines) {
      String jarName
      if (line.endsWith(".jar")) {
        jarName = pathToToJarName.get(line)
      }
      else {
        String moduleName = pathToModuleName.get(line)
        jarName = moduleName != null ? libModulesToJar.get(moduleName) : null
      }
      if (jarName != null) {
        jarFileNames.add(jarName)
      }
    }

    if (jarFileNames.isEmpty()) {
      buildContext.messages.warning("Jar order file is empty")
      return
    }

    Files.writeString(classesOrderFile, String.join("\n", jarFileNames))
    buildContext.addResourceFile(classesOrderFile)
    buildContext.messages.info("Completed generating jar file. Before preparing: ${lines.size()} after: ${jarFileNames.size()}")
  }

  static void reorderJARs(@NotNull Path loadingOrderFilePath, @NotNull BuildContext buildContext, @NotNull PlatformLayout platformLayout) {
    buildContext.messages.block("Reorder JARs", new Supplier<Void>() {
      @Override
      Void get() {
        String targetDirectory = buildContext.paths.distAll
        buildContext.messages.progress("Reordering *.jar files in $targetDirectory")
        Collection<String> moduleJars = platformLayout.moduleJars.entrySet()
          .collect(new HashSet()) {
            DistributionJARsBuilder.getActualModuleJarPath(it.key, it.value, platformLayout.explicitlySetJarPaths, buildContext)
          }

        StringBuilder ignoredJarFiles = new StringBuilder()
        DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(buildContext.paths.distAll, "lib"))
        try {
          for (Path file : stream) {
            String path = file.toString()
            if (path.endsWith(".jar") && !moduleJars.contains(path)) {
              ignoredJarFiles.append(path).append('\n' as char)
            }
          }
        }
        finally {
          stream.close()
        }

        Path ignoredJarsFile = buildContext.paths.tempDir.resolve("required_for_dist.txt")
        Files.writeString(ignoredJarsFile, ignoredJarFiles)

        BuildUtils.runJava(buildContext,
                           "com.intellij.util.io.zip.ReorderJarsMain",
                           List.of(loadingOrderFilePath.toString(), targetDirectory, targetDirectory, ignoredJarsFile.parent.toString()),
                           List.of("-Xmx2048m"),
                           buildContext.getModuleRuntimeClasspath(buildContext.findRequiredModule("intellij.platform.util"), false))
        return null
      }
    })
  }
}
