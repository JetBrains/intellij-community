// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmMethod
import com.intellij.lang.jvm.JvmModifiersOwner

/**
 * Extension Point provides language-abstracted code modifications for JVM-based languages.
 *
 * Each method should return  list of code modifications which could be empty.
 * If method returns empty list this means that operation on given elements is not supported or not yet implemented for a language.
 *
 * Every new added method should return empty list by default and then be overridden in implementations for each language if it is possible.
 */
abstract class JvmElementActionsFactory {

  open fun createChangeModifierActions(target: JvmModifiersOwner, request: ChangeModifierRequest): List<IntentionAction> = emptyList()

  open fun createAddAnnotationActions(target: JvmModifiersOwner, request: AnnotationRequest): List<IntentionAction> = emptyList()

  open fun createAddFieldActions(targetClass: JvmClass, request: CreateFieldRequest): List<IntentionAction> = emptyList()

  open fun createAddMethodActions(targetClass: JvmClass, request: CreateMethodRequest): List<IntentionAction> = emptyList()

  open fun createAddConstructorActions(targetClass: JvmClass, request: CreateConstructorRequest): List<IntentionAction> = emptyList()

  open fun createChangeParametersActions(target: JvmMethod, request: ChangeParametersRequest): List<IntentionAction> = emptyList()
}
