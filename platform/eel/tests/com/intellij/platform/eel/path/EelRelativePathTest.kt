// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.path

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.assertions.withClue
import io.kotest.matchers.be
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot
import io.kotest.matchers.types.beInstanceOf
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

@Suppress("ClassName")
class EelRelativePathTest {
  @ParameterizedTest
  @CsvSource(textBlock = """
      , 
      .., ..
      abc, abc
      abc/def, abc/def
      abc/., abc
      abc/..,
      abc/../.., ..
      abc/def/../ghi, abc/ghi
      abc/def/.././ghi, abc/ghi
      abc/def/./../ghi, abc/ghi
      abc/./def/../ghi, abc/ghi
      ./abc/def/../ghi, abc/ghi """)
  fun normalize(source: String?, expected: String?) {
    val sourcePath = EelPath.Relative.parseE(source ?: "")
    val expectedPath = EelPath.Relative.parseE(expected ?: "")
    sourcePath.normalize() should be(expectedPath)
  }

  @Nested
  inner class getChild {
    @Test
    fun positive() {
      val empty = EelPath.Relative.buildE()
      empty.getChildE("a") should be(EelPath.Relative.buildE("a"))
      empty.getChildE("a").getChildE("bc") should be(EelPath.Relative.buildE("a", "bc"))
    }
  }

  @Nested
  inner class resolve {
    @Test
    fun `parent directory should persist`() {
      val path = EelPath.Relative.parseE("abc/..")
      val targetPath = EelPath.Relative.parseE("def")

      withClue("IjentPath.Relative.resolve must not normalize paths") {
        EelPath.Relative.parseE("abc/../def") should be(path.resolveE(targetPath))
      }
    }
  }

  @Nested
  inner class getName {
    @Test
    fun positive() {
      val raw = "a/b/c"
      val path = EelPath.Relative.parseE(raw)
      val expected = EelPath.Relative.parseE("b")
      expected should be(path.getName(1))
    }

    @Test
    fun `out of bound`() {
      val raw = "a/b/c"
      val path = EelPath.Relative.parseE(raw)

      shouldThrowAny {
        path.getName(10)
      } should beInstanceOf<IllegalArgumentException>()
    }
  }

  @Nested
  inner class getParent {
    @ParameterizedTest
    @ValueSource(strings = ["a/b", "a/b/c"])
    fun `is not null`(raw: String) {
      val path = EelPath.Relative.parseE(raw)
      path.parent shouldNot be(null)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "a"])
    fun `is null`(raw: String) {
      val path = EelPath.Relative.parseE(raw)
      path.parent should be(null)
    }
  }
}