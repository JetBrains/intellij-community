// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution

import com.intellij.execution.envFile.parseEnvFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class EnvFileParserTest {
  @Test  fun `simple test`() {
    doTest("foo=bar", "foo", "bar")
  }

  @Test  fun `test comment`() {
    doTest("foo=bar  #comment", "foo", "bar")
  }

  @Test  fun `single quotas`() {
    doTest("foo='foo#bar' #comment", "foo", "foo#bar")
  }

  @Test  fun `double quotas`() {
    doTest("foo=\"foo#bar\" #comment", "foo", "foo#bar")
  }

  @Test  fun multiline() {
    doTest("foo=\"foo\nbar\" #comment", "foo", "foobar")
  }

  private fun doTest(input: String, key: String, value: String) {
    val map = parseEnvFile(input)
    assertThat(map).hasSize(1)
    val entry = map.entries.first()
    assertThat(entry.key).isEqualTo(key)
    assertThat(entry.value).isEqualTo(value)
  }
}