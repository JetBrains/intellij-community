// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.server.impl

import com.intellij.util.PathUtil
import com.intellij.util.io.DirectoryContentBuilder
import com.intellij.util.io.directoryContent
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.Manifest
import kotlin.io.path.listDirectoryEntries

class BuildProcessClasspathManagerTest {
  @Test
  fun `no manifest`() {
    val jarsDir = directoryContent {
      zip("foo.jar") {}
      dir("bar") {}
    }.generateInTempDir()
    assertClasspath(jarsDir, "foo.jar", "bar")
  }

  @Test
  fun `multiple versions`() {
    val jarsDir = directoryContent {
      zip("foo1.jar") {
        manifest("foo", "1.1")
      }
      zip("foo2.jar") {
        manifest("foo", "1.2")
      }
      zip("bar.jar") {
        manifest("bar", "1.3")
      }
      zip("foo.jar") {
        manifest("foo", null)
      }
    }.generateInTempDir()
    assertClasspath(jarsDir, "foo2.jar", "bar.jar", "foo.jar")
  }

  @Test
  fun `complex version text`() {
    val jarsDir = directoryContent {
      zip("kotlin-stdlib-jdk7-1.4.21.jar") {
        manifest("kotlin-stdlib-jdk7", "1.4.21-release-351 (1.4.21)")
      }
      zip("kotlin-stdlib-jdk7-1.3.72.jar") {
        manifest("kotlin-stdlib-jdk7", "1.3.72-release-468 (1.3.72)")
      }
    }.generateInTempDir()
    assertClasspath(jarsDir, "kotlin-stdlib-jdk7-1.4.21.jar")
  }

  private fun assertClasspath(jarsDir: Path, vararg names: String) {
    val classpath = jarsDir.listDirectoryEntries().map { it.toString() }
    val filteredNames = BuildProcessClasspathManager.filterOutOlderVersionsForTests(classpath).map { PathUtil.getFileName(it) }
    assertThat(filteredNames).containsExactlyInAnyOrder(*names)
  }

  private fun DirectoryContentBuilder.manifest(title: String?, version: String?) {
    dir("META-INF") {
      file("MANIFEST.MF", Manifest().run {
        mainAttributes.putValue(Attributes.Name.MANIFEST_VERSION.toString(), "1.0")
        title?.let { mainAttributes.putValue(Attributes.Name.IMPLEMENTATION_TITLE.toString(), it) }
        version?.let { mainAttributes.putValue(Attributes.Name.IMPLEMENTATION_VERSION.toString(), it) }
        val output = ByteArrayOutputStream()
        write(output)
        output.toByteArray()
      })
    }
  }
}