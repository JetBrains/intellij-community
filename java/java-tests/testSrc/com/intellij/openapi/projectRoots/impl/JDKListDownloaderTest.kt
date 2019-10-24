// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl

import org.junit.Assert
import org.junit.Test

class JDKListDownloaderTest {

  @Test
  fun `model can be downloaded and parsed`() {
    val data = JDKListDownloader.downloadModel(null)
    Assert.assertTrue(data.items.isNotEmpty())
  }
}
