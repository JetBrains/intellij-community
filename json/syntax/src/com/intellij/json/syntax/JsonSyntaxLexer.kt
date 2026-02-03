// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.syntax

import com.intellij.platform.syntax.util.lexer.FlexAdapter

/**
 * @author Mikhail Golubev
 */
class JsonSyntaxLexer : FlexAdapter(_JsonLexer())