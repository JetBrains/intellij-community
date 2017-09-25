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
package com.intellij.lang.java.request

import com.intellij.codeInsight.ExpectedTypeInfo
import com.intellij.lang.jvm.actions.ExpectedType
import com.intellij.lang.jvm.types.JvmType

internal class ExpectedJavaType(val info: ExpectedTypeInfo) : ExpectedType {

  override val theType: JvmType get() = info.defaultType

  override val theKind: ExpectedType.Kind
    get() = when (info.kind) {
      ExpectedTypeInfo.TYPE_OR_SUBTYPE -> ExpectedType.Kind.SUBTYPE
      ExpectedTypeInfo.TYPE_OR_SUPERTYPE -> ExpectedType.Kind.SUPERTYPE
      else -> ExpectedType.Kind.EXACT
    }
}
