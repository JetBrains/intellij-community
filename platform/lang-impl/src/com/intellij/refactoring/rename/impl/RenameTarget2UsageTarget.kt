// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.impl

import com.intellij.model.Pointer
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.TargetPresentation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.usages.UsageTarget
import javax.swing.Icon

internal class RenameTarget2UsageTarget(
  private val pointer: Pointer<out RenameTarget>,
  private val newName: String
) : UsageTarget {

  private val target: RenameTarget?
    get() {
      ApplicationManager.getApplication().assertReadAccessAllowed()
      return pointer.dereference()
    }

  override fun isValid(): Boolean = target != null

  // ----- presentation -----

  override fun getPresentation(): ItemPresentation = itemPresentation

  private var itemPresentation: ItemPresentation = computeItemPresentation(requireNotNull(target))

  override fun update() {
    itemPresentation = computeItemPresentation(target ?: return)
  }

  private fun computeItemPresentation(target: RenameTarget): ItemPresentation = object : ItemPresentation {

    private val presentation: TargetPresentation = target.presentation()

    override fun getIcon(unused: Boolean): Icon? = presentation.icon

    override fun getPresentableText(): String = RefactoringBundle.message(
      "rename.target.text.0.1", presentation.presentableText, newName
    )

    override fun getLocationString(): String? = presentation.locationText
  }


  override fun isReadOnly(): Boolean = false

  // ----- Navigatable & NavigationItem -----

  override fun getName(): String? = null
  override fun canNavigate(): Boolean = false
  override fun canNavigateToSource(): Boolean = false
  override fun navigate(requestFocus: Boolean): Unit = Unit

  // ----- actions -----

  override fun findUsages(): Unit = Unit
}
