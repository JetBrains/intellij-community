package com.intellij.credentialStore.linux

import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.UsefulTestCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.math.BigInteger
import java.util.*

class LinuxSecretTest {
  @Test
  fun test() {
    if (!SystemInfo.isLinux || UsefulTestCase.IS_UNDER_TEAMCITY) {
      return
    }

    val store = SecretCredentialStore("com.intellij.test")
    val pass = BigInteger(128, Random()).toString(32)
    store.set("test", pass.toByteArray())
    assertThat(store.get("test")).isEqualTo(pass)

    store.set("test", null)
    assertThat(store.get("test")).isNull()

    val unicodePassword = "Gr\u00FCnwald"
    store.set("test", unicodePassword.toByteArray())
    assertThat(store.get("test")).isEqualTo(unicodePassword)

    store.set(unicodePassword, pass.toByteArray())
    assertThat(store.get(unicodePassword)).isEqualTo(pass)
  }
}