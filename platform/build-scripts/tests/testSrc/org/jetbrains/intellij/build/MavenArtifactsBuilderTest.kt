// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.impl.MavenArtifactsBuilder
import org.junit.Assert
import org.junit.Test

class MavenArtifactsBuilderTest {
  @Test
  fun `maven coordinates`() {
    checkCoordinates("intellij.xml", "com.jetbrains.intellij.xml", "xml")
    checkCoordinates("intellij.xml.impl", "com.jetbrains.intellij.xml", "xml-impl")
    checkCoordinates("intellij.java.debugger", "com.jetbrains.intellij.java", "java-debugger")
    checkCoordinates("intellij.platform.util", "com.jetbrains.intellij.platform", "util")
    checkCoordinates("intellij.platform.testFramework", "com.jetbrains.intellij.platform", "test-framework")
    checkCoordinates("intellij.java.compiler.antTasks", "com.jetbrains.intellij.java", "java-compiler-ant-tasks")
    checkCoordinates("intellij.platform.vcs.log", "com.jetbrains.intellij.platform", "vcs-log")
    checkCoordinates("intellij.spring", "com.jetbrains.intellij.spring", "spring")
    checkCoordinates("intellij.spring.boot", "com.jetbrains.intellij.spring", "spring-boot")
    checkCoordinates("intellij.junit.v5.rt", "com.jetbrains.intellij.junit", "junit-v5-rt")
  }

  private fun checkCoordinates(moduleName: String, expectedGroupId: String, expectedArtifactId: String) {
    val coordinates = MavenArtifactsBuilder.generateMavenCoordinates(moduleName, MockBuildMessages(), "snapshot")
    Assert.assertEquals("Incorrect groupId generated for $moduleName", expectedGroupId, coordinates.groupId)
    Assert.assertEquals("Incorrect artifactId generated for $moduleName", expectedArtifactId, coordinates.artifactId)
  }
}