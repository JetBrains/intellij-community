package com.intellij.util.io

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class IsLocalHostTest {
  @Test
  fun `google ip`() {
    assertThat(isLocalHost("37.29.1.113", false)).isFalse()
  }

  @Test
  fun `google name`() {
    assertThat(isLocalHost("google.com", false)).isFalse()
  }

  @Test
  fun `jetbrains name`() {
    assertThat(isLocalHost("jetbrains.com", false)).isFalse()
  }

  @Test
  fun `unknown name`() {
    assertThat(isLocalHost("foo.com", false)).isFalse()
  }

  @Test
  fun `unknown unqualified name`() {
    assertThat(isLocalHost("local", false)).isFalse()
  }

  @Test
  fun `invalid ip`() {
    assertThat(isLocalHost("0.0.0.0.0.0.0", false)).isFalse()
  }

  @Test
  fun `any`() {
    assertThat(isLocalHost("0.0.0.0", false)).isTrue()
  }

  @Test
  fun `localhost`() {
    assertThat(isLocalHost("localhost", false)).isTrue()
  }

  @Test
  fun `localhost only loopback`() {
    assertThat(isLocalHost("localhost", true)).isTrue()
  }
}