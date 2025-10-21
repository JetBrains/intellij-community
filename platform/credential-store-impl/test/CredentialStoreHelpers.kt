// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("CredentialStoreHelpers")
package com.intellij.credentialStore

import com.intellij.ide.passwordSafe.impl.BasePasswordSafe
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.assertions.Assertions
import com.intellij.util.system.OS
import org.junit.Assume.assumeTrue
import java.util.*

internal fun assumeLocalMac() =
  assumeTrue("Local macOS only", OS.CURRENT == OS.macOS && !UsefulTestCase.IS_UNDER_TEAMCITY)

internal fun assumeLocalLinux() =
  assumeTrue("Local Linux only", OS.CURRENT == OS.Linux && (!UsefulTestCase.IS_UNDER_TEAMCITY || System.getenv("FORCE_CREDENTIALS_TEST") != null))

internal fun randomString() = UUID.randomUUID().toString()

internal fun doNullUsername(ps: BasePasswordSafe) {
  val attributes = CredentialAttributes(randomString())
  try {
    ps.set(attributes, Credentials(null, "password"))

    val saved = ps.get(attributes)!!
    Assertions.assertThat(saved.userName).isNullOrEmpty()
    Assertions.assertThat(saved.password).isEqualTo("password")
  }
  finally {
    ps.set(attributes, null)
  }
}

internal fun doErasePassword(ps: BasePasswordSafe) {
  val attributes = CredentialAttributes(randomString())
  try {
    ps.set(attributes, Credentials("a", "b"))
    ps.set(attributes, Credentials("a", null as String?))

    val saved = ps.get(attributes)!!
    Assertions.assertThat(saved.userName).isEqualTo("a")
    Assertions.assertThat(saved.password).isNullOrEmpty()
  }
  finally {
    ps.set(attributes, null)
  }
}
