// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.text.NameUtilCore
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.Immutable
import org.apache.maven.model.Dependency
import org.apache.maven.model.Exclusion
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Writer
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor
import org.jetbrains.jps.model.library.JpsRepositoryLibraryType
import org.jetbrains.jps.model.module.JpsDependencyElement
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency
/**
 * Generates Maven artifacts for IDE and plugin modules. Artifacts aren't generated for modules which depends on non-repository libraries.
 * @see org.jetbrains.intellij.build.ProductProperties#mavenArtifacts
 * @see org.jetbrains.intellij.build.BuildOptions#MAVEN_ARTIFACTS_STEP
 */
@CompileStatic
class MavenArtifactsBuilder {
  /** second component of module names which describes a common group rather than a specific framework and therefore should be excluded from artifactId */
  private static final Set<String> COMMON_GROUP_NAMES = ["platform", "vcs", "tools", "clouds"] as Set<String>
  protected final BuildContext buildContext

  MavenArtifactsBuilder(BuildContext buildContext) {
    this.buildContext = buildContext
  }

  void generateMavenArtifacts(List<String> namesOfModulesToPublish, String outputDir) {
    Map<JpsModule, MavenArtifactData> modulesToPublish = generateMavenArtifactData(namesOfModulesToPublish)
    buildContext.messages.progress("Generating Maven artifacts for ${modulesToPublish.size()} modules")
    buildContext.messages.debug("Generate artifacts for the following modules:")
    modulesToPublish.each {module, data -> buildContext.messages.debug("  $module.name -> $data.coordinates")}
    layoutMavenArtifacts(modulesToPublish, outputDir)
  }

  @SuppressWarnings("GrUnresolvedAccess")
  @CompileDynamic
  private void layoutMavenArtifacts(Map<JpsModule, MavenArtifactData> modulesToPublish, String outputDir) {
    def ant = buildContext.ant
    def publishSourcesFilter = buildContext.productProperties.mavenArtifacts.publishSourcesFilter
    def buildContext = this.buildContext
    Map<JpsModule, String> pomXmlFiles = [:]
    modulesToPublish.each { module, artifactData ->
      String filePath = "$buildContext.paths.temp/pom-files/${artifactData.coordinates.getDirectoryPath()}/${artifactData.coordinates.getFileName("", "pom")}"
      pomXmlFiles[module] = filePath
      generatePomXmlFile(filePath, artifactData)
    }
    new LayoutBuilder(buildContext).layout("$buildContext.paths.artifacts/$outputDir") {
      modulesToPublish.each { aModule, artifactData ->
        dir(artifactData.coordinates.directoryPath) {
          ant.fileset(file: pomXmlFiles[aModule])
          def javaSourceRoots = aModule.getSourceRoots(JavaSourceRootType.SOURCE).toList()
          def javaResourceRoots = aModule.getSourceRoots(JavaResourceRootType.RESOURCE).toList()
          def hasSources = !(javaSourceRoots.isEmpty() && javaResourceRoots.isEmpty())
          ant.jar(name: artifactData.coordinates.getFileName("", "jar"), duplicate: "fail",
                  filesetmanifest: "merge", whenmanifestonly: "create") {
            if (hasSources) {
              module(aModule.name)
            }
          }
          if (publishSourcesFilter.test(aModule, buildContext) && hasSources) {
            zip(artifactData.coordinates.getFileName("sources", "jar")) {
              javaSourceRoots.each { root ->
                ant.zipfileset(dir: root.file.absolutePath, prefix: root.properties.packagePrefix.replace('.', '/'))
              }
              javaResourceRoots.each { root ->
                ant.zipfileset(dir: root.file.absolutePath, prefix: root.properties.relativeOutputPath)
              }
            }
          }
        }
      }
    }
  }

  private static void generatePomXmlFile(String pomXmlPath, MavenArtifactData artifactData) {
    Model pomModel = new Model(
      modelVersion: '4.0.0',
      groupId: artifactData.coordinates.groupId,
      artifactId: artifactData.coordinates.artifactId,
      version: artifactData.coordinates.version
    )
    artifactData.dependencies.each { dep ->
      pomModel.addDependency(createDependencyTag(dep))
    }

    File file = new File(pomXmlPath)
    FileUtil.createParentDirs(file)
    file.withWriter {
      new MavenXpp3Writer().write(it, pomModel)
    }
  }

