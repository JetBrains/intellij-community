// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface LookupImplVetoPolicy {
  companion object {
    val EP = ExtensionPointName<LookupImplVetoPolicy>("com.intellij.lookup.vetoPolicy")

    val FORCE_VETO_HIDING = Key.create<Boolean>("FORCE_VETO_HIDING")
    val FORCE_VETO_HIDING_ON_CHANGE = Key.create<Boolean>("FORCE_VETO_HIDING_ON_CHANGE")

    @JvmStatic
    fun anyVetoesHiding(lookupImpl: LookupImpl): Boolean {
      return FORCE_VETO_HIDING.get(lookupImpl) == true || EP.extensionList.any { policy -> policy.vetoesHiding(lookupImpl) }
    }

    @JvmStatic
    fun anyVetoesHidingOnChange(lookupImpl: LookupImpl): Boolean {
      return FORCE_VETO_HIDING_ON_CHANGE.get(lookupImpl) == true || EP.extensionList.any { policy -> policy.vetoesHidingOnChange(lookupImpl) }
    }

    fun LookupImpl.forceVetoHiding() {
      putUserData(FORCE_VETO_HIDING, true)
    }

    fun LookupImpl.forceVetoHidingOnChange() {
      putUserData(FORCE_VETO_HIDING_ON_CHANGE, true)
    }
  }

  fun vetoesHiding(lookupImpl: LookupImpl): Boolean = false
  fun vetoesHidingOnChange(lookupImpl: LookupImpl): Boolean = false
}