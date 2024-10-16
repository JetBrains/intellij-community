// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.Iconable.IconFlags
import com.intellij.psi.PsiFile
import com.intellij.ui.NewUiValue
import com.intellij.util.ui.EmptyIcon
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

private val EP_NAME = ExtensionPointName<EmptyIntentionProvider>("com.intellij.emptyIntentionProvider")

@ApiStatus.Internal
interface EmptyIntentionProvider {
  fun invoke(project: Project, editor: Editor?, file: PsiFile?, template: String): Boolean
}

internal class EmptyIntentionGeneratorIntention(private val name: @IntentionFamilyName String, private val template: String) :
  IntentionAction, LowPriorityAction, Iconable {
  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    EP_NAME.extensionList.any { it.invoke(project, editor, file, template) }
  }

  override fun startInWriteAction(): Boolean = true

  override fun getText(): @IntentionFamilyName String = name

  override fun getFamilyName(): @IntentionFamilyName String = name

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true

  override fun getIcon(@IconFlags flags: Int): Icon = if (NewUiValue.isEnabled()) EmptyIcon.ICON_0 else AllIcons.Actions.RealIntentionBulb

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as EmptyIntentionGeneratorIntention

    if (name != other.name) return false
    return template == other.template
  }

  override fun hashCode(): Int {
    var result = name.hashCode()
    result = 31 * result + template.hashCode()
    return result
  }
}