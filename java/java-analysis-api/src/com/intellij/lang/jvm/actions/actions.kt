/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:JvmName("JvmElementActionFactories")

package com.intellij.lang.jvm.actions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmModifiersOwner
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.registry.Registry

fun useInterlaguageActions(): Boolean = ApplicationManager.getApplication().isUnitTestMode || Registry.`is`("ide.interlanguage.fixes")

val EP_NAME = ExtensionPointName.create<JvmElementActionsFactory>("com.intellij.lang.jvm.actions.jvmElementActionsFactory")

private inline fun createActions(crossinline actions: (JvmElementActionsFactory) -> List<IntentionAction>): List<IntentionAction> {
  return EP_NAME.extensions.flatMap {
    actions(it)
  }
}

fun createModifierActions(target: JvmModifiersOwner, request: MemberRequest.Modifier): List<IntentionAction> {
  return createActions {
    it.createChangeModifierActions(target, request)
  }
}

fun createConstructorActions(target: JvmClass, request: MemberRequest.Constructor): List<IntentionAction> {
  return createActions {
    it.createAddConstructorActions(target, request)
  }
}

fun createMethodAction(target: JvmClass, request: MemberRequest.Method): IntentionAction? {
  for (factory in EP_NAME.extensions) {
    return factory.createAddMethodActions(target, request).firstOrNull() ?: continue
  }
  return null
}

fun createMethodActions(target: JvmClass, request: MemberRequest.Method): List<IntentionAction> {
  return createActions {
    it.createAddMethodActions(target, request)
  }
}

fun createPropertyActions(target: JvmClass, request: MemberRequest.Property): List<IntentionAction> {
  return createActions {
    it.createAddPropertyActions(target, request)
  }
}
