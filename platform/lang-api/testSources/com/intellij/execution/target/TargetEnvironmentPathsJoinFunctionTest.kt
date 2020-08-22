// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class TargetEnvironmentPathsJoinFunctionTest(private val basePath: String,
                                             private val relativePath: String,
                                             private val fileSeparator: Char,
                                             private val expected: String) {
  @Test
  fun `test concatPaths`() {
    Assert.assertEquals(expected, joinTargetPaths(basePath, relativePath, fileSeparator))
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{index}: join(\"{0}\", \"{1}\") = \"{3}\" (file separator = ''{2}'')")
    fun data(): Collection<Array<Any>> = listOf(
      arrayOf("/", "foo", '/', "/foo"),
      arrayOf("/foo", "bar", '/', "/foo/bar"),
      arrayOf("/foo/", "bar", '/', "/foo/bar"),
      arrayOf("/foo", "bar/", '/', "/foo/bar"),
      arrayOf("/foo/", "bar/", '/', "/foo/bar"),

      arrayOf("/", ".", '/', "/"),
      arrayOf("/", "..", '/', "/"),

      arrayOf("C:\\", ".", '\\', "C:\\"),
      arrayOf("C:\\\\", ".", '\\', "C:\\"),
      arrayOf("C:\\", "..", '\\', "C:\\"),
      arrayOf("C:\\\\", "..", '\\', "C:\\"),
      arrayOf("C:\\", "foo", '\\', "C:\\foo"),
      arrayOf("C:\\", "foo\\", '\\', "C:\\foo"),

      arrayOf("C:\\", "Directory With Spaces\\", '\\', "C:\\Directory With Spaces"),
      arrayOf("C:\\Directory With Spaces", "File With Spaces.txt", '\\', "C:\\Directory With Spaces\\File With Spaces.txt"),
      arrayOf("C:\\Directory With Spaces", "Subdirectory With Spaces\\", '\\', "C:\\Directory With Spaces\\Subdirectory With Spaces")
    )
  }
}