// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup

import org.jetbrains.annotations.ApiStatus

/**
 * DO NOT USE THIS INTERFACE FOR LOOKUP ELEMENT CUSTOMIZATION
 * Use designated LookupElement#getLookupString
 *
 * This interface is necessary for RD when we want to insert presentation text instead of the original lookup string.
 * In not-so-rare cases, it is a better approximation of the insertion text.
 * At the same time, we want to keep lookupString untouched.
 */
@Deprecated("Use LookupElement#getLookupString directly!!!")
@ApiStatus.Internal
interface LookupElementWithOverriddenLookupString {
  fun getOverriddenLookupString(): String
}
