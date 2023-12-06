// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.jvm.actions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.lang.jvm.*

/**
 * This extension point provides language-abstracted code modifications for JVM-based languages.
 *
 * Each method returns a possibly empty list of code modifications.
 * Returning an empty list means that the operation on the given elements
 * is not supported or not yet implemented for a language.
 *
 * Every newly added method should return an empty list by default
 * and then be overridden in implementations for each language if it is possible.
 */
abstract class JvmElementActionsFactory {
  open fun createChangeModifierActions(target: JvmModifiersOwner, request: ChangeModifierRequest): List<IntentionAction> = emptyList()

  open fun createChangeOverrideActions(target: JvmModifiersOwner, shouldBePresent: Boolean): List<IntentionAction> = emptyList()

  open fun createAddAnnotationActions(target: JvmModifiersOwner, request: AnnotationRequest): List<IntentionAction> = emptyList()

  open fun createRemoveAnnotationActions(target: JvmModifiersOwner, request: AnnotationRequest): List<IntentionAction> = emptyList()

  open fun createChangeAnnotationAttributeActions(annotation: JvmAnnotation,
                                                  attributeIndex: Int,
                                                  request: AnnotationAttributeRequest,
                                                  @IntentionName text: String,
                                                  @IntentionFamilyName familyName: String): List<IntentionAction> = emptyList()

  open fun createAddFieldActions(targetClass: JvmClass, request: CreateFieldRequest): List<IntentionAction> = emptyList()

  open fun createAddMethodActions(targetClass: JvmClass, request: CreateMethodRequest): List<IntentionAction> = emptyList()

  open fun createAddConstructorActions(targetClass: JvmClass, request: CreateConstructorRequest): List<IntentionAction> = emptyList()

  open fun createChangeParametersActions(target: JvmMethod, request: ChangeParametersRequest): List<IntentionAction> = emptyList()

  open fun createChangeTypeActions(target: JvmMethod, request: ChangeTypeRequest): List<IntentionAction> = emptyList()

  open fun createChangeTypeActions(target: JvmParameter, request: ChangeTypeRequest): List<IntentionAction> = emptyList()

  open fun createChangeTypeActions(target: JvmField, request: ChangeTypeRequest): List<IntentionAction> = emptyList()
}
