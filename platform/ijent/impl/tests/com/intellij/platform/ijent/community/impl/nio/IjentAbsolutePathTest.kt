// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import com.intellij.platform.ijent.fs.IjentPath
import com.intellij.platform.ijent.fs.IjentPathResult
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory

class IjentAbsolutePathTest {
  @TestFactory
  fun `toString returns the same path as parsed`(): List<DynamicTest> = buildList {
    val unixPaths = listOf(
      "/",
      "/foo",
      "/foo/bar",
      "/foo/bar/.",
      "/foo/bar/./baz",
      "/foo/bar/./baz/..",
    )

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
    )

    for (rawPath in (unixPaths + windowsPaths)) {
      add(dynamicTest(rawPath) {
        val ijentPath = IjentPath.Absolute
          .parse(rawPath, null)
          .shouldBeTypeOf<IjentPathResult.Ok<IjentPath.Absolute>>()
          .path
        ijentPath.toString() shouldBe rawPath
      })
    }
  }
}