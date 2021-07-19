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
    List libSources
    if (librariesToMerge.isEmpty()) {
      libSources = null
    }
    else {
      libSources = new ArrayList()
      boolean isSeparateUberJar = actualModuleJars.size() != 1 || buildContext.paths.distAllDir == outputDir.parent
      Path uberJarFile = outputDir.resolve(isSeparateUberJar ? "3rd-party.jar" : actualModuleJars.keySet().first())
      String relativeToDistTargetFilePath = layoutSpec.getOutputFilePath("lib/" + uberJarFile.fileName.toString())
      for (Map.Entry<JpsLibrary, List<Path>> entry : librariesToMerge.entrySet()) {
        filesToSourceWithMapping(libSources, entry.value, layoutSpec.projectStructureMapping, entry.key, relativeToDistTargetFilePath,
                                 buildContext)
      }
      libSources.sort(null)
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
    // the only purpose of this - construct path for DistributionFileEntry.path
    String relativePathToLibFile = layoutSpec.getOutputFilePath("lib")

    MethodHandle isLibraryMergeable = BuildHelper.getInstance(buildContext).isLibraryMergeable

    for (ProjectLibraryData libraryData in layout.includedProjectLibraries) {
      String relativePath = libraryData.relativeOutputPath
      if (relativePath != null && !relativePath.isEmpty()) {
        outputDir = outputDir.resolve(relativePath)
        relativePathToLibFile += "/" + relativePath
      }

      JpsLibrary library = buildContext.project.libraryCollection.findLibrary(libraryData.libraryName)
      if (library == null) {
        throw new IllegalArgumentException("Cannot find library ${libraryData.libraryName} in the project")
      }

      boolean removeVersionFromJarName = layout instanceof PlatformLayout &&
                                         ((PlatformLayout)layout)
                                           .projectLibrariesWithRemovedVersionFromJarNames.contains(libraryData.libraryName)
      String libName = library.name
      List<Path> files = getLibraryFiles(library, copiedFiles, false)
      if (!removeVersionFromJarName && !libraryData.standalone && isLibraryMergeable.invokeWithArguments(libName)) {
        toMerge.put(library, files)
      }
      else {
        List sources
        if (libName != "Gradle" && !removeVersionFromJarName) {
          String fileName = libNameToMergedJarFileName(libName)
          buildLibrary(library, relativePathToLibFile, outputDir.resolve(fileName), files, layoutSpec, buildContext)
        }
        else {
          for (Path file : files) {
            String fileName = file.fileName.toString()
            if (removeVersionFromJarName) {
              Matcher matcher = file.fileName.toString() =~ LayoutBuilder.JAR_NAME_WITH_VERSION_PATTERN
              if (matcher.matches()) {
                fileName = matcher.group(1) + ".jar"
                buildContext.messages.debug(" include $fileName (renamed from ${files[0]}) from library '${library.name}'")
              }
            }
            buildLibrary(library, relativePathToLibFile, outputDir.resolve(fileName), List.of(file), layoutSpec, buildContext)
          }
        }
      }
    }

    getModuleLibs(actualModuleJars, layout, layoutSpec, buildContext).forEach(new Consumer<JpsLibrary>() {
      @Override
      void accept(JpsLibrary library) {
        String libName = library.name
        List<Path> files = getLibraryFiles(library, copiedFiles, true)
        for (int i = files.size() - 1; i >= 0; i--) {
          Path file = files.get(i)
          String fileName = file.fileName.toString()
          //noinspection SpellCheckingInspection
          if (fileName.endsWith("-rt.jar") || fileName.startsWith("jps-") || fileName.contains("-agent") ||
              libName == "async-profiler-windows") {
            files.remove(i)
            buildLibrary(library, relativePathToLibFile, outputDir.resolve(fileName), List.of(file), layoutSpec, buildContext)
          }
        }
        if (!files.isEmpty()) {
          toMerge.put(library, files)
        }
      }
    })

    return toMerge
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
    BuildHelper.getInstance(buildContext).buildJar
      .invokeWithArguments(targetFile, sources, buildContext.messages, dryRun)
  }
}