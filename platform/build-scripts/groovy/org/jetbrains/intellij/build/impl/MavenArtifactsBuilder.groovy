// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.codeStyle.NameUtil
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.Immutable
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsRepositoryLibraryType
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
  private final BuildContext buildContext

  MavenArtifactsBuilder(BuildContext buildContext) {
    this.buildContext = buildContext
  }

  void generateMavenArtifacts(List<String> ideModuleNames) {
    def mavenArtifacts = buildContext.productProperties.mavenArtifacts
    Map<JpsModule, MavenArtifactData> modulesToPublish = generateMavenArtifactData((mavenArtifacts.forIdeModules ? ideModuleNames : [])
                                                                                     + mavenArtifacts.additionalModules)
    buildContext.messages.progress("Generating Maven artifacts for ${modulesToPublish.size()} modules")
    buildContext.messages.debug("Generate artifacts for the following modules:")
    modulesToPublish.each {module, data -> buildContext.messages.debug("  $module.name -> $data.coordinates")}
    layoutMavenArtifacts(modulesToPublish)
  }

  @SuppressWarnings("GrUnresolvedAccess")
  @CompileDynamic
  private void layoutMavenArtifacts(Map<JpsModule, MavenArtifactData> modulesToPublish) {
    def ant = buildContext.ant
    def publishSourcesFilter = buildContext.productProperties.mavenArtifacts.publishSourcesFilter
    def buildContext = this.buildContext
    Map<JpsModule, String> pomXmlFiles = [:]
    modulesToPublish.each { module, artifactData ->
      String filePath = "$buildContext.paths.temp/pom-files/${artifactData.coordinates.getDirectoryPath()}/${artifactData.coordinates.getFileName("", "pom")}"
      pomXmlFiles[module] = filePath
      generatePomXmlFile(filePath, artifactData)
    }
    new LayoutBuilder(buildContext, true).layout("$buildContext.paths.artifacts/maven-artifacts") {
      modulesToPublish.each { aModule, artifactData ->
        dir(artifactData.coordinates.directoryPath) {
          ant.fileset(file: pomXmlFiles[aModule])
          jar(artifactData.coordinates.getFileName("", "jar")) {
            module(aModule.name)
          }
          if (publishSourcesFilter.test(aModule, buildContext)) {
            zip(artifactData.coordinates.getFileName("sources", "jar")) {
              aModule.getSourceRoots(JavaSourceRootType.SOURCE).each { root ->
                ant.zipfileset(dir: root.file.absolutePath, prefix: root.properties.packagePrefix.replace('.', '/'))
              }
              aModule.getSourceRoots(JavaResourceRootType.RESOURCE).each { root ->
                ant.zipfileset(dir: root.file.absolutePath, prefix: root.properties.relativeOutputPath)
              }
            }
          }
        }
      }
    }
  }

  private static void generatePomXmlFile(String pomXmlPath, MavenArtifactData artifactData) {
    File file = new File(pomXmlPath)
    FileUtil.createParentDirs(file)
    file.text = """
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>${artifactData.coordinates.groupId}</groupId>
    <artifactId>${artifactData.coordinates.artifactId}</artifactId>
    <version>${artifactData.coordinates.version}</version>
    <dependencies>
${artifactData.dependencies.collect {"""
        <dependency>
             <groupId>$it.coordinates.groupId</groupId>
             <artifactId>$it.coordinates.artifactId</artifactId>
             <version>$it.coordinates.version</version>
${it.scope == DependencyScope.COMPILE ? "" : """
             <scope>runtime</scope>
"""}             
${it.includeTransitiveDeps ? "" : """
             <exclusions>
                 <exclusion>
                     <artifactId>*</artifactId>
                     <groupId>*</groupId>
                 </exclusion>
             </exclusions>
"""}             
        </dependency>
"""}.join("\n")}    
    </dependencies>    
</project>
""".readLines().findAll {!it.trim().isEmpty()}.join("\n")
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
    def words = NameUtil.splitNameIntoWords(s)
    def result = new ArrayList<String>()
    for (int i = 0; i < words.length; i++) {
      String next;
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
    def allPlatformModules = moduleNames.collect {
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

  private MavenArtifactData generateMavenArtifactData(JpsModule module, Map<JpsModule, MavenArtifactData> results, Set<JpsModule> nonMavenizableModules,
                                                      Set<JpsModule> computationInProgress) {
    if (results.containsKey(module)) return results[module]
    if (nonMavenizableModules.contains(module)) return null
    if (!module.name.startsWith("intellij.")) {
      buildContext.messages.debug("  module '$module.name' doesn't belong to IntelliJ project so it cannot be published")
      return null
    }
    def scrambleTool = buildContext.proprietaryBuildTools.scrambleTool
    if (scrambleTool != null && scrambleTool.namesOfModulesRequiredToBeScrambled.contains(module.name)) {
      buildContext.messages.debug("  module '$module.name' must be scrambled so it cannot be published")
      return null
    }

    boolean mavenizable = true
    computationInProgress << module
    List<MavenArtifactDependency> dependencies = []
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

      if (dependency instanceof JpsModuleDependency) {
        def depModule = (dependency as JpsModuleDependency).module
        if (computationInProgress.contains(depModule)) {
          buildContext.messages.debug(" module '$module.name' recursively depends on itself so it cannot be published")
          mavenizable = false
          return
        }
        def depArtifact = generateMavenArtifactData(depModule, results, nonMavenizableModules, computationInProgress)
        if (depArtifact == null) {
          buildContext.messages.debug(" module '$module.name' depends on non-mavenizable module '$depModule.name' so it cannot be published")
          mavenizable = false
          return
        }
        dependencies << new MavenArtifactDependency(depArtifact.coordinates, true, scope)
      }
      else if (dependency instanceof JpsLibraryDependency) {
        def library = (dependency as JpsLibraryDependency).library
        def repLibrary = library.asTyped(JpsRepositoryLibraryType.INSTANCE)
        if (repLibrary == null) {
          buildContext.messages.debug(" module '$module.name' depends on non-maven library ${LibraryLicensesListGenerator.getLibraryName(library)}")
          mavenizable = false
        }
        else {
          def libraryDescriptor = repLibrary.properties.data
          dependencies << new MavenArtifactDependency(new MavenCoordinates(libraryDescriptor.groupId, libraryDescriptor.artifactId, libraryDescriptor.version),
                                                      libraryDescriptor.includeTransitiveDependencies, scope)
        }
      }
    }
    computationInProgress.remove(module)
    if (!mavenizable) {
      nonMavenizableModules << module
      return null
    }
    def artifactData = new MavenArtifactData(generateMavenCoordinates(module.name, buildContext.messages, buildContext.buildNumber), dependencies)
    results[module] = artifactData
    return artifactData
  }

  @Immutable
  private static class MavenArtifactData {
    MavenCoordinates coordinates
    List<MavenArtifactDependency> dependencies
  }

  private enum DependencyScope { COMPILE, RUNTIME }

  @Immutable
  private static class MavenArtifactDependency {
    MavenCoordinates coordinates
    boolean includeTransitiveDeps
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
