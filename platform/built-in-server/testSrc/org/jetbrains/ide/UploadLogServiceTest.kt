// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

internal class UploadLogServiceTest : BuiltInServerTestCase() {
  override val urlPathPrefix = "/api/logs"

  @Test
  fun `statusReturnedCorrectly`() {
    doTest("/status", asSignedRequest = false) { response ->
      assertThat(response.statusCode()).isEqualTo(200)
    }
  }
}