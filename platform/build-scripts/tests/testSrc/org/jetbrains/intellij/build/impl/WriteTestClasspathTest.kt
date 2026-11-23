// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class WriteTestClasspathTest {
  @Test
  fun `each test process of a module gets its own file`(@TempDir root: Path) {
    val directory = root.resolve("test-classpath")

    val first = writeTestClasspath(directory, "intellij.platform.tests", listOf("Tests classpath: [a.jar]"))
    val second = writeTestClasspath(directory, "intellij.platform.tests", listOf("Bootstrap classpath: [b.jar]", "Tests classpath: [c.jar]"))

    assertThat(first).isNotEqualTo(second)
    assertThat(first.parent).isEqualTo(directory)
    assertThat(first.fileName.toString()).startsWith("intellij.platform.tests-").endsWith(".txt")
    assertThat(Files.readAllLines(first)).containsExactly("Tests classpath: [a.jar]")
    assertThat(Files.readAllLines(second)).containsExactly("Bootstrap classpath: [b.jar]", "Tests classpath: [c.jar]")
  }

  @Test
  fun `classpath files use default permissions`(@TempDir root: Path) {
    assumeTrue(Files.getFileStore(root).supportsFileAttributeView("posix"))

    val referenceFile = Files.createFile(root.resolve("reference"))
    val classpathFile = writeTestClasspath(root, "intellij.platform.tests", emptyList())

    assertThat(Files.getPosixFilePermissions(classpathFile)).isEqualTo(Files.getPosixFilePermissions(referenceFile))
  }
}