  private static Dependency createDependencyTag(MavenArtifactDependency dep) {
    def dependency = new Dependency(
      groupId: dep.coordinates.groupId,
      artifactId: dep.coordinates.artifactId,
      version: dep.coordinates.version
    )
    if (dep.scope == DependencyScope.RUNTIME) {
      dependency.scope = "runtime"
    }
    if (dep.includeTransitiveDeps) {
      dep.excludedDependencies.each {
        dependency.addExclusion(new Exclusion(groupId: StringUtil.substringBefore(it, ":"), artifactId: StringUtil.substringAfter(it, ":")))
      }
    }
    else {
      dependency.addExclusion(new Exclusion(groupId: "*", artifactId: "*"))
    }
    dependency
  }

  static MavenCoordinates generateMavenCoordinates(String moduleName, BuildMessages messages, String version) {
    def names = moduleName.split("\\.")
    if (names.size() < 2) {
      messages.error("Cannot generate Maven artifacts: incorrect module name '${moduleName}'")
    }
    String groupId = "com.jetbrains.${names.take(2).join(".")}"
    def firstMeaningful = names.size() > 2 && COMMON_GROUP_NAMES.contains(names[1]) ? 2 : 1
    String artifactId = names.drop(firstMeaningful).collectMany {
      splitByCamelHumpsMergingNumbers(it).collect {it.toLowerCase(Locale.US)}
    }.join("-")
    return new MavenCoordinates(groupId, artifactId, version)
  }

  private static List<String> splitByCamelHumpsMergingNumbers(String s) {
    def words = NameUtilCore.splitNameIntoWords(s)

    def result = new ArrayList<String>()
    for (int i = 0; i < words.length; i++) {
      String next
      if (i < words.length - 1 && Character.isDigit(words[i + 1].charAt(0))) {
        next = words[i] + words[i+1]
        i++
      }
      else {
        next = words[i]
      }
      result << next
    }
    return result
  }

  private Map<JpsModule, MavenArtifactData> generateMavenArtifactData(List<String> moduleNames) {
    buildContext.messages.debug("Collecting platform modules which can be published as Maven artifacts")
    List<JpsModule> allPlatformModules = moduleNames.collect {
      buildContext.findRequiredModule(it)
    }

    def results = new HashMap<JpsModule, MavenArtifactData>()
    def nonMavenizableModulesSet = new HashSet<JpsModule>()
    def computationInProgressSet = new HashSet<JpsModule>()
    allPlatformModules.each {
      generateMavenArtifactData(it, results, nonMavenizableModulesSet, computationInProgressSet)
    }
    return results
  }

  enum DependencyScope { COMPILE, RUNTIME }

  static Map<JpsDependencyElement, DependencyScope> scopedDependencies(JpsModule module) {
    Map<JpsDependencyElement, DependencyScope> result = [:]
    module.dependenciesList.dependencies.each { dependency ->
      def extension = JpsJavaExtensionService.getInstance().getDependencyExtension(dependency)
      if (extension == null) return
      DependencyScope scope
      switch (extension.scope) {
        case JpsJavaDependencyScope.COMPILE:
          //if a dependency isn't exported transitive dependencies will include it into runtime classpath only
          scope = extension.isExported() ? DependencyScope.COMPILE : DependencyScope.RUNTIME
          break
        case JpsJavaDependencyScope.RUNTIME:
          scope = DependencyScope.RUNTIME
          break
        case JpsJavaDependencyScope.PROVIDED:
          //'provided' scope is used only for compilation and it shouldn't be exported
          return
        case JpsJavaDependencyScope.TEST:
          return
        default:
          return
      }
      result[dependency] = scope
    }
    return result
  }

