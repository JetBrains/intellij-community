// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard.projectTypeStep

import com.intellij.ide.util.newProjectWizard.TemplatesGroup
import com.intellij.ide.wizard.impl.ProjectTypeListItem
import com.intellij.ide.wizard.impl.ProjectTypeListSection
import javax.swing.Icon

internal open class TemplateGroupItem(
  val group: TemplatesGroup
) : ProjectTypeListItem {

  override val id: String?
    get() = group.id

  override val name: String
    get() = group.name

  override val icon: Icon?
    get() = group.icon

  override val isPromo: Boolean
    get() = group.isPromo

  override val section: ProjectTypeListSection
    get() = ProjectTypeListSection.GENERATORS

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is TemplateGroupItem) return false

    if (group != other.group) return false

    return true
  }

  override fun hashCode(): Int {
    return group.hashCode()
  }

  companion object {
    @JvmField
    val COMPARATOR: Comparator<TemplateGroupItem> = compareBy { it.group }
  }
}
