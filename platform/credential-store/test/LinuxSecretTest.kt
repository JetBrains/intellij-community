package com.intellij.credentialStore.linux

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.UsefulTestCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.math.BigInteger
import java.util.*

private const val TEST_SERVICE_NAME = "IntelliJ Platform Test"

class LinuxSecretTest {
  @Test
  fun test() {
    if (!SystemInfo.isLinux || UsefulTestCase.IS_UNDER_TEAMCITY) {
      return
    }

    val store = SecretCredentialStore("com.intellij.test")
    val pass = BigInteger(128, Random()).toString(32)
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

    val serviceNameOnlyAttributes = CredentialAttributes("Test IJ - example.com")
    store.set(serviceNameOnlyAttributes, Credentials("foo", "pass"))
    assertThat(store.get(serviceNameOnlyAttributes)).isEqualTo(Credentials("foo", "pass"))
  }
}