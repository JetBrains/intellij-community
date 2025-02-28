// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.parser.elements

import com.intellij.util.xml.dom.XmlElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed class ActionDescriptor(
  @JvmField val name: ActionDescriptorName,
  @JvmField val element: XmlElement,
  @JvmField val resourceBundle: String?,
) {
  @Suppress("EnumEntryName")
  enum class ActionDescriptorName {
    action, group, separator, reference, unregister, prohibit,
  }

  class ActionDescriptorMisc(
    name: ActionDescriptorName,
    element: XmlElement,
    resourceBundle: String?,
  ) : ActionDescriptor(name, element, resourceBundle)

  class ActionDescriptorAction(
    @JvmField val className: String,
    @JvmField val isInternal: Boolean,
    element: XmlElement,
    resourceBundle: String?,
  ) : ActionDescriptor(name = ActionDescriptorName.action, element = element, resourceBundle = resourceBundle)

  class ActionDescriptorGroup(
    @JvmField val className: String?,
    @JvmField val id: String?,
    element: XmlElement,
    resourceBundle: String?,
  ) : ActionDescriptor(name = ActionDescriptorName.group, element = element, resourceBundle = resourceBundle)
}