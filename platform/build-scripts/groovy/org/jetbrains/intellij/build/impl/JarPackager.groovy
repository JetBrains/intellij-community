// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtil
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlin.Triple
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleLibraryFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleOutputEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectLibraryEntry
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
import org.jetbrains.jps.util.JpsPathUtil

import java.lang.invoke.MethodHandle
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.BiFunction
import java.util.function.Function
import java.util.function.IntConsumer
import java.util.function.Predicate
import java.util.regex.Matcher

import static org.jetbrains.intellij.build.impl.ProjectLibraryData.PackMode

@CompileStatic
final class JarPackager {
  private final Map<Path, JarDescriptor> jarDescriptors = new LinkedHashMap<>()
  private final BuildContext context
  private final Collection<DistributionFileEntry> projectStructureMapping = new ConcurrentLinkedQueue<DistributionFileEntry>()
  private final BuildHelper buildHelper

  private JarPackager(BuildContext context) {
    this.context = context
    this.buildHelper = BuildHelper.getInstance(context)
  }

  static void pack(Map<String, List<String>> actualModuleJars, Path outputDir, BuildContext context) {
    pack(actualModuleJars, outputDir, new BaseLayout() {}, new ModuleOutputPatcher(), false, context)
  }

  private static final Map<String, Predicate<String>> EXTRA_MERGE_RULES = new LinkedHashMap<>()

  private final Map<JpsLibrary, ProjectLibraryData> libToMetadata = new HashMap<>()

  static {
    EXTRA_MERGE_RULES.put("groovy.jar", new Predicate<String>() {
      @Override
      boolean test(String name) {
        return name.startsWith("org.codehaus.groovy:")
      }
    })
    EXTRA_MERGE_RULES.put("jsch-agent.jar", new Predicate<String>() {
      @Override
      boolean test(String name) {
        return name.startsWith("jsch-agent")
      }
    })
    // see ClassPathUtil.getUtilClassPath
    EXTRA_MERGE_RULES.put("3rd-party-rt.jar", new Predicate<String>() {
      private static final Set<String> libsThatUsedInJps = Set.of(
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
        "commons-lang3",
        "kotlin-stdlib-jdk8"
      )

      @Override
      boolean test(String name) {
        return libsThatUsedInJps.contains(name) || name.startsWith("kotlinx-") || name == "kotlin-reflect"
      }
    })
  }

  static Collection<DistributionFileEntry> pack(Map<String, List<String>> actualModuleJars,
                                                Path outputDir,
                                                BaseLayout layout,
                                                ModuleOutputPatcher moduleOutputPatcher,
                                                boolean dryRun,
                                                BuildContext context) {
    Map<Path, JpsLibrary> copiedFiles = new HashMap<>()

    JarPackager packager = new JarPackager(context)

    for (ModuleLibraryData data in layout.includedModuleLibraries) {
      JpsLibrary library = context.findRequiredModule(data.moduleName).libraryCollection.libraries
        .find { LayoutBuilder.getLibraryName(it) == data.libraryName }
      if (library == null) {
        throw new IllegalArgumentException("Cannot find library ${data.libraryName} in '${data.moduleName}' module")
      }

      String fileName = libNameToMergedJarFileName(data.libraryName)
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
          targetFile = outputDir.resolve(relativePath).resolve(fileName)
        }
      }

      if (targetFile == null) {
        targetFile = outputDir.resolve(fileName)
      }

      packager.addLibrary(library, targetFile, getLibraryFiles(library, copiedFiles, true))
    }

    Map<String, List> extraLibSources = new HashMap<>()
    Map<JpsLibrary, List<Path>> libraryToMerge = packager.packLibraries(actualModuleJars, outputDir, layout, copiedFiles, extraLibSources)

    boolean isRootDir = context.paths.distAllDir == outputDir.parent
    if (isRootDir) {
      for (Map.Entry<String, Predicate<String>> rule : EXTRA_MERGE_RULES.entrySet() ) {
        packager.mergeLibsByPredicate(rule.key, libraryToMerge, outputDir, rule.value)
      }

      if (!libraryToMerge.isEmpty()) {
        packager.filesToSourceWithMappings(outputDir.resolve(BaseLayout.APP_JAR), libraryToMerge)
      }
    }
    else if (!libraryToMerge.isEmpty()) {
      String mainJarName = ((PluginLayout)layout).mainJarName
      assert actualModuleJars.containsKey(mainJarName)
      packager.filesToSourceWithMappings(outputDir.resolve(mainJarName), libraryToMerge)
    }

    // must be concurrent - buildJars executed in parallel
    Map<String, Integer> moduleNameToSize = new ConcurrentHashMap<>()

    for (Map.Entry<String, List<String>> entry in actualModuleJars.entrySet()) {
      String jarPath = entry.key
      Path jarFile = outputDir.resolve(entry.key)
      List<String> modules = entry.value
      JarDescriptor descriptor = packager.jarDescriptors.computeIfAbsent(jarFile, { new JarDescriptor(jarFile, new ArrayList()) })
      if (descriptor.includedModules.isEmpty()) {
        descriptor.includedModules = new ArrayList<>(modules)
      }
      else {
        descriptor.includedModules.addAll(modules)
      }
      List sourceList = descriptor.sources
      List extra = extraLibSources.get(entry.key)
      if (extra != null) {
        sourceList.addAll(extra)
      }

      packager.packModuleOutputAndUnpackedProjectLibraries(modules,
                                                           jarPath,
                                                           jarFile,
                                                           moduleOutputPatcher,
                                                           layout,
                                                           moduleNameToSize,
                                                           sourceList)
    }

    List<Triple<Path, String, List<?>>> entries = new ArrayList<>(packager.jarDescriptors.size())

    boolean isReorderingEnabled = !context.options.buildStepsToSkip.contains(BuildOptions.GENERATE_JAR_ORDER_STEP)
    for (JarDescriptor descriptor : packager.jarDescriptors.values()) {
      String pathInClassLog = ""
      if (isReorderingEnabled) {
        if (isRootDir) {
          pathInClassLog = outputDir.parent.relativize(descriptor.jarFile).toString().replace(File.separatorChar, (char)'/')
        }
        else if (outputDir.startsWith(context.paths.distAllDir)) {
          pathInClassLog = context.paths.distAllDir.relativize(descriptor.jarFile).toString().replace(File.separatorChar, (char)'/')
        }
        else if (outputDir.parent?.fileName?.toString() == "plugins") {
          pathInClassLog = outputDir.parent.parent.relativize(descriptor.jarFile).toString().replace(File.separatorChar, (char)'/')
        }
      }
      entries.add(new Triple(descriptor.jarFile, pathInClassLog, descriptor.sources))
    }
    packager.buildHelper.buildJars.accept(entries, dryRun)

    for (JarDescriptor item : packager.jarDescriptors.values()) {
      for (String moduleName : item.includedModules) {
        Integer size = moduleNameToSize.get(moduleName)
        if (size == null) {
          throw new IllegalStateException("Size is not set for " + moduleName + " (moduleNameToSize=${moduleNameToSize.toMapString()})")
        }
        packager.projectStructureMapping.add(new ModuleOutputEntry(item.jarFile, moduleName, size))
      }
    }

    return packager.projectStructureMapping
  }

  private void mergeLibsByPredicate(String jarName,
                                    Map<JpsLibrary, List<Path>> libraryToMerge,
                                    Path outputDir,
                                    Predicate<String> predicate) {
    Map<JpsLibrary, List<Path>> result = new LinkedHashMap<>()

    Iterator<Map.Entry<JpsLibrary, List<Path>>> iterator = libraryToMerge.entrySet().iterator()
    while (iterator.hasNext()) {
      Map.Entry<JpsLibrary, List<Path>> entry = iterator.next()
      if (predicate.test(entry.key.name)) {
        iterator.remove()
        result.put(entry.key, entry.value)
      }
    }

    if (result.isEmpty()) {
      return
    }

    filesToSourceWithMappings(outputDir.resolve(jarName), result)
  }

  private void filesToSourceWithMappings(Path uberJarFile, Map<JpsLibrary, List<Path>> libraryToMerge) {
    List sources = getJarDescriptorSources(uberJarFile)
    for (Map.Entry<JpsLibrary, List<Path>> entry : libraryToMerge.entrySet()) {
      filesToSourceWithMapping(sources, entry.value, entry.key, uberJarFile)
    }
  }

  static Path getSearchableOptionsDir(BuildContext buildContext) {
    return buildContext.paths.tempDir.resolve("searchableOptionsResult")
  }

  private void packModuleOutputAndUnpackedProjectLibraries(Collection<String> modules,
                                                           String jarPath,
                                                           Path jarFile,
                                                           ModuleOutputPatcher moduleOutputPatcher,
                                                           BaseLayout layout,
                                                           Map<String, Integer> moduleNameToSize,
                                                           List sourceList) {
    MethodHandle addModuleSources = buildHelper.addModuleSources
    Path searchableOptionsDir = getSearchableOptionsDir(context)

    Span.current().addEvent("include module outputs", Attributes.of(AttributeKey.stringArrayKey("modules"), List.copyOf(modules)))
    for (String moduleName in modules) {
      addModuleSources.invokeWithArguments(moduleName,
                                           moduleNameToSize,
                                           context.getModuleOutputDir(context.findRequiredModule(moduleName)),
                                           moduleOutputPatcher.getPatchedDir(moduleName),
                                           moduleOutputPatcher.getPatchedContent(moduleName),
                                           searchableOptionsDir,
                                           layout.moduleExcludes.get(moduleName),
                                           sourceList)
    }

    BiFunction<Path, IntConsumer, ?> createZipSource = buildHelper.createZipSource
    for (String libraryName in layout.projectLibrariesToUnpack.get(jarPath)) {
      JpsLibrary library = context.project.libraryCollection.findLibrary(libraryName)
      if (library == null) {
        context.messages.error("Project library '$libraryName' from $jarPath should be unpacked but it isn't found")
      }

      for (File ioFile : library.getFiles(JpsOrderRootType.COMPILED)) {
        Path file = ioFile.toPath()
        sourceList.add(createZipSource.apply(file, new IntConsumer() {
          @Override
          void accept(int size) {
            ProjectLibraryData libraryData = new ProjectLibraryData(library.name, "", PackMode.MERGED, "explicitUnpack")
            projectStructureMapping.add(new ProjectLibraryEntry(jarFile, libraryData, file, size))
          }
        }))
      }
    }
  }

  @CompileStatic
  @EqualsAndHashCode
  @ToString
  static final class JarDescriptor {
    final Path jarFile
    final List sources
    Collection<String> includedModules = Collections.<String>emptyList()

    JarDescriptor(Path jarFile, List sources) {
      this.jarFile = jarFile
      this.sources = sources
    }
  }

  private Map<JpsLibrary, List<Path>> packLibraries(Map<String, List<String>> jarToModuleNames,
                                                    Path outputDir,
                                                    BaseLayout layout,
                                                    Map<Path, JpsLibrary> copiedFiles,
                                                    Map<String, List> extraLibSources) {
    Map<JpsLibrary, List<Path>> toMerge = new LinkedHashMap<JpsLibrary, List<Path>>()
    Predicate<String> isLibraryMergeable = buildHelper.isLibraryMergeable

    Iterator<ProjectLibraryData> projectLibIterator = layout.includedProjectLibraries.isEmpty()
      ? Collections.<ProjectLibraryData> emptyIterator()
      : layout.includedProjectLibraries.stream().sorted(new Comparator<ProjectLibraryData>() {
      @SuppressWarnings("ChangeToOperator")
      @Override
      int compare(ProjectLibraryData o1, ProjectLibraryData o2) {
        int r = (o1.outPath ?: "").compareTo(o2.outPath ?: "")
        return r == 0 ? o1.libraryName.compareTo(o2.libraryName) : r
      }
    }).iterator()
    while (projectLibIterator.hasNext()) {
      ProjectLibraryData libraryData = projectLibIterator.next()
      JpsLibrary library = context.project.libraryCollection.findLibrary(libraryData.libraryName)
      if (library == null) {
        throw new IllegalArgumentException("Cannot find library ${libraryData.libraryName} in the project")
      }

      libToMetadata.put(library, libraryData)

      String libName = library.name
      List<Path> files = getLibraryFiles(library, copiedFiles, false)

      PackMode packMode = libraryData.packMode
      if (packMode == PackMode.MERGED && !EXTRA_MERGE_RULES.values().any { it.test(libName) } &&
          !isLibraryMergeable.test(libName)) {
        packMode = PackMode.STANDALONE_MERGED
      }

      String outPath = libraryData.outPath
      if (packMode == PackMode.MERGED && outPath == null) {
        toMerge.put(library, files)
      }
      else {
        Path libOutputDir = outputDir
        if (outPath != null) {
          if (outPath.endsWith(".jar")) {
            addLibrary(library, outputDir.resolve(outPath), files)
            continue
          }
          else {
            libOutputDir = outputDir.resolve(outPath)
          }
        }

        if (packMode == PackMode.STANDALONE_MERGED) {
          addLibrary(library, libOutputDir.resolve(libNameToMergedJarFileName(libName)), files)
        }
        else {
          for (Path file : files) {
            String fileName = file.fileName.toString()
            if (packMode == PackMode.STANDALONE_SEPARATE_WITHOUT_VERSION_NAME) {
              fileName = removeVersionFromJar(fileName)
            }
            addLibrary(library, libOutputDir.resolve(fileName), List.of(file))
          }
        }
      }
    }

    for (Map.Entry<String, List<String>> entry : jarToModuleNames.entrySet()) {
      String targetFilename = entry.key
      if (targetFilename.contains("/")) {
        continue
      }

      for (String moduleName : entry.value) {
        if (layout.modulesWithExcludedModuleLibraries.contains(moduleName)) {
          continue
        }

        Collection<String> excluded = layout.excludedModuleLibraries.get(moduleName)
        for (JpsDependencyElement element : context.findRequiredModule(moduleName).dependenciesList.dependencies) {
          if (!(element instanceof JpsLibraryDependency)) {
            continue
          }

          packModuleLibs(moduleName, targetFilename, (JpsLibraryDependency)element, excluded, layout, outputDir, copiedFiles,
                         extraLibSources)
        }
      }
    }

    return toMerge
  }

  private void packModuleLibs(String moduleName,
                              String targetFilename,
                              JpsLibraryDependency libraryDependency,
                              Collection<String> excluded,
                              BaseLayout layout,
                              Path outputDir,
                              Map<Path, JpsLibrary> copiedFiles,
                              Map<String, List> extraLibSources) {
    JpsCompositeElement parent = libraryDependency.libraryReference?.parentReference?.resolve()
    if (!(parent instanceof JpsModule)) {
      return
    }

    if (!(JpsJavaExtensionService.instance.getDependencyExtension(libraryDependency)?.scope
            ?.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME) ?: false)) {
      return
    }

    JpsLibrary library = libraryDependency.library
    String libraryName = LayoutBuilder.getLibraryName(library)
    if (!excluded.contains(libraryName) &&
        !layout.includedModuleLibraries.any { it.libraryName == libraryName }) {
      String libName = library.name
      List<Path> files = getLibraryFiles(library, copiedFiles, true)

      if (libName == "async-profiler-windows") {
        // custom name, removeVersionFromJar doesn't support strings like `2.1-ea-4`
        addLibrary(library, outputDir.resolve("async-profiler-windows.jar"), files)
        return
      }

      for (int i = files.size() - 1; i >= 0; i--) {
        Path file = files.get(i)
        String fileName = file.fileName.toString()
        //noinspection SpellCheckingInspection
        if (fileName.endsWith("-rt.jar") || fileName.contains("-agent") || fileName == "yjp-controller-api-redist.jar") {
          files.remove(i)
          addLibrary(library, outputDir.resolve(removeVersionFromJar(fileName)), List.of(file))
        }
      }
      if (!files.isEmpty()) {
        BiFunction<Path, IntConsumer, ?> createZipSource = buildHelper.createZipSource
        List sources = extraLibSources.computeIfAbsent(targetFilename, new Function<String, List>() {
          @Override
          List apply(String s) {
            return new ArrayList()
          }
        })

        Path targetFile = outputDir.resolve(targetFilename)
        for (Path file : files) {
          sources.add(createZipSource.apply(file, new IntConsumer() {
            @Override
            void accept(int size) {
              projectStructureMapping.add(new ModuleLibraryFileEntry(targetFile, moduleName, file, size))
            }
          }))
        }
      }
    }
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
    List<String> urls = library.getRootUrls(JpsOrderRootType.COMPILED)
    List<Path> result = new ArrayList<Path>(urls.size())
    String libName = library.name
    for (String url : urls) {
      if (JpsPathUtil.isJrtUrl(url)) {
        continue
      }

      Path file = Path.of(JpsPathUtil.urlToPath(url))
      JpsLibrary alreadyCopiedFor = copiedFiles.putIfAbsent(file, library)
      if (alreadyCopiedFor != null) {
        // check name - we allow to have same named module level library name
        if (isModuleLevel && alreadyCopiedFor.name == libName) {
          continue
        }
        throw new IllegalStateException("File $file from $libName is already provided by ${alreadyCopiedFor.name} library")
      }

      result.add(file)
    }
    return result
  }

  private static String libNameToMergedJarFileName(String libName) {
    return FileUtil.sanitizeFileName(libName.toLowerCase(), false) + ".jar"
  }

  private void filesToSourceWithMapping(List to, List<Path> files, JpsLibrary library, Path targetFile) {
    JpsElementReference<? extends JpsCompositeElement> parentReference = library.createReference().getParentReference()
    boolean isModuleLibrary = parentReference instanceof JpsModuleReference
    BiFunction<Path, IntConsumer, ?> createZipSource = buildHelper.createZipSource
    for (Path file : files) {
      IntConsumer consumer = createLibSizeConsumer(file, isModuleLibrary, projectStructureMapping, targetFile,
                                                   library, isModuleLibrary ? (JpsModuleReference)parentReference : null)
      to.add(createZipSource.apply(file, consumer))
    }
  }

  private IntConsumer createLibSizeConsumer(Path file,
                                            boolean isModuleLibrary,
                                            Collection<DistributionFileEntry> projectStructureMapping,
                                            Path targetFile,
                                            JpsLibrary library,
                                            JpsModuleReference moduleReference) {
    return new IntConsumer() {
      @Override
      void accept(int size) {
        if (isModuleLibrary) {
          projectStructureMapping.add(new ModuleLibraryFileEntry(targetFile, moduleReference.moduleName, file, size))
        }
        else {
          projectStructureMapping.add(new ProjectLibraryEntry(targetFile, libToMetadata.get(library), file, size))
        }
      }
    }
  }

  private void addLibrary(JpsLibrary library, Path targetFile, List<Path> files) {
    filesToSourceWithMapping(getJarDescriptorSources(targetFile), files, library, targetFile)
  }

  private List getJarDescriptorSources(Path targetFile) {
    return jarDescriptors.computeIfAbsent(targetFile, { new JarDescriptor(targetFile, new ArrayList()) }).sources
  }
}