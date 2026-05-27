// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

internal class LibraryFileCopyTrackerTest {
  @Test
  fun `tracks library copies by final target file`() {
    val tracker = LibraryFileCopyTracker()
    val file = Path.of("/maven/io/mockk/mockk-agent/1.14.5/mockk-agent-1.14.5.jar")
    val moduleJar = Path.of("/dist/plugins/python-junit5Tests-plugin/lib/modules/intellij.python.pyproject.tests.jar")
    val separateJar = Path.of("/dist/plugins/python-junit5Tests-plugin/lib/mockk-agent.jar")
    val anotherSeparateJar = Path.of("/dist/plugins/another-plugin/lib/mockk-agent.jar")

    assertThat(tracker.markLibraryFileForCopy(file = file, targetFile = moduleJar)).isTrue()
    assertThat(tracker.markLibraryFileForCopy(file = file, targetFile = separateJar)).isTrue()
    assertThat(tracker.markLibraryFileForCopy(file = file, targetFile = separateJar)).isFalse()
    assertThat(tracker.markLibraryFileForCopy(file = file, targetFile = anotherSeparateJar)).isTrue()
  }

  @Test
  fun `normalizes library jar file names`() {
    assertThat(removeVersionFromJar("mockk-agent-1.14.5.jar")).isEqualTo("mockk-agent.jar")
    assertThat(removeVersionFromJar("maven-resolver-provider.jar")).isEqualTo("maven-resolver-provider.jar")
    assertThat(nameToJarFileName("io.mockk agent")).isEqualTo("io.mockk-agent.jar")
  }

  @Test
  fun `detects jars packed separately`() {
    assertThat(isSeparateLibraryJar("mockk-agent-1.14.5.jar")).isTrue()
    assertThat(isSeparateLibraryJar("byte-buddy-1.17.7.jar")).isTrue()
    assertThat(isSeparateLibraryJar("objenesis-3.4.jar")).isTrue()
    assertThat(isSeparateLibraryJar("kotlin-reflect-rt.jar")).isTrue()
    assertThat(isSeparateLibraryJar("maven-resolver-provider.jar")).isTrue()
    assertThat(isSeparateLibraryJar("ideformer-agent.jar")).isFalse()
    assertThat(isSeparateLibraryJar("code-agents-agent.jar")).isFalse()
    assertThat(isSeparateLibraryJar("kotlin-stdlib.jar")).isFalse()
  }
}
