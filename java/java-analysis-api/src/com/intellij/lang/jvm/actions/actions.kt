// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("JvmElementActionFactories")

package com.intellij.lang.jvm.actions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmModifiersOwner
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.registry.Registry

fun useInterlaguageActions(): Boolean = ApplicationManager.getApplication().isUnitTestMode || Registry.`is`("ide.interlanguage.fixes")

val EP_NAME: ExtensionPointName<JvmElementActionsFactory> = ExtensionPointName.create<JvmElementActionsFactory>("com.intellij.lang.jvm.actions.jvmElementActionsFactory")

private inline fun createActions(crossinline actions: (JvmElementActionsFactory) -> List<IntentionAction>): List<IntentionAction> {
  return EP_NAME.extensions.flatMap {
    actions(it)
  }
}

fun createMethodActions(target: JvmClass, request: CreateMethodRequest): List<IntentionAction> {
  return createActions {
    it.createAddMethodActions(target, request)
  }
}

fun createConstructorActions(target: JvmClass, request: CreateConstructorRequest): List<IntentionAction> {
  return createActions {
    it.createAddConstructorActions(target, request)
  }
}

fun createAddAnnotationActions(target: JvmModifiersOwner, request: AnnotationRequest): List<IntentionAction> {
  return createActions {
    it.createAddAnnotationActions(target, request)
  }
}

fun createModifierActions(target: JvmModifiersOwner, request: MemberRequest.Modifier): List<IntentionAction> {
  return createActions {
    it.createChangeModifierActions(target, request)
  }
}

fun createAddFieldActions(target: JvmClass, request: CreateFieldRequest): List<IntentionAction> =
  createActions { it.createAddFieldActions(target, request) }
