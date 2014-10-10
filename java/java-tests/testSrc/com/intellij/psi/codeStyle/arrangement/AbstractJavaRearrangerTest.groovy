/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.arrangement

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.java.JavaLanguage

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.*
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.PUBLIC
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.STATIC

/**
 * @author Denis Zhdanov
 */
abstract class AbstractJavaRearrangerTest extends AbstractRearrangerTest {
  protected def classic = [rule(INTERFACE),
                           rule(CLASS),
                           rule(FIELD, STATIC),
                           rule(FIELD, PUBLIC),
                           rule(FIELD),
                           rule(METHOD, PUBLIC),
                           rule(METHOD)]

  AbstractJavaRearrangerTest() {
    fileType = JavaFileType.INSTANCE
    language = JavaLanguage.INSTANCE
  }
}
