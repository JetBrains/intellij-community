// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore

import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.UsefulTestCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.*

private const val TEST_SERVICE_NAME = "$SERVICE_NAME_PREFIX Test"

inline fun macTest(task: () -> Unit) {
  if (SystemInfo.isMacIntel64 && !UsefulTestCase.IS_UNDER_TEAMCITY) {
    task()
  }
}

internal class CredentialStoreTest {
  @Test
  fun linux() {
    if (!SystemInfo.isLinux || UsefulTestCase.IS_UNDER_TEAMCITY) {
      return
    }

    doTest(SecretCredentialStore("com.intellij.test"))
  }

  @Test
  fun mac() {
    macTest { doTest(KeyChainCredentialStore()) }
  }

  @Test
  fun keePass() {
    doTest(KeePassCredentialStore())
  }

  @Test
  fun `mac - testEmptyAccountName`() {
    macTest { testEmptyAccountName(KeyChainCredentialStore()) }
  }

  @Test
  fun `mac - changedAccountName`() {
    macTest { testChangedAccountName(KeyChainCredentialStore()) }
  }

  @Test
  fun `linux - testEmptyAccountName`() {
    if (isLinuxSupported()) {
      testEmptyAccountName(SecretCredentialStore("com.intellij.test"))
    }
  }

  private fun isLinuxSupported() = SystemInfo.isLinux && !UsefulTestCase.IS_UNDER_TEAMCITY

  @Test
  fun `KeePass - testEmptyAccountName`() {
    testEmptyAccountName(KeePassCredentialStore())
  }

  @Test
  fun `KeePass - changedAccountName`() {
    testChangedAccountName(KeePassCredentialStore())
  }

  @Test
  fun `KeePass - memoryOnlyPassword`() {
    memoryOnlyPassword(KeePassCredentialStore())
  }

  @Test
  fun `Keychain - memoryOnlyPassword`() {
    macTest { memoryOnlyPassword(KeyChainCredentialStore()) }
  }

  @Test
  fun `linux - memoryOnlyPassword`() {
    if (isLinuxSupported()) {
      memoryOnlyPassword(SecretCredentialStore("com.intellij.test"))
    }
  }


  private fun memoryOnlyPassword(store: CredentialStore) {
    val pass = randomString()
    val userName = randomString()
    val serviceName = randomString()
    store.set(CredentialAttributes(serviceName, userName, isPasswordMemoryOnly = true), Credentials(userName, pass))

    val credentials = store.get(CredentialAttributes(serviceName, userName))
    @Suppress("UsePropertyAccessSyntax")
    assertThat(credentials).isNotNull()
    assertThat(credentials!!.userName).isEqualTo(userName)
    assertThat(credentials.password).isNullOrEmpty()
  }

  private fun doTest(store: CredentialStore) {
    val pass = randomString()
    store.setPassword(CredentialAttributes(TEST_SERVICE_NAME, "test"), pass)
    assertThat(store.getPassword(CredentialAttributes(TEST_SERVICE_NAME, "test"))).isEqualTo(pass)

    store.set(CredentialAttributes(TEST_SERVICE_NAME, "test"), null)
    assertThat(store.get(CredentialAttributes(TEST_SERVICE_NAME, "test"))).isNull()

    val unicodePassword = "Gr\u00FCnwald"
    store.setPassword(CredentialAttributes(TEST_SERVICE_NAME, "test"), unicodePassword)
    assertThat(store.getPassword(CredentialAttributes(TEST_SERVICE_NAME, "test"))).isEqualTo(unicodePassword)

    val unicodeAttributes = CredentialAttributes(TEST_SERVICE_NAME, unicodePassword)
    store.setPassword(unicodeAttributes, pass)
    assertThat(store.getPassword(unicodeAttributes)).isEqualTo(pass)
  }

  private fun testEmptyAccountName(store: CredentialStore) {
    val serviceNameOnlyAttributes = CredentialAttributes("Test IJ — ${randomString()}")
    try {
      val credentials = Credentials(randomString(), "pass")
      store.set(serviceNameOnlyAttributes, credentials)
      assertThat(store.get(serviceNameOnlyAttributes)).isEqualTo(credentials)
    }
    finally {
      store.set(serviceNameOnlyAttributes, null)
    }

    val userName = randomString()
    val attributes = CredentialAttributes("Test IJ — ${randomString()}", userName)
    try {
      store.set(attributes, Credentials(userName))
      assertThat(store.get(attributes)).isEqualTo(Credentials(userName, if (store is KeyChainCredentialStore) "" else null))
    }
    finally {
      store.set(attributes, null)
    }
  }

  private fun testChangedAccountName(store: CredentialStore) {
    val serviceNameOnlyAttributes = CredentialAttributes("Test IJ — ${randomString()}")
    try {
      val credentials = Credentials(randomString(), "pass")
      var newUserName = randomString()
      val newPassword = randomString()
      store.set(serviceNameOnlyAttributes, credentials)
      assertThat(store.get(serviceNameOnlyAttributes)).isEqualTo(credentials)
      store.set(CredentialAttributes(serviceNameOnlyAttributes.serviceName, newUserName), Credentials(newUserName, newPassword))
      assertThat(store.get(serviceNameOnlyAttributes)).isEqualTo(Credentials(newUserName, newPassword))

      newUserName = randomString()
      store.set(CredentialAttributes(serviceNameOnlyAttributes.serviceName, newUserName), Credentials(newUserName, newPassword))
      assertThat(store.get(serviceNameOnlyAttributes)!!.userName).isEqualTo(newUserName)
    }
    finally {
      store.set(serviceNameOnlyAttributes, null)
    }
  }
}

internal fun randomString() = UUID.randomUUID().toString()