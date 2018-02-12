// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions

import com.intellij.lang.jvm.types.JvmType

typealias ExpectedTypes = List<ExpectedType>

fun expectedType(type: JvmType, kind: ExpectedType.Kind = ExpectedType.Kind.EXACT): ExpectedType = SimpleExpectedType(type, kind)

fun expectedTypes(type: JvmType): ExpectedTypes = listOf(expectedType(type))

private class SimpleExpectedType(
  override val theType: JvmType,
  override val theKind: ExpectedType.Kind
) : ExpectedType
