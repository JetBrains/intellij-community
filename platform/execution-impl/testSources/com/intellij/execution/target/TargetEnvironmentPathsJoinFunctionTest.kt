// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class TargetEnvironmentPathsJoinFunctionTest(private val paths: List<String>,
                                             private val fileSeparator: Char,
                                             private val expected: String) {
  @Test
  fun `test concatPaths`() {
    Assert.assertEquals(expected, joinTargetPaths(*paths.toTypedArray(), fileSeparator = fileSeparator))
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{index}: join(\"{0}\", fileSeparator = \"{1}\") = \"{2}\" ")
    fun data(): Collection<Array<Any>> = listOf(
      arrayOf(listOf("/", "foo"), '/', "/foo"),
      arrayOf(listOf("/foo", "bar"), '/', "/foo/bar"),
      arrayOf(listOf("/foo/", "bar"), '/', "/foo/bar"),
      arrayOf(listOf("/foo", "bar/"), '/', "/foo/bar"),
      arrayOf(listOf("/foo/", "bar/"), '/', "/foo/bar"),

      arrayOf(listOf("/", "."), '/', "/"),

      arrayOf(listOf("C:\\", "."), '\\', "C:\\"),
      arrayOf(listOf("C:\\\\", "."), '\\', "C:\\"),
      arrayOf(listOf("C:\\", "foo"), '\\', "C:\\foo"),
      arrayOf(listOf("C:\\", "foo\\"), '\\', "C:\\foo"),

      arrayOf(listOf("C:/", "."), '\\', "C:\\"),
      arrayOf(listOf("C://", "."), '\\', "C:\\"),
      arrayOf(listOf("C:/", "foo"), '\\', "C:\\foo"),
      arrayOf(listOf("C:", "foo/"), '\\', "C:\\foo"),

      arrayOf(listOf("C:\\", "Directory With Spaces\\"), '\\', "C:\\Directory With Spaces"),
      arrayOf(listOf("C:\\Directory With Spaces", "File With Spaces.txt"), '\\', "C:\\Directory With Spaces\\File With Spaces.txt"),
      arrayOf(listOf("C:\\Directory With Spaces", "Subdirectory With Spaces\\"), '\\', "C:\\Directory With Spaces\\Subdirectory With Spaces"),

      arrayOf(listOf("C:/", "Directory With Spaces/"), '\\', "C:\\Directory With Spaces"),
      arrayOf(listOf("C:/Directory With Spaces", "File With Spaces.txt"), '\\', "C:\\Directory With Spaces\\File With Spaces.txt"),
      arrayOf(listOf("C:/Directory With Spaces", "Subdirectory With Spaces/"), '\\', "C:\\Directory With Spaces\\Subdirectory With Spaces"),

      arrayOf(listOf("/", "foo", "bar"), '/', "/foo/bar"),
      arrayOf(listOf("/foo", "bar", "baz"), '/', "/foo/bar/baz"),
      arrayOf(listOf("/foo/", "bar", "baz"), '/', "/foo/bar/baz"),
      arrayOf(listOf("/foo", "bar/", "baz"), '/', "/foo/bar/baz"),
      arrayOf(listOf("/foo/", "bar/", "baz"), '/', "/foo/bar/baz"),

      arrayOf(listOf("/", "foo", "bar/"), '/', "/foo/bar"),
      arrayOf(listOf("/foo", "bar", "baz/"), '/', "/foo/bar/baz"),
      arrayOf(listOf("/foo/", "bar", "baz/"), '/', "/foo/bar/baz"),
      arrayOf(listOf("/foo", "bar/", "baz/"), '/', "/foo/bar/baz"),
      arrayOf(listOf("/foo/", "bar/", "baz/"), '/', "/foo/bar/baz"),
    )
  }
}