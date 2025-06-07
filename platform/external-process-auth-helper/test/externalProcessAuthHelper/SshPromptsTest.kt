// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalProcessAuthHelper

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.regex.Matcher
import java.util.regex.Pattern

class SshPromptsTest {
  @Test
  @Suppress("NonAsciiCharacters")
  fun testPassphraseRegex() {
    passphraseRegexShouldMatch("Enter passphrase for key 'С:\\test\\dir\\.ssh\\id.rsa':", "С:\\test\\dir\\.ssh\\id.rsa")
    passphraseRegexShouldMatch("Enter passphrase for 'С:\\test\\dir\\.ssh\\id.rsa':", "С:\\test\\dir\\.ssh\\id.rsa")
    passphraseRegexShouldMatch("Enter passphrase for С:\\test\\dir\\.ssh\\id.rsa:", "С:\\test\\dir\\.ssh\\id.rsa")
    passphraseRegexShouldMatch("Enter passphrase for key С:\\test\\dir\\.ssh\\id.rsa:", "С:\\test\\dir\\.ssh\\id.rsa")
    passphraseRegexShouldMatch("Enter passphrase for key '/home/test/rsa':", "/home/test/rsa")
    passphraseRegexShouldMatch("Enter passphrase for key /home/test/rsa:", "/home/test/rsa")
    passphraseRegexShouldMatch("Enter passphrase for '/home/test/rsa':", "/home/test/rsa")
  }

  @Test
  fun testPasswordRegex() {
    regexShouldMatch(SshPrompts.PASSWORD_PROMPT, "User1's password:", "User1") { SshPrompts.extractUsername(it) }
  }

  @Test
  fun testStripConfirmationOptions() {
    assertEquals("Text. ${SshPrompts.CONFIRM_CONNECTION_PROMPT}?",
                 SshPrompts.stripConfirmConnectionOptions("Text. Are you sure you want to continue connecting (yes/no)?"))
    assertEquals(SshPrompts.CONFIRM_CONNECTION_PROMPT + "?",
                 SshPrompts.stripConfirmConnectionOptions("${SshPrompts.CONFIRM_CONNECTION_PROMPT} (yes/no)?"))
    assertEquals(SshPrompts.CONFIRM_CONNECTION_PROMPT + "?",
                 SshPrompts.stripConfirmConnectionOptions("${SshPrompts.CONFIRM_CONNECTION_PROMPT} (yes/no/[fingerprint])?"))
  }

  private fun passphraseRegexShouldMatch(input: String, expected: String) {
    regexShouldMatch(SshPrompts.PASSPHRASE_PROMPT, input, expected) { SshPrompts.extractKeyPath(it) }
  }

  private fun regexShouldMatch(pattern: Pattern, input: String, expected: String, resultProvider: (Matcher) -> String) {
    val matcher = pattern.matcher(input)
    assertTrue(matcher.matches())
    val result = resultProvider(matcher)
    assertEquals(expected, result)
  }
}
