// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax.element

import com.intellij.platform.syntax.SyntaxElementType
import kotlin.jvm.JvmField

object JShellSyntaxElementType {
  @JvmField val FILE: SyntaxElementType = SyntaxElementType("JSHELL_FILE")
  @JvmField val ROOT_CLASS: SyntaxElementType = SyntaxElementType("JSHELL_ROOT_CLASS")
  @JvmField val STATEMENTS_HOLDER: SyntaxElementType = SyntaxElementType("JSHELL_STATEMENTS_HOLDER")
  @JvmField val IMPORT_HOLDER: SyntaxElementType = SyntaxElementType("JSHELL_IMPORT_HOLDER")
}
