// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.concurrency.ThreadingAssertions

@Service(Service.Level.APP)
internal class ActionGroupCustomizationService {
  companion object {
    @JvmStatic
    fun getInstance(): ActionGroupCustomizationService = service()
  }

  fun getReadOnlyActionGroupIds(): Set<String> {
    ThreadingAssertions.assertBackgroundThread()
    return emptySet()
  }
}
