// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.Language
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.JvmModifiersOwner
import com.intellij.lang.jvm.actions.annotationRequest
import com.intellij.lang.jvm.actions.createAddAnnotationActions
import com.intellij.lang.jvm.actions.createModifierActions
import com.intellij.lang.jvm.actions.modifierRequest
import com.intellij.util.SmartList
import org.jetbrains.uast.UField
import org.jetbrains.uast.UastVisibility

/**
 * Makes the visibility of a [UField] public.
 * This is a workaround see https://youtrack.jetbrains.com/issue/KTIJ-972
 */
fun UField.createMakePublicActions(): List<IntentionAction> {
  val jPsi = javaPsi
  val isPublic = visibility == UastVisibility.PUBLIC
  val actions = SmartList<IntentionAction>()
  if (!isPublic) actions.addAll(createModifierActions(this, modifierRequest(JvmModifier.PUBLIC, true)))
  if (sourcePsi?.language == Language.findLanguageByID("kotlin") &&
      jPsi is JvmModifiersOwner && !jPsi.hasAnnotation("kotlin.jvm.JvmField")
  ) {
    actions.addAll(createAddAnnotationActions(jPsi, annotationRequest("kotlin.jvm.JvmField")))
  }
  return actions
}