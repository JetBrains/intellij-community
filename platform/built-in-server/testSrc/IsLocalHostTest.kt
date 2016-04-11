package org.jetbrains.io

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class IsLocalHostTest {
  @Test
  fun `google ip`() {
    assertThat(isLocalHost("37.29.1.113")).isFalse()
  }

  @Test
  fun `google name`() {
    assertThat(isLocalHost("google.com")).isFalse()
  }

  @Test
  fun `jetbrains name`() {
    assertThat(isLocalHost("jetbrains.com")).isFalse()
  }

  @Test
  fun `unknown name`() {
    assertThat(isLocalHost("foo.com")).isFalse()
  }

  @Test
  fun `unknown unqualified name`() {
    assertThat(isLocalHost("local")).isFalse()
  }

  @Test
  fun `invalid ip`() {
    assertThat(isLocalHost("0.0.0.0.0.0.0")).isFalse()
  }

  @Test
  fun `any`() {
    assertThat(isLocalHost("0.0.0.0")).isTrue()
  }

  @Test
  fun `localhost`() {
    assertThat(isLocalHost("localhost")).isTrue()
  }
}