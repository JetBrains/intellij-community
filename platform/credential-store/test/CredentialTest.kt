package com.intellij.credentialStore

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
class CredentialTest {
  @Test
  fun join() {
    test("foo", "pass", "foo@pass")
    test("foo@", "pass", "foo\\@@pass")
    test("\\foo@", "pass", "\\\\foo\\@@pass")
    test("\\foo\\", "pass", "\\\\foo\\\\@pass")
    test("", "pass", "@pass")
    test("foo", "", "foo@")
  }

  private fun test(u: String, p: String, joined: String) {
    assertThat(joinData(u, p)).isEqualTo(joined)
    assertThat(splitData(joined)).isEqualTo(Credentials(u, p))
  }
}