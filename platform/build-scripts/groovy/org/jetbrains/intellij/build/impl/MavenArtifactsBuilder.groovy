// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.codeStyle.NameUtil
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.Immutable
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsRepositoryLibraryType
import org.jetbrains.jps.model.module.JpsModule
/**
 * Generates Maven artifacts for IDE and plugin modules. Artifacts aren't generated for modules which depends on non-repository libraries.
 * @see org.jetbrains.intellij.build.ProductProperties#mavenArtifacts
 * @see org.jetbrains.intellij.build.BuildOptions#MAVEN_ARTIFACTS_STEP
 */
@CompileStatic
class MavenArtifactsBuilder {
  /** second component of module names which describes a common group rather than a specific framework and therefore should be excluded from artifactId */
  private static final Set<String> COMMON_GROUP_NAMES = ["platform", "vcs", "tools", "clouds"] as Set<String>

  /** temporary map which specifies which modules will be replaced by libraries so they won't need to be published */
  private static final Map<String, MavenCoordinates> MODULES_TO_BE_CONVERTED_TO_LIBRARIES = [
    "intellij.platform.annotations": new MavenCoordinates("org.jetbrains", "annotations", "16.0.1"),
    "intellij.platform.annotations.java5": new MavenCoordinates("org.jetbrains", "annotations", "16.0.1"),
    "intellij.platform.annotations.common": null
  ] as Map<String, MavenCoordinates>

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

  private MavenCoordinates generateMavenCoordinates(JpsModule module) {
    def names = module.name.split("\\.")
    if (names.size() < 2) {
      buildContext.messages.error("Cannot generate Maven artifacts: incorrect module name '$module.name'")
    }
    String groupId = "com.jetbrains.${names.take(2).join(".")}"
    def firstMeaningful = names.size() > 2 && COMMON_GROUP_NAMES.contains(names[1]) ? 2 : 1
    String artifactId = names.drop(firstMeaningful).collectMany {
      NameUtil.splitNameIntoWords(it).toList().collect {it.toLowerCase(Locale.US)}
    }.join("-")
    return new MavenCoordinates(groupId, artifactId, buildContext.buildNumber)
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
    if (MODULES_TO_BE_CONVERTED_TO_LIBRARIES.containsKey(module.name)) {
      def coordinates = MODULES_TO_BE_CONVERTED_TO_LIBRARIES[module.name]
      return coordinates != null ? new MavenArtifactData(coordinates, []) : null
    }
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
    JpsJavaExtensionService.dependencies(module).productionOnly().runtimeOnly().processModuleAndLibraries({ JpsModule dep ->
      if (computationInProgress.contains(dep)) {
        buildContext.messages.debug(" module '$module.name' recursively depends on itself so it cannot be published")
        mavenizable = false
        return
      }
      def depArtifact = generateMavenArtifactData(dep, results, nonMavenizableModules, computationInProgress)
      if (depArtifact == null) {
        buildContext.messages.debug(" module '$module.name' depends on non-mavenizable module '$dep.name' so it cannot be published")
        mavenizable = false
        return
      }
      dependencies << new MavenArtifactDependency(depArtifact.coordinates, true)
    }, { JpsLibrary library ->
      def repLibrary = library.asTyped(JpsRepositoryLibraryType.INSTANCE)
      if (repLibrary == null) {
        buildContext.messages.debug(" module '$module.name' depends on non-maven library ${LibraryLicensesListGenerator.getLibraryName(library)}")
        mavenizable = false
      }
      else {
        def libraryDescriptor = repLibrary.properties.data
        dependencies << new MavenArtifactDependency(new MavenCoordinates(libraryDescriptor.groupId, libraryDescriptor.artifactId, libraryDescriptor.version),
                                                    libraryDescriptor.includeTransitiveDependencies)
      }
    })
    computationInProgress.remove(module)
    if (!mavenizable) {
      nonMavenizableModules << module
      return null
    }
    def artifactData = new MavenArtifactData(generateMavenCoordinates(module), dependencies)
    results[module] = artifactData
    return artifactData
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
  }

  @Immutable
  private static class MavenCoordinates {
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
