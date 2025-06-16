// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.find.actions

import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.impl.AllSearchOptions
import com.intellij.model.Pointer
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.UiDataProvider
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
class SearchTarget2UsageTarget(
  private val project: Project,
  target: SearchTarget,
  private val allOptions: AllSearchOptions,
) : UsageTarget, UiDataProvider, ConfigurableUsageTarget {

  private val myPointer: Pointer<out SearchTarget> = target.createPointer()
  override fun isValid(): Boolean = myPointer.dereference() != null

  // ----- presentation -----

  private var myItemPresentation: ItemPresentation = getItemPresentation(target)

  override fun update() {
    val target = myPointer.dereference() ?: return
    myItemPresentation = getItemPresentation(target)
  }

  private fun getItemPresentation(target: SearchTarget): ItemPresentation {
    val presentation = target.presentation()
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

  override fun getName(): String? = null

  // ----- actions -----

  override fun getShortcut(): KeyboardShortcut? = UsageViewUtil.getShowUsagesWithSettingsShortcut()

  override fun getLongDescriptiveName(): @Nls String {
    val target = myPointer.dereference() ?: return UsageViewBundle.message("node.invalid")
    return UsageViewBundle.message(
      "search.title.0.in.1",
      target.usageHandler.getSearchString(allOptions),
      allOptions.options.searchScope.displayName
    )
  }

  override fun showSettings() {
    val target = myPointer.dereference() ?: return
    val dialog = UsageOptionsDialog(project, target.displayString, allOptions, target.showScopeChooser(), true)
    if (!dialog.showAndGet()) {
      return
    }
    val newOptions = dialog.result()
    findUsages(project, target, newOptions)
  }

  override fun findUsages(): Unit = error("must not be called")
  override fun findUsagesInEditor(editor: FileEditor): Unit = error("must not be called")
  override fun highlightUsages(file: PsiFile, editor: Editor, clearHighlights: Boolean): Unit = error("must not be called")

  // ----- data context -----

  override fun uiDataSnapshot(sink: DataSink) {
    sink[UsageView.USAGE_SCOPE] = allOptions.options.searchScope
  }
}
