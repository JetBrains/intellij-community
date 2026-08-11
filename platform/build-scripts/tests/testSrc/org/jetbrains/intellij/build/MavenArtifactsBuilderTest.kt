// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.BuildPaths.Companion.ULTIMATE_HOME
import org.jetbrains.intellij.build.impl.createBuildContext
import org.jetbrains.intellij.build.impl.maven.DependencyScope
import org.jetbrains.intellij.build.impl.maven.MavenArtifactDependency
import org.jetbrains.intellij.build.impl.maven.MavenArtifactsBuilder
import org.jetbrains.intellij.build.impl.maven.MavenCoordinates
import org.junit.Assert
import org.junit.Test

class MavenArtifactsBuilderTest {
  private val builder by lazy {
    runBlocking {
      MavenArtifactsBuilder(
        createBuildContext(
          projectHome = ULTIMATE_HOME,
          productProperties = IdeaCommunityProperties(COMMUNITY_ROOT.communityRoot),
          setupTracer = false,
        )
      )
    }
  }

  @Test
  fun `maven coordinates`() {
    checkCoordinates("intellij.xml.impl", "com.jetbrains.intellij.xml", "xml-impl")
    checkCoordinates("intellij.java.debugger", "com.jetbrains.intellij.java", "java-debugger")
    checkCoordinates("intellij.platform.util", "com.jetbrains.intellij.platform", "util")
    checkCoordinates("intellij.platform.testFramework.common", "com.jetbrains.intellij.platform", "test-framework-common")
    checkCoordinates("intellij.platform.testFramework.junit5", "com.jetbrains.intellij.platform", "test-framework-junit5")
    checkCoordinates("intellij.platform.testFramework.teamCity", "com.jetbrains.intellij.platform", "test-framework-team-city")
    checkCoordinates("intellij.platform.testFramework", "com.jetbrains.intellij.platform", "test-framework")
    checkCoordinates("intellij.java.compiler.antTasks", "com.jetbrains.intellij.java", "java-compiler-ant-tasks")
    checkCoordinates("intellij.platform.vcs.log", "com.jetbrains.intellij.platform", "vcs-log")
    checkCoordinates("intellij.spring", "com.jetbrains.intellij.spring", "spring")
    checkCoordinates("intellij.spring.boot", "com.jetbrains.intellij.spring", "spring-boot")
    checkCoordinates("intellij.junit.v5.rt", "com.jetbrains.intellij.junit", "junit-v5-rt")
  }

  private fun checkCoordinates(moduleName: String, expectedGroupId: String, expectedArtifactId: String) {
    val coordinates = builder.generateMavenCoordinates(moduleName, "snapshot")
    Assert.assertEquals("Incorrect groupId generated for $moduleName", expectedGroupId, coordinates.groupId)
    Assert.assertEquals("Incorrect artifactId generated for $moduleName", expectedArtifactId, coordinates.artifactId)
  }

  /**
   * The platform icons modules are published to Maven Central transitively as dependencies of the Jewel
   * `jewel-ui` artifact, and Sonatype requires `-javadoc.jar` for them too. Keep the local Maven Central
   * validation in sync so missing Javadocs fail before the real publication step.
   */
  @Test
  fun `icons platform dependencies require javadocs`() {
    val context = runBlocking {
      createBuildContext(
        projectHome = ULTIMATE_HOME,
        productProperties = IdeaCommunityProperties(COMMUNITY_ROOT.communityRoot),
        setupTracer = false,
      )
    }
    val isJavadocJarRequired = context.productProperties.mavenArtifacts.isJavadocJarRequired
    val validateForMavenCentralPublication = context.productProperties.mavenArtifacts.validateForMavenCentralPublication
    for (moduleName in listOf("intellij.platform.icons.api", "intellij.platform.icons.api.rendering", "intellij.platform.icons.impl")) {
      val module = context.outputProvider.findRequiredModule(moduleName)
      Assert.assertTrue("POM for $moduleName is not validated for Maven Central publication", validateForMavenCentralPublication(module))
      Assert.assertTrue("Javadocs are not required for $moduleName", isJavadocJarRequired(module))
    }
  }

  /**
   * The Icons API modules are part of Jewel's public API surface (e.g. `Icon(...)` and the `iconKey(...)` DSL),
   * so the published Jewel POMs must declare them as compile dependencies. Otherwise consumers of the standalone
   * artifacts hit `NoClassDefFoundError: com/intellij/platform/icons/IconManager` at `IntUiTheme` startup.
   * See JEWEL-1374.
   */
  @Test
  fun `jewel poms declare icons api dependencies`() {
    val context = runBlocking {
      createBuildContext(
        projectHome = ULTIMATE_HOME,
        productProperties = IdeaCommunityProperties(COMMUNITY_ROOT.communityRoot),
        setupTracer = false,
      )
    }
    val jewelUi = context.outputProvider.findRequiredModule("intellij.platform.jewel.ui")
    val iconsDependencies = listOf("icons-api", "icons-api-rendering", "icons-impl").map { artifactId ->
      MavenArtifactDependency(
        coordinates = MavenCoordinates("com.jetbrains.intellij.platform", artifactId, "SNAPSHOT"),
        includeTransitiveDeps = false,
        excludedDependencies = emptyList(),
        scope = null,
      )
    }

    val patched = context.productProperties.mavenArtifacts.patchDependencies(jewelUi, iconsDependencies)

    Assert.assertEquals(
      "The Icons API dependencies must be kept in the jewel-ui POM",
      iconsDependencies.map { it.coordinates.artifactId },
      patched.map { it.coordinates.artifactId },
    )
    for (dependency in patched) {
      Assert.assertEquals("${dependency.coordinates} must be a compile dependency", DependencyScope.COMPILE, dependency.scope)
      Assert.assertTrue("${dependency.coordinates} must include transitive dependencies", dependency.includeTransitiveDeps)
    }
  }
}
