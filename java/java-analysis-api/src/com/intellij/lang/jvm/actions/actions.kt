// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("JvmElementActionFactories")

package com.intellij.lang.jvm.actions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.lang.jvm.*
import com.intellij.openapi.extensions.ExtensionPointName

public val EP_NAME: ExtensionPointName<JvmElementActionsFactory> = ExtensionPointName.create(
  "com.intellij.lang.jvm.actions.jvmElementActionsFactory"
)

private inline fun createActions(crossinline actions: (JvmElementActionsFactory) -> List<IntentionAction>): List<IntentionAction> {
  return EP_NAME.extensionList.flatMap {
    actions(it)
  }
}

public fun createMethodActions(target: JvmClass, request: CreateMethodRequest): List<IntentionAction> {
  return createActions {
    it.createAddMethodActions(target, request)
  }
}

public fun createConstructorActions(target: JvmClass, request: CreateConstructorRequest): List<IntentionAction> {
  return createActions {
    it.createAddConstructorActions(target, request)
  }
}

public fun createAddAnnotationActions(target: JvmModifiersOwner, request: AnnotationRequest): List<IntentionAction> {
  return createActions {
    it.createAddAnnotationActions(target, request)
  }
}

public fun createRemoveAnnotationActions(target: JvmModifiersOwner, request: AnnotationRequest): List<IntentionAction> {
  return createActions {
    it.createRemoveAnnotationActions(target, request)
  }
}

public fun createChangeAnnotationAttributeActions(annotation: JvmAnnotation,
                                           attributeIndex: Int,
                                           request: AnnotationAttributeRequest,
                                           @IntentionName text: String,
                                           @IntentionFamilyName familyName: String): List<IntentionAction> {
  return createActions {
    it.createChangeAnnotationAttributeActions(annotation, attributeIndex, request, text, familyName)
  }
}

public fun createModifierActions(target: JvmModifiersOwner, request: ChangeModifierRequest): List<IntentionAction> {
  return createActions {
    it.createChangeModifierActions(target, request)
  }
}

public fun createChangeOverrideActions(target: JvmModifiersOwner, shouldBePresent: Boolean): List<IntentionAction> {
  return createActions {
    it.createChangeOverrideActions(target, shouldBePresent)
  }
}


public fun createAddFieldActions(target: JvmClass, request: CreateFieldRequest): List<IntentionAction> {
  return createActions {
    it.createAddFieldActions(target, request)
  }
}

public fun createChangeParametersActions(target: JvmMethod, request: ChangeParametersRequest): List<IntentionAction> {
  return createActions {
    it.createChangeParametersActions(target, request)
  }
}

public fun createChangeTypeActions(target: JvmMethod, request: ChangeTypeRequest): List<IntentionAction> {
  return createActions {
    it.createChangeTypeActions(target, request)
  }
}

public fun createChangeTypeActions(target: JvmParameter, request: ChangeTypeRequest): List<IntentionAction> {
  return createActions {
    it.createChangeTypeActions(target, request)
  }
}

public fun createChangeTypeActions(target: JvmField, request: ChangeTypeRequest): List<IntentionAction> {
  return createActions {
    it.createChangeTypeActions(target, request)
  }
}