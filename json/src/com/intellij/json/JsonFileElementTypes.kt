// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json

import com.intellij.json.json5.Json5Language
import com.intellij.json.jsonLines.JsonLinesLanguage
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.psi.SyntaxGrammarKitFileElementType
import com.intellij.psi.tree.IFileElementType

@JvmField
val JSON_SYNTAX_FILE: SyntaxElementType = SyntaxElementType("JSON_FILE")

@JvmField
val JSON_FILE: IFileElementType = SyntaxGrammarKitFileElementType(JsonLanguage.INSTANCE)

@JvmField
val JSON5_SYNTAX_FILE: SyntaxElementType = SyntaxElementType("JSON5_FILE")

@JvmField
val JSON5_FILE: IFileElementType = SyntaxGrammarKitFileElementType(Json5Language.INSTANCE)

@JvmField
val JSON_LINES_SYNTAX_FILE: SyntaxElementType = SyntaxElementType("JSON_LINES_FILE")

@JvmField
val JSON_LINES_FILE: IFileElementType = SyntaxGrammarKitFileElementType(JsonLinesLanguage)