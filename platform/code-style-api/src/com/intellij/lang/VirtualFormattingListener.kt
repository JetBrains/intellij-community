// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang

import com.intellij.openapi.util.TextRange


interface VirtualFormattingListener {

  fun shiftIndentInsideRange(node: ASTNode?, range: TextRange, indent: Int)

  fun replaceWhiteSpace(textRange: TextRange, whiteSpace: String)

}
