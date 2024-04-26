// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard.projectTypeStep

import com.intellij.ide.util.newProjectWizard.TemplatesGroup
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal open class TemplateGroupItem(
  val group: TemplatesGroup
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is TemplateGroupItem) return false

    if (group != other.group) return false

    return true
  }

  override fun hashCode(): Int {
    return group.hashCode()
  }
}
