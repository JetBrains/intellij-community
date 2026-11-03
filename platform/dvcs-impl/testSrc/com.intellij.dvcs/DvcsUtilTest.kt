// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs

import com.intellij.mock.MockApplication
import com.intellij.openapi.Disposable
import com.intellij.testFramework.junit5.TestDisposable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DvcsUtilTest {
  @BeforeEach
  fun setUp(@TestDisposable disposable: Disposable) {
    MockApplication.setUp(disposable)
  }

  @Test
  fun testJoinWithAnd() {
    assertThat(DvcsUtil.joinWithAnd(listOf(""), 3)).isEqualTo("")
    assertThat(DvcsUtil.joinWithAnd(listOf("1"), 3)).isEqualTo("1")
    assertThat(DvcsUtil.joinWithAnd(listOf("1", "2"), 3)).isEqualTo("1 and 2")
    assertThat(DvcsUtil.joinWithAnd(listOf("1", "2", "3"), 3)).isEqualTo("1, 2 and 3")
    assertThat(DvcsUtil.joinWithAnd(listOf("1", "2", "3", "4"), 3)).isEqualTo("1, 2 and 2 others")
  }
}