  private MavenArtifactData generateMavenArtifactData(JpsModule module, Map<JpsModule, MavenArtifactData> results, Set<JpsModule> nonMavenizableModules,
                                                      Set<JpsModule> computationInProgress) {
    if (results.containsKey(module)) return results[module]
    if (nonMavenizableModules.contains(module)) return null
    if (shouldSkipModule(module.name, false)) {
      buildContext.messages.warning("  module '$module.name' doesn't belong to IntelliJ project so it cannot be published")
      return null
    }
    def scrambleTool = buildContext.proprietaryBuildTools.scrambleTool
    if (scrambleTool != null && scrambleTool.namesOfModulesRequiredToBeScrambled.contains(module.name)) {
      buildContext.messages.warning("  module '$module.name' must be scrambled so it cannot be published")
      return null
    }

    boolean mavenizable = true
    computationInProgress << module
    List<MavenArtifactDependency> dependencies = []
    scopedDependencies(module).each { dependency, scope ->
      if (dependency instanceof JpsModuleDependency) {
        def depModule = (dependency as JpsModuleDependency).module
        if (shouldSkipModule(depModule.name, true)) return
        if (computationInProgress.contains(depModule)) {
          /*
           It's forbidden to have compile-time circular dependencies in IntelliJ project, but there are some cycles with runtime scope
            (e.g. intellij.platform.ide.impl depends on (runtime scope) intellij.platform.configurationStore.impl which depends on intellij.platform.ide.impl).
           It's convenient to have such dependencies to allow running tests in classpath of their modules, so we can just ignore them while
           generating pom.xml files.
          */
          buildContext.messages.warning(" module '$module.name': skip recursive dependency on '$depModule.name'")
        }
        else {
          MavenArtifactData depArtifact = generateMavenArtifactData(depModule, results, nonMavenizableModules, computationInProgress)
          if (depArtifact == null) {
            buildContext.messages.warning(" module '$module.name' depends on non-mavenizable module '$depModule.name' so it cannot be published")
            mavenizable = false
            return
          }
          dependencies << new MavenArtifactDependency(depArtifact.coordinates, true, [], scope)
        }
      }
      else if (dependency instanceof JpsLibraryDependency) {
        def library = (dependency as JpsLibraryDependency).library
        def typed = library.asTyped(JpsRepositoryLibraryType.INSTANCE)
        if (typed != null) {
          dependencies << createArtifactDependencyByLibrary(typed.properties.data, scope)
        }
        else if (!isOptionalDependency(library)) {
          buildContext.messages.warning(" module '$module.name' depends on non-maven library ${LibraryLicensesListGenerator.getLibraryName(library)}")
          mavenizable = false
        }
      }
    }
    computationInProgress.remove(module)
    if (!mavenizable) {
      nonMavenizableModules << module
      return null
    }
    MavenArtifactData artifactData = new MavenArtifactData(generateMavenCoordinatesForModule(module), dependencies)
    results[module] = artifactData
    return artifactData
  }

  protected boolean shouldSkipModule(String moduleName, boolean moduleIsDependency) {
    if (moduleIsDependency) return false
    return !moduleName.startsWith("intellij.")
  }

  protected MavenCoordinates generateMavenCoordinatesForModule(JpsModule module) {
    return generateMavenCoordinates(module.name, buildContext.messages, buildContext.buildNumber)
  }

  static boolean isOptionalDependency(JpsLibrary library) {
    //todo: this is a temporary workaround until these libraries are published to Maven repository;
    // it's unlikely that code which depend on these libraries will be used when running tests so skipping these dependencies shouldn't cause real problems.
    //  'microba' contains UI elements which are used in few places (IDEA-200834),
    //  'precompiled_jshell-frontend' is used by "JShell Console" action only (IDEA-222381).
    library.name == "microba" || library.name == "jshell-frontend"
  }

  private static MavenArtifactDependency createArtifactDependencyByLibrary(JpsMavenRepositoryLibraryDescriptor descriptor, DependencyScope scope) {
    new MavenArtifactDependency(new MavenCoordinates(descriptor.groupId, descriptor.artifactId, descriptor.version),
                                descriptor.includeTransitiveDependencies, descriptor.excludedDependencies, scope)
  }

  static Dependency createDependencyTagByLibrary(JpsMavenRepositoryLibraryDescriptor descriptor) {
    createDependencyTag(createArtifactDependencyByLibrary(descriptor, DependencyScope.COMPILE))
  }

  @Immutable
  private static class MavenArtifactData {
    MavenCoordinates coordinates
    List<MavenArtifactDependency> dependencies
  }

  @Immutable
  private static class MavenArtifactDependency {
    MavenCoordinates coordinates
    boolean includeTransitiveDeps
    List<String> excludedDependencies
    DependencyScope scope
  }

  @Immutable
  static class MavenCoordinates {
    String groupId
    String artifactId
    String version

    @Override
    String toString() {
      "$groupId:$artifactId:$version"
    }

    String getDirectoryPath() {
      "${groupId.replace('.', '/')}/$artifactId/$version"
    }

    String getFileName(String classifier, String packaging) {
      "$artifactId-$version${classifier.isEmpty() ? "" : "-$classifier"}.$packaging"
    }
  }
}
