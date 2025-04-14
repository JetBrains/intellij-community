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
package com.intellij.java.syntax.element

import com.intellij.platform.syntax.SyntaxElementType

object JShellSyntaxElementType {
  @JvmField val FILE: SyntaxElementType = SyntaxElementType("JSHELL_FILE")
  @JvmField val ROOT_CLASS: SyntaxElementType = SyntaxElementType("JSHELL_ROOT_CLASS")
  @JvmField val STATEMENTS_HOLDER: SyntaxElementType = SyntaxElementType("JSHELL_STATEMENTS_HOLDER")
  @JvmField val IMPORT_HOLDER: SyntaxElementType = SyntaxElementType("JSHELL_IMPORT_HOLDER")
}
