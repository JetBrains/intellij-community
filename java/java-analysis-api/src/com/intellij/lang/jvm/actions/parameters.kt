// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions

import com.intellij.lang.jvm.types.JvmType

fun expectedParameter(type: JvmType, vararg names: String): ExpectedParameter {
  return expectedParameter(expectedType(type, ExpectedType.Kind.SUPERTYPE), *names)
}

fun expectedParameter(expectedType: ExpectedType, vararg names: String): ExpectedParameter {
  return expectedParameter(listOf(expectedType), *names)
}

fun expectedParameter(types: List<ExpectedType>, vararg names: String): ExpectedParameter {
  return expectedParameter(types, names.toList())
}

fun expectedParameter(types: List<ExpectedType>, names: Collection<String>): ExpectedParameter {
  return SimpleExpectedParameter(types, names)
}

private class SimpleExpectedParameter(
  private val types: List<ExpectedType>,
  private val names: Collection<String>
) : ExpectedParameter {
  override fun getExpectedTypes() = types
  override fun getSemanticNames() = names
}