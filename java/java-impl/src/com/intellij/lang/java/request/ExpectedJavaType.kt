// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.request

import com.intellij.codeInsight.ExpectedTypeInfo
import com.intellij.lang.jvm.actions.ExpectedType
import com.intellij.lang.jvm.types.JvmType

internal class ExpectedJavaType(val info: ExpectedTypeInfo) : ExpectedType {

  override fun getTheType(): JvmType = info.defaultType

  override fun getTheKind(): ExpectedType.Kind = when (info.kind) {
    ExpectedTypeInfo.TYPE_OR_SUBTYPE -> ExpectedType.Kind.SUBTYPE
    ExpectedTypeInfo.TYPE_OR_SUPERTYPE -> ExpectedType.Kind.SUPERTYPE
    else -> ExpectedType.Kind.EXACT
  }
}
