// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore

import com.intellij.credentialStore.keePass.InMemoryCredentialStore
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.AssumptionViolatedException
import org.junit.Test
import java.io.Closeable
import java.util.*

private val TEST_SERVICE_NAME = generateServiceName("Test", "test")

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

    val store = SecretCredentialStore.create("com.intellij.test")
    if (store == null) throw AssumptionViolatedException("No secret service")
    doTest(store)
  }

  @Test
  fun linuxKWallet() {
    if (!SystemInfo.isLinux || UsefulTestCase.IS_UNDER_TEAMCITY) {
      return
    }
    val kWallet = KWalletCredentialStore.create()
    if (kWallet == null) {
      throw AssumptionViolatedException("No KWallet")
    }

    doTest(kWallet)
  }

  @Test
  fun mac() {
    macTest { doTest(KeyChainCredentialStore()) }
  }

  @Test
  fun keePass() {
    doTest(InMemoryCredentialStore())
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
      val store = SecretCredentialStore.create("com.intellij.test")
      if (store != null) testEmptyAccountName(store)
    }
  }

  private fun isLinuxSupported() = SystemInfo.isLinux && !UsefulTestCase.IS_UNDER_TEAMCITY

  @Test
  fun `KeePass - testEmptyAccountName`() {
    testEmptyAccountName(InMemoryCredentialStore())
  }

  @Test
  fun `KeePass - testEmptyStrAccountName`() {
    testEmptyStrAccountName(InMemoryCredentialStore())
  }

  @Test
  fun `KeePass - changedAccountName`() {
    testChangedAccountName(InMemoryCredentialStore())
  }

  @Test
  fun `KeePass - memoryOnlyPassword`() {
    memoryOnlyPassword(InMemoryCredentialStore())
  }

  @Test
  fun `Keychain - memoryOnlyPassword`() {
    macTest { memoryOnlyPassword(KeyChainCredentialStore()) }
  }

  @Test
  fun `linux - memoryOnlyPassword`() {
    if (isLinuxSupported()) {
      val store = SecretCredentialStore.create("com.intellij.test")
      if (store != null) memoryOnlyPassword(store)
    }
  }

  @Test
  fun `native wrapper - pending removal`() {
    val store = wrappedInMemory()
    val attributes = CredentialAttributes("attr")
    val c1 = Credentials("u1", "p1")
    runInEdtAndWait {
      store.set(attributes, c1)
      assertThat(store.get(attributes)).isEqualTo(c1)
      store.set(attributes, null)
      PlatformTestUtil.dispatchNextEventIfAny(IdeEventQueue.getInstance())
      assertThat(store.get(attributes)).isNull()
    }
  }

  @Test
  fun `native wrapper - removal attrs`() {
    val store = wrappedInMemory()
    val attributes = CredentialAttributes("attr")
    val c1 = Credentials("u1", "p1")
    val attributes2 = CredentialAttributes("attr", c1.userName)
    runInEdtAndWait {
      store.set(attributes, c1)
      assertThat(store.get(attributes)).isEqualTo(c1)
      store.set(attributes, null)
      assertThat(store.get(attributes)).isNull()
      assertThat(store.get(attributes2)).isNull()
    }
  }


  @Test
  fun `native wrapper - multiple`() {
    val store = wrappedInMemory()
    val attributes = CredentialAttributes("attr")
    val c1 = Credentials("u1", "p1")
    val c2 = Credentials("u2", "p2")
    runInEdtAndWait {
      store.set(attributes, c1)
      assertThat(store.get(attributes)).isEqualTo(c1)
      store.set(attributes, c2)
      PlatformTestUtil.dispatchNextEventIfAny(IdeEventQueue.getInstance())
      assertThat(store.get(attributes)).isEqualTo(c2)
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
    if (store is Closeable) store.close()
  }

  private fun testEmptyAccountName(store: CredentialStore) {
    val serviceNameOnlyAttributes = CredentialAttributes("Test IJ — ${randomString()}")
    try {
      val credentials = Credentials(randomString(), "pass")
      store.set(serviceNameOnlyAttributes, credentials)
      assertThat(store.get(serviceNameOnlyAttributes)).isEqualTo(credentials)
      val credentials2 = Credentials(randomString(), "pass2")
      store.set(serviceNameOnlyAttributes, credentials2)
      assertThat(store.get(serviceNameOnlyAttributes)).isEqualTo(credentials2)
      val attributesWithUser = CredentialAttributes(serviceNameOnlyAttributes.serviceName, credentials.userName)
      assertThat(store.get(attributesWithUser)).isNull()
      val attributesWithUser2 = CredentialAttributes(serviceNameOnlyAttributes.serviceName, credentials2.userName)
      assertThat(store.get(attributesWithUser2)).isEqualTo(credentials2)
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

  private fun testEmptyStrAccountName(store: CredentialStore) {
    val attributes = CredentialAttributes("Test IJ — ${randomString()}", "")
    try {
      val credentials = Credentials("", "pass")
      store.set(attributes, credentials)
      assertThat(store.get(attributes)).isEqualTo(credentials)
    }
    finally {
      store.set(attributes, null)
    }
    assertThat(store.get(attributes)).isNull()
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