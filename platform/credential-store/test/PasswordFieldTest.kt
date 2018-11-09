// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore

import com.intellij.testFramework.assertions.Assertions.assertThat
import org.junit.Test
import javax.swing.JPasswordField

class PasswordFieldTest {
  @Test
  fun text() {
    assertThat(JPasswordField("").getTrimmedChars()).isEqualTo(null)
    assertThat(JPasswordField(" ").getTrimmedChars()).isEqualTo(null)
    assertThat(JPasswordField("  ").getTrimmedChars()).isEqualTo(null)
    assertThat(JPasswordField(" a ").getTrimmedChars()).isEqualTo(charArrayOf('a'))
    assertThat(JPasswordField("b").getTrimmedChars()).isEqualTo(charArrayOf('b'))
    assertThat(JPasswordField("b b").getTrimmedChars()).isEqualTo("b b".toCharArray())
    assertThat(JPasswordField(" foo").getTrimmedChars()).isEqualTo("foo".toCharArray())
    assertThat(JPasswordField("foo  ").getTrimmedChars()).isEqualTo("foo".toCharArray())
  }
}