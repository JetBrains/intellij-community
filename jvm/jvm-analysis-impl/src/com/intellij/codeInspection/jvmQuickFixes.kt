// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.lang.jvm.actions.ChangeModifierRequest
import com.intellij.lang.jvm.actions.createModifierActions
import org.jetbrains.uast.UDeclaration

internal fun createModifierQuickfixes(target: UDeclaration, request: ChangeModifierRequest): Array<LocalQuickFix>? {
  val containingFile = target.sourcePsi?.containingFile ?: return null
  return IntentionWrapper.wrapToQuickFixes(createModifierActions(target, request).toTypedArray(), containingFile)
}