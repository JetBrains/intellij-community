// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtil
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleLibraryFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleOutputEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectLibraryEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectStructureMapping
import org.jetbrains.jps.model.JpsCompositeElement
import org.jetbrains.jps.model.JpsElementReference
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsDependencyElement
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference

import java.lang.invoke.MethodHandle
import java.nio.file.Path
import java.util.function.Consumer
import java.util.function.IntConsumer
import java.util.function.Predicate
import java.util.regex.Matcher
import java.util.stream.Stream

import static org.jetbrains.intellij.build.impl.ProjectLibraryData.PackMode

@CompileStatic
final class JarPackager {
  static void pack(Map<String, List<String>> actualModuleJars,
                   Path outputDir,
                   BaseLayout layout,
                   LayoutBuilder.LayoutSpec layoutSpec,
                   BuildContext buildContext) {
    Map<Path, JpsLibrary> copiedFiles = new HashMap<>()

    for (ModuleLibraryData data in layout.includedModuleLibraries) {
      JpsModule module = buildContext.findRequiredModule(data.moduleName)
      JpsLibrary library = module.libraryCollection.libraries.find {layoutSpec.getLibraryName(it) == data.libraryName}
      if (library == null) {
        throw new IllegalArgumentException("Cannot find library ${data.libraryName} in '${data.moduleName}' module")
      }

      List<Path> files = getLibraryFiles(library, copiedFiles, true)
      String fileName = libNameToMergedJarFileName(data.libraryName)

      String relativePathToLibFile = layoutSpec.getOutputFilePath("lib")
      String relativePath = data.relativeOutputPath
      Path targetFile = null
      if (relativePath != null) {
        if (relativePath.endsWith(".jar")) {
          int index = relativePath.lastIndexOf('/')
          if (index == -1) {
            fileName = relativePath
            relativePath = ""
          }
          else {
            fileName = relativePath.substring(index + 1)
            relativePath = relativePath.substring(0, index)
          }
        }
        if (!relativePath.isEmpty()) {
          relativePathToLibFile += '/' + relativePath
          targetFile = outputDir.resolve(relativePath).resolve(fileName)
        }
      }

      if (targetFile == null) {
        targetFile = outputDir.resolve(fileName)
      }
      buildLibrary(library, relativePathToLibFile, targetFile, files, layoutSpec, buildContext)
    }

    Map<JpsLibrary, List<Path>> librariesToMerge = packLibraries(actualModuleJars, outputDir, layout, layoutSpec, copiedFiles, buildContext)

    boolean isRootDir = buildContext.paths.distAllDir == outputDir.parent
    if (isRootDir) {
      // kotlinx- libs to one kotlinx.jar
      mergeLibsByPredicate("kotlinx.jar", librariesToMerge, outputDir, layoutSpec, buildContext) { it.startsWith("kotlinx-") }

      // see ClassPathUtil.getUtilClassPath
      Set<String> libsThatUsedInJps = Set.of(
        "ASM",
        "aalto-xml",
        "netty-buffer",
        "netty-codec-http",
        "netty-handler-proxy",
        "fastutil-min",
        "gson",
        "Log4J",
        "Slf4j",
        // see getBuildProcessApplicationClasspath - used in JPS
        "lz4-java",
        "maven-resolver-provider",
        "OroMatcher",
        "jgoodies-forms",
        "jgoodies-common",
        "NanoXML",
        // see ArtifactRepositoryManager.getClassesFromDependencies
        "plexus-utils",
        "Guava",
        "http-client",
        "commons-codec",
        "commons-logging",
        "commons-lang3"
      )
      mergeLibsByPredicate("3rd-party-rt.jar", librariesToMerge, outputDir, layoutSpec, buildContext) { libsThatUsedInJps.contains(it) }
    }
    List libSources
    if (librariesToMerge.isEmpty()) {
      libSources = null
    }
    else {
      boolean isSeparateUberJar = isRootDir || actualModuleJars.size() != 1
      Path uberJarFile = outputDir.resolve(isSeparateUberJar ? "3rd-party.jar" : actualModuleJars.keySet().first())
      libSources = filesToSourceWithMappings(layoutSpec, uberJarFile, librariesToMerge, buildContext)
      if (isSeparateUberJar) {
        buildJar(uberJarFile, libSources, !layoutSpec.copyFiles, buildContext)
        libSources = null
      }
    }

    for (Map.Entry<String, List<String>> entry in actualModuleJars.entrySet()) {
      String jarPath = entry.key
      Path jarFile = outputDir.resolve(jarPath)
      List sourceList = new ArrayList()
      if (libSources != null) {
        sourceList.addAll(libSources)
        libSources = null
      }
      packModuleOutputAndUnpackedProjectLibraries(entry.value, jarPath, jarFile, layoutSpec, buildContext, layout, sourceList)
    }
  }

  private static void mergeLibsByPredicate(String jarName,
                                           Map<JpsLibrary, List<Path>> librariesToMerge,
                                           Path outputDir,
                                           LayoutBuilder.LayoutSpec layoutSpec,
                                           BuildContext buildContext,
                                           Predicate<String> predicate) {
    Map<JpsLibrary, List<Path>> kotlinxToMerge = new HashMap<>()

    Iterator<Map.Entry<JpsLibrary, List<Path>>> iterator = librariesToMerge.entrySet().iterator()
    while (iterator.hasNext()) {
      Map.Entry<JpsLibrary, List<Path>> entry = iterator.next()
      if (predicate.test(entry.key.name)) {
        iterator.remove()
        kotlinxToMerge.put(entry.key, entry.value)
      }
    }

    if (kotlinxToMerge.isEmpty()) {
      return
    }

    Path uberJarFile = outputDir.resolve(jarName)
    List libSources = filesToSourceWithMappings(layoutSpec, uberJarFile, kotlinxToMerge, buildContext)
    buildJar(uberJarFile, libSources, !layoutSpec.copyFiles, buildContext)
  }

  private static List filesToSourceWithMappings(LayoutBuilder.LayoutSpec layoutSpec,
                                                Path uberJarFile,
                                                Map<JpsLibrary, List<Path>> librariesToMerge,
                                                BuildContext buildContext) {
    List sources = new ArrayList()
    String relativeToDistTargetFilePath = layoutSpec.getOutputFilePath("lib/" + uberJarFile.fileName.toString())
    for (Map.Entry<JpsLibrary, List<Path>> entry : librariesToMerge.entrySet()) {
      filesToSourceWithMapping(sources, entry.value, layoutSpec.projectStructureMapping, entry.key, relativeToDistTargetFilePath,
                               buildContext)
    }
    sources.sort(null)
    sources
  }

  private static Stream<JpsLibrary> getModuleLibs(Map<String, List<String>> actualModuleJars,
                                                  BaseLayout layout,
                                                  LayoutBuilder.LayoutSpec layoutSpec,
                                                  BuildContext buildContext) {
    // include all module libraries from the plugin modules added to IDE classpath to layout
    return actualModuleJars.entrySet()
      .stream()
      .filter { !it.key.contains("/") }
      .flatMap { it.value.stream() }
      .filter { !layout.modulesWithExcludedModuleLibraries.contains(it) }
      .flatMap { moduleName ->
        Collection<String> excluded = layout.excludedModuleLibraries.get(moduleName)
        buildContext.findRequiredModule(moduleName).dependenciesList.dependencies.stream()
          .filter(new Predicate<JpsDependencyElement>() {
            @Override
            boolean test(JpsDependencyElement it) {
              if (it instanceof JpsLibraryDependency &&
                  ((JpsLibraryDependency)it)?.libraryReference?.parentReference?.resolve() instanceof JpsModule) {
                return JpsJavaExtensionService.instance.getDependencyExtension(it)
                         ?.scope?.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME) ?: false
              }
              else {
                return false
              }
            }
          })
          .map { ((JpsLibraryDependency)it).library }
          .filter(new Predicate<JpsLibrary>() {
            @Override
            boolean test(JpsLibrary library) {
              String libraryName = layoutSpec.getLibraryName(library)
              return !excluded.contains(libraryName) &&
                     !layout.includedModuleLibraries.any { it.libraryName == libraryName }
            }
          })
      }
  }

  static Path getSearchableOptionsDir(BuildContext buildContext) {
    return buildContext.paths.tempDir.resolve("searchableOptionsResult")
  }

  private static void packModuleOutputAndUnpackedProjectLibraries(Collection<String> modules,
                                                                  String jarPath,
                                                                  Path jarFile,
                                                                  LayoutBuilder.LayoutSpec layoutSpec,
                                                                  BuildContext buildContext,
                                                                  BaseLayout layout,
                                                                  List sourceList) {
    Map<String, Integer> moduleNameToSize = new HashMap<>()
    MethodHandle addModuleSources = BuildHelper.getInstance(buildContext).addModuleSources
    Path searchableOptionsDir = getSearchableOptionsDir(buildContext)
    for (String moduleName in modules) {
      addModuleSources.invokeWithArguments(moduleName,
                                           moduleNameToSize,
                                           Path.of(buildContext.getModuleOutputPath(buildContext.findRequiredModule(moduleName))),
                                           layoutSpec.moduleOutputPatches.get(moduleName) ?: Collections.<Path> emptyList(),
                                           searchableOptionsDir,
                                           layout.moduleExcludes.get(moduleName),
                                           sourceList,
                                           buildContext.messages)
    }

    String relativeToDistFilePath = layoutSpec.getOutputFilePath("lib/" + jarPath)
    MethodHandle createZipSource = BuildHelper.getInstance(buildContext).createZipSource
    for (String libraryName in layout.projectLibrariesToUnpack.get(jarPath)) {
      JpsLibrary library = buildContext.project.libraryCollection.findLibrary(libraryName)
      if (library == null) {
        buildContext.messages.error("Project library '$libraryName' from $jarPath should be unpacked but it isn't found")
      }
      for (File ioFile : library.getFiles(JpsOrderRootType.COMPILED)) {
        Path file = ioFile.toPath()
        sourceList.add(createZipSource.invokeWithArguments(file, new IntConsumer() {
          @Override
          void accept(int size) {
            layoutSpec.projectStructureMapping.addEntry(new ProjectLibraryEntry(relativeToDistFilePath, library.name, file, size))
          }
        }))
      }
    }

    buildJar(jarFile, sourceList, !layoutSpec.copyFiles, buildContext)
    for (String moduleName in modules) {
      layoutSpec.projectStructureMapping.addEntry(new ModuleOutputEntry(relativeToDistFilePath, moduleName, moduleNameToSize.get(moduleName)))
    }
  }

  private static Map<JpsLibrary, List<Path>> packLibraries(Map<String, List<String>> actualModuleJars,
                                                           Path outputDir,
                                                           BaseLayout layout,
                                                           LayoutBuilder.LayoutSpec layoutSpec,
                                                           Map<Path, JpsLibrary> copiedFiles,
                                                           BuildContext buildContext) {
    Map<JpsLibrary, List<Path>> toMerge = new HashMap<JpsLibrary, List<Path>>()
    String basePathToLibFile = layoutSpec.getOutputFilePath("lib")

    MethodHandle isLibraryMergeable = BuildHelper.getInstance(buildContext).isLibraryMergeable

    if (!layout.includedProjectLibraries.isEmpty()) {
      buildContext.messages.debug("included project libraries: " + layout.includedProjectLibraries.join("\n"))
    }

    for (ProjectLibraryData libraryData in layout.includedProjectLibraries) {
      // the only purpose of this - construct path for DistributionFileEntry.path
      String relativePathToLibFile = basePathToLibFile
      Path outputDirForLibFile = outputDir

      String relativePath = libraryData.relativeOutputPath
      if (relativePath != null && !relativePath.isEmpty()) {
        outputDirForLibFile = outputDirForLibFile.resolve(relativePath)
        relativePathToLibFile += "/" + relativePath
      }

      JpsLibrary library = buildContext.project.libraryCollection.findLibrary(libraryData.libraryName)
      if (library == null) {
        throw new IllegalArgumentException("Cannot find library ${libraryData.libraryName} in the project")
      }

      String libName = library.name
      List<Path> files = getLibraryFiles(library, copiedFiles, false)

      PackMode packMode = libraryData.packMode
      if (layout instanceof PlatformLayout &&
          ((PlatformLayout)layout).projectLibrariesWithRemovedVersionFromJarNames.contains(libraryData.libraryName)) {
        packMode = PackMode.STANDALONE_SEPARATE_WITHOUT_VERSION_NAME
      }
      else if (packMode == PackMode.MERGED && !isLibraryMergeable.invokeWithArguments(libName)) {
        packMode = PackMode.STANDALONE_MERGED
      }

      if (packMode == PackMode.MERGED) {
        toMerge.put(library, files)
      }
      else if (packMode == PackMode.STANDALONE_MERGED) {
        String fileName = libNameToMergedJarFileName(libName)
        buildLibrary(library, relativePathToLibFile, outputDirForLibFile.resolve(fileName), files, layoutSpec, buildContext)
      }
      else {
        for (Path file : files) {
          String fileName = file.fileName.toString()
          if (packMode == PackMode.STANDALONE_SEPARATE_WITHOUT_VERSION_NAME) {
            fileName = removeVersionFromJar(fileName)
          }
          buildLibrary(library, relativePathToLibFile, outputDirForLibFile.resolve(fileName), List.of(file), layoutSpec, buildContext)
        }
      }
    }

    getModuleLibs(actualModuleJars, layout, layoutSpec, buildContext).forEach(new Consumer<JpsLibrary>() {
      @Override
      void accept(JpsLibrary library) {
        String libName = library.name
        List<Path> files = getLibraryFiles(library, copiedFiles, true)

        if (libName == "async-profiler-windows") {
          // custom name, removeVersionFromJar doesn't support strings like `2.1-ea-4`
          buildLibrary(library, basePathToLibFile, outputDir.resolve("async-profiler-windows.jar"), files, layoutSpec, buildContext)
          return
        }

        for (int i = files.size() - 1; i >= 0; i--) {
          Path file = files.get(i)
          String fileName = file.fileName.toString()
          boolean isBnd = libName == "bndlib" || libName == "bndlib-resolve" || libName == "bndlib-repository" || libName == "bundlor"
          //noinspection SpellCheckingInspection
          if (fileName.endsWith("-rt.jar") || fileName.startsWith("jps-") || fileName.contains("-agent") ||
              fileName == "yjp-controller-api-redist.jar" || isBnd) {
            files.remove(i)
            buildLibrary(library, basePathToLibFile, outputDir.resolve(isBnd ? fileName : removeVersionFromJar(fileName)), List.of(file), layoutSpec, buildContext)
          }
        }
        if (!files.isEmpty()) {
          toMerge.put(library, files)
        }
      }
    })

    return toMerge
  }

  private static String removeVersionFromJar(String fileName) {
    Matcher matcher = fileName =~ LayoutBuilder.JAR_NAME_WITH_VERSION_PATTERN
    if (matcher.matches()) {
      return matcher.group(1) + ".jar"
    }
    else {
      return fileName
    }
  }

  private static List<Path> getLibraryFiles(JpsLibrary library, Map<Path, JpsLibrary> copiedFiles, boolean isModuleLevel) {
    List<File> files = library.getFiles(JpsOrderRootType.COMPILED)
    List<Path> nioFiles = new ArrayList<Path>(files.size())
    String libName = library.name
    for (File file : files) {
      Path nioFile = file.toPath()
      JpsLibrary alreadyCopiedFor = copiedFiles.putIfAbsent(nioFile, library)
      if (alreadyCopiedFor != null) {
        // check name - we allow to have same named module level library name
        if (isModuleLevel && alreadyCopiedFor.name == libName) {
          continue
        }
        throw new IllegalStateException("File $nioFile from $libName is already provided by ${alreadyCopiedFor.name} library")
      }

      nioFiles.add(nioFile)
    }
    return nioFiles
  }

  private static String libNameToMergedJarFileName(String libName) {
    FileUtil.sanitizeFileName(libName.toLowerCase(), false) + ".jar"
  }

  private static filesToSourceWithMapping(List to,
                                          List<Path> files,
                                          ProjectStructureMapping projectStructureMapping,
                                          JpsLibrary library,
                                          String relativeToDistTargetFilePath,
                                          BuildContext buildContext) {
    JpsElementReference<? extends JpsCompositeElement> parentReference = library.createReference().getParentReference()
    boolean isModuleLibrary = parentReference instanceof JpsModuleReference
    MethodHandle createZipSource = BuildHelper.getInstance(buildContext).createZipSource
    for (Path file : files) {
      IntConsumer consumer = createLibSizeConsumer(file, isModuleLibrary, projectStructureMapping, relativeToDistTargetFilePath,
                                                   library, isModuleLibrary ? (JpsModuleReference)parentReference : null)
      to.add(createZipSource.invokeWithArguments(file, consumer))
    }
  }

  private static IntConsumer createLibSizeConsumer(Path file,
                                                   boolean isModuleLibrary,
                                                   ProjectStructureMapping projectStructureMapping,
                                                   String relativeToDistTargetFilePath,
                                                   JpsLibrary library,
                                                   JpsModuleReference moduleReference) {
    return new IntConsumer() {
      @Override
      void accept(int size) {
        if (isModuleLibrary) {
          projectStructureMapping.addEntry(new ModuleLibraryFileEntry(relativeToDistTargetFilePath, moduleReference.moduleName, file, size))
        }
        else {
          projectStructureMapping.addEntry(new ProjectLibraryEntry(relativeToDistTargetFilePath, library.name, file, size))
        }
      }
    }
  }

  private static void buildLibrary(JpsLibrary library,
                                   String relativePathToLibFile,
                                   Path targetFile,
                                   List<Path> files,
                                   LayoutBuilder.LayoutSpec layoutSpec,
                                   BuildContext buildContext) {
    List sources = new ArrayList()
    String relativeToDistTargetFilePath = relativePathToLibFile + "/" + targetFile.fileName.toString()
    filesToSourceWithMapping(sources, files, layoutSpec.projectStructureMapping, library, relativeToDistTargetFilePath, buildContext)
    buildJar(targetFile, sources, !layoutSpec.copyFiles, buildContext)
  }

  private static buildJar(Path targetFile, List sources, boolean dryRun, BuildContext buildContext) {
    BuildHelper.getInstance(buildContext).buildJar.invokeWithArguments(targetFile, sources, buildContext.messages, dryRun)
  }
}