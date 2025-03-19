// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.path

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class EelAbsolutePathTest {
  @TestFactory
  fun `toString returns the same path as parsed`(): List<DynamicTest> = buildList {
    val unixPaths = listOf(
      "/",
      "/foo",
      "/foo/bar",
      "/foo/bar/.",
      "/foo/bar/./baz",
      "/foo/bar/./baz/..",
    ).map { it to EelPath.OS.UNIX }

    val windowsPaths = listOf(
      """C:\""",  // Keep in mind that "C:" is not an absolute path, it actually points to a current directory.
      """C:\Program Files""",
      """C:\Program Files\Foo""",
      """C:\Program Files\Foo\..""",
      """C:\Program Files\Foo\..\bar""",
      """C:\Program Files\Foo\..\bar\.""",
      """C:\Program Files\Foo\..\bar\.\baz.exe""",
      """\\wsl${'$'}\Ubuntu-22.04""",
      """\\wsl${'$'}\Ubuntu-22.04\home""",
      """\\wsl${'$'}\Ubuntu-22.04\home\user""",
    ).map { it to EelPath.OS.WINDOWS }

    for ((rawPath, os) in (unixPaths + windowsPaths)) {
      add(dynamicTest(rawPath) {
        val eelPath = EelPath.parse(rawPath, DummyEelDescriptor(os))
        eelPath.toString() shouldBe rawPath
      })
    }
  }

  @TestFactory
  fun `os-dependent separators`(): List<DynamicTest> = buildList {
    val unixPath = EelPath.parse("/", DummyEelDescriptor(EelPath.OS.UNIX))
    val windowsPath = EelPath.parse("C:\\", DummyEelDescriptor(EelPath.OS.WINDOWS))
    val parts = listOf(Triple("a/b/c/d", listOf("a", "b", "c", "d"), listOf("a", "b", "c", "d")),
                       Triple("a\\b\\c\\d", listOf("a\\b\\c\\d"), listOf("a", "b", "c", "d")),
                       Triple("a\\b/c\\d", listOf("a\\b", "c\\d"), listOf("a", "b", "c", "d")))
    for ((resolvable, unixAnswer, windowsAnswer) in parts) {
      add(dynamicTest("unix: $resolvable") {
        unixPath.resolve(resolvable).parts shouldBe unixAnswer
      })
      add(dynamicTest("windows: $resolvable") {
        windowsPath.resolve(resolvable).parts shouldBe windowsAnswer
      })
    }
  }

  @Test
  fun endsWith() {
    val path = EelPath.parse("C:\\foo\\bar\\baz", DummyEelDescriptor(EelPath.OS.WINDOWS))
    path.endsWith(listOf("bar", "baz")) shouldBe true
    path.endsWith(listOf("bar", "baz", "qux")) shouldBe false
    path.endsWith(listOf("C:", "foo", "bar", "bax")) shouldBe false
  }

  class DummyEelDescriptor(override val operatingSystem: EelPath.OS) : EelDescriptor {
    override suspend fun upgrade(): EelApi {
      return Assertions.fail<Nothing>()
    }
  }
}