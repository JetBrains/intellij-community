// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore

import com.intellij.credentialStore.gpg.GpgToolWrapper
import com.intellij.credentialStore.gpg.Pgp
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.SystemProperties
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.readBytes

internal class PgpTest {
  @Test
  fun `list keys`() {
    val pgp = Pgp(object : GpgToolWrapper {
      override fun listSecretKeys(): String {
        return """
          sec:u:4096:1:4A80F49A4F310160:1488277734:::u:::scESC:::+:::23::0:
          fpr:::::::::59B9F2A7746C72782F769DF14A80F49A4F310160:
          grp:::::::::17BD0839039245C34F1D6F4E19AF1D3A6096F875:
          uid:u::::1488277734::461DC46F99FC4906DD8CFCAA7C807415AA613E99::Foo Bar <foo@example.com>::::::::::0:
          ssb:u:4096:1:EE4C14DA445A73EF:1488277734::::::e:::+:::23:
          fpr:::::::::4DFED9B767CBD7358D53F0E6EE4C14DA445A73EF:
          grp:::::::::A1CAC786A8744EB623629FE13310F6B58219299A:
          sec:u:4096:1:C9B8EC731170961F:1540025925:::u:::scESC:::+:::23::0:
          fpr:::::::::D4F7247FF0F2C734D5A81BC1C9B8EC731170961F:
          grp:::::::::6FB00F9FE5AAA4045051642AF58E1DA5B1F854B4:
          uid:u::::1540025925::687269A34F90ADA2A69E7FED718BFF6612C5DBD2::test <test@example.com>::::::::::0:
          ssb:u:4096:1:0C58EDCCDE1BAC09:1540025925::::::e:::+:::23:
          fpr:::::::::A28E33BF31E44023B443B3980C58EDCCDE1BAC09:
          grp:::::::::B77F0FD73A184F51665D781BBA1800200BE85579:
          sec:u:4096:1:C4183F955D4DC0F1:1540025957:::u:::scSC:::+:::23::0:
          fpr:::::::::5C50DFB0A46F59D2CA667A17C4183F955D4DC0F1:
          grp:::::::::ED761CE5A962F8F12AF3484E054C7341A9E90CB4:
          uid:u::::1540025957::4432B4FD5436B0CF942D7FE0B1A7C5403B8E20A1::test (without encryption) <test@foo.com>::::::::::0:
          """.trimIndent()
      }

      override fun encrypt(data: ByteArray, recipient: String) = throw UnsupportedOperationException()

      override fun decrypt(data: ByteArray) = throw UnsupportedOperationException()
    })
    assertThat(pgp.listKeys().joinToString("\n")).isEqualTo("""
      PgpKey(keyId=4A80F49A4F310160, userId=Foo Bar <foo@example.com>)
      PgpKey(keyId=C9B8EC731170961F, userId=test <test@example.com>)
    """.trimIndent())
  }

  @Test
  fun decrypt() {
    val encryptedFile = Paths.get(SystemProperties.getUserHome(), "test.gpg")
    assumeTrue("$encryptedFile doesn't exist", encryptedFile.exists())

    assertThat(Pgp().decrypt(encryptedFile.readBytes()).toString(Charsets.UTF_8)).isEqualTo("foo bar")
  }
}
