// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.customize

import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import java.util.concurrent.atomic.AtomicReference

@Deprecated(message = "This is an internal part of long removed initial wizard")
object CustomizeIDEWizardInteractions {
  val featuredPluginGroups: AtomicReference<Set<PluginId>> = AtomicReference<Set<PluginId>>()

  var skippedOnPage: Int = -1

  @JvmOverloads
  @Deprecated("Does nothing")
  fun record(type: CustomizeIDEWizardInteractionType, pluginDescriptor: PluginDescriptor? = null, groupId: String? = null) {}
}

enum class CustomizeIDEWizardInteractionType {
  WizardDisplayed
}

data class CustomizeIDEWizardInteraction(
  val type: CustomizeIDEWizardInteractionType,
  val timestamp: Long,
  val pluginDescriptor: PluginDescriptor?,
  val groupId: String?
)