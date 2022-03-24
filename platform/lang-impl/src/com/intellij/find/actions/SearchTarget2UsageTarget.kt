// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("DEPRECATION")

package com.intellij.find.actions

import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.api.UsageHandler
import com.intellij.find.usages.impl.AllSearchOptions
import com.intellij.model.Pointer
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.usageView.UsageViewBundle
import com.intellij.usageView.UsageViewUtil
import com.intellij.usages.ConfigurableUsageTarget
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageView
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon

@ApiStatus.Internal
class SearchTarget2UsageTarget<O>(
  private val project: Project,
  target: SearchTarget,
  private val allOptions: AllSearchOptions<O>
) : UsageTarget, DataProvider, ConfigurableUsageTarget {

  private val myPointer: Pointer<out SearchTarget> = target.createPointer()
  override fun isValid(): Boolean = myPointer.dereference() != null

  // ----- presentation -----

  private var myItemPresentation: ItemPresentation = getItemPresentation(target)

  override fun update() {
    val target = myPointer.dereference() ?: return
    myItemPresentation = getItemPresentation(target)
  }

  private fun getItemPresentation(target: SearchTarget): ItemPresentation {
    val presentation = target.presentation
    return object : ItemPresentation {
      override fun getIcon(unused: Boolean): Icon? = presentation.icon
      override fun getPresentableText(): String = presentation.presentableText
      override fun getLocationString(): String = presentation.locationText ?: ""
    }
  }

  override fun getPresentation(): ItemPresentation = myItemPresentation

  override fun isReadOnly(): Boolean = false // TODO used in Usage View displayed by refactorings

  // ----- Navigatable & NavigationItem -----
  // TODO Symbol navigation

  override fun canNavigate(): Boolean = false
  override fun canNavigateToSource(): Boolean = false
  override fun getName(): String? = null
  override fun navigate(requestFocus: Boolean) = Unit

  // ----- actions -----

  override fun getShortcut(): KeyboardShortcut? = UsageViewUtil.getShowUsagesWithSettingsShortcut()

  override fun getLongDescriptiveName(): @Nls String {
    val target = myPointer.dereference() ?: return UsageViewBundle.message("node.invalid")
    @Suppress("UNCHECKED_CAST") val usageHandler = target.usageHandler as UsageHandler<O>
    return UsageViewBundle.message(
      "search.title.0.in.1",
      usageHandler.getSearchString(allOptions),
      allOptions.options.searchScope.displayName
    )
  }

  override fun showSettings() {
    val target = myPointer.dereference() ?: return
    @Suppress("UNCHECKED_CAST") val usageHandler = target.usageHandler as UsageHandler<O>
    val dialog = UsageOptionsDialog(project, target.displayString, usageHandler, allOptions, target.showScopeChooser(), true)
    if (!dialog.showAndGet()) {
      return
    }
    val newOptions = dialog.result()
    findUsages(project, target, usageHandler, newOptions)
  }

  override fun findUsages(): Unit = error("must not be called")
  override fun findUsagesInEditor(editor: FileEditor): Unit = error("must not be called")
  override fun highlightUsages(file: PsiFile, editor: Editor, clearHighlights: Boolean): Unit = error("must not be called")

  // ----- data context -----

  override fun getData(dataId: String): Any? {
    if (UsageView.USAGE_SCOPE.`is`(dataId)) {
      return allOptions.options.searchScope
    }
    return null
  }
}