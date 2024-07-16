// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup.impl

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface LookupImplVetoPolicy {
  companion object {
    val EP = ExtensionPointName<LookupImplVetoPolicy>("com.intellij.lookup.vetoPolicy")

    @JvmStatic
    fun anyVetoesHiding(lookupImpl: LookupImpl): Boolean {
      return EP.extensionList.any { policy -> policy.vetoesHiding(lookupImpl) }
    }

    @JvmStatic
    fun anyVetoesHidingOnChange(lookupImpl: LookupImpl): Boolean {
      return EP.extensionList.any{ policy -> policy.vetoesHidingOnChange(lookupImpl) }
    }
  }

  fun vetoesHiding(lookupImpl: LookupImpl): Boolean = false
  fun vetoesHidingOnChange(lookupImpl: LookupImpl): Boolean = false
}