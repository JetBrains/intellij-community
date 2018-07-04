// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("KotlinUtils")

package com.intellij.util

import com.intellij.openapi.util.Pair as JBPair

operator fun <A> JBPair<A, *>.component1(): A = first
operator fun <A> JBPair<*, A>.component2(): A = second

// This function helps to get rid of platform types
fun <A : Any, B : Any> JBPair<A?, B?>.toNotNull(): Pair<A, B> {
  return requireNotNull(first) to requireNotNull(second)
}
