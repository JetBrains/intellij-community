// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.concurrency.ThreadingAssertions
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.APP)
internal class ActionGroupCustomizationService {
  companion object {
    @JvmStatic
    fun getInstance(): ActionGroupCustomizationService = service()
  }

  fun getReadOnlyActionGroupIds(): Set<String> {
    ThreadingAssertions.assertBackgroundThread()
    val result = hashSetOf<String>()
    for (extension in ACTION_GROUP_CUSTOMIZATION_EP.extensionList) {
      result.addAll(extension.getReadOnlyActionGroupIds())
    }
    return result
  }
}

internal val ACTION_GROUP_CUSTOMIZATION_EP: ExtensionPointName<ActionGroupCustomizationExtension> =
  ExtensionPointName.create("com.intellij.actionGroupCustomization")

@ApiStatus.Internal
interface ActionGroupCustomizationExtension {
  fun getReadOnlyActionGroupIds(): Set<String>
}
