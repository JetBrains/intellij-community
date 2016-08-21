package com.intellij.credentialStore

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
class CredentialTest {
  @Test
  fun join() {
    test("foo", "pass", "foo@pass")
    test("foo", "\\pass@", "foo@\\pass@")
    test("foo@", "pass", "foo\\@@pass")
    test("\\foo@", "pass", "\\\\foo\\@@pass")
    test("\\foo\\", "pass", "\\\\foo\\\\@pass")
    test("foo", "", "foo@")
  }

  @Test
  fun emptyUser() {
    test("", "pass", "@pass")
  }

  private fun test(u: String, p: String, joined: String) {
    assertThat(joinData(u, p)).isEqualTo(joined)
    assertThat(splitData(joined)).isEqualTo(Credentials(u, p))
  }
}