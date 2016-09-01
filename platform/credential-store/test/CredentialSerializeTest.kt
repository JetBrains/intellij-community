package com.intellij.credentialStore

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class CredentialSerializeTest {
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

  @Test
  fun nullPassword() {
    test("foo", null, "foo")
    test(null, "foo@", "@foo@")
  }

  private fun test(u: String?, p: String?, joined: String) {
    val pass = p?.let(::OneTimeString)
    assertThat(joinData(u, pass)).isEqualTo(joined.toByteArray())
    assertThat(splitData(joined)).isEqualTo(Credentials(u, pass))
  }
}