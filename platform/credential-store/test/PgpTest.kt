// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore

import com.intellij.credentialStore.gpg.GpgToolWrapper
import com.intellij.credentialStore.gpg.Pgp
import com.intellij.credentialStore.gpg.PgpKey
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.SystemProperties
import com.intellij.util.io.exists
import com.intellij.util.io.readBytes
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Paths

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
                sec:u:4096:1:0CE777E8BE9477EF:1539670578:::u:::scESC:::+:::23::0:
                fpr:::::::::815DCD20BECFE321EA37E67D0CE777E8BE9477EF:
                grp:::::::::CB26AD8D61B30F34BC13F5678FEA8D5247980205:
                uid:u::::1539670578::245790E6FDD162E381082973E71F54B6238A7E9C::test <test@example.com>::::::::::0:
                ssb:u:4096:1:F678B66FEFDDA6CA:1539670578::::::e:::+:::23:
                fpr:::::::::71F1746CE870050BC15D9424F678B66FEFDDA6CA:
                grp:::::::::954970BF3D78156316E8AE8CB94955E3B4B2412A:
              """.trimIndent()
            }

      override fun encrypt(data: ByteArray, recipient: String) = throw UnsupportedOperationException()

      override fun decrypt(data: ByteArray) = throw UnsupportedOperationException()
    })
    assertThat(pgp.listKeys()).containsExactly(PgpKey("4A80F49A4F310160", "Foo Bar <foo@example.com>"), PgpKey("0CE777E8BE9477EF", "test <test@example.com>"))
  }

  @Test
  fun decrypt() {
    val encryptedFile = Paths.get(SystemProperties.getUserHome(), "test.gpg")
    assumeTrue(encryptedFile.exists())

    assertThat(Pgp().decrypt(encryptedFile.readBytes()).toString(Charsets.UTF_8)).isEqualTo("foo bar")
  }
}