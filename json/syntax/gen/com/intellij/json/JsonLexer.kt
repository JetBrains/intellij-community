// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.syntax

import com.intellij.json._JsonLexer
import com.intellij.platform.syntax.util.lexer.FlexAdapter

/**
 * @author Mikhail Golubev
 */
class JsonLexer : FlexAdapter(_JsonLexer()){
  fun bpp(){
    this.start("ggg")
  }
}