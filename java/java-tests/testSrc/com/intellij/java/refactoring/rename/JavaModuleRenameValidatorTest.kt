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
package com.intellij.java.refactoring.rename

import com.intellij.psi.PsiJavaFile
import com.intellij.refactoring.rename.JavaModuleRenameValidator
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.ProcessingContext
import org.junit.Test

class JavaModuleRenameValidatorTest : LightCodeInsightFixtureTestCase() {
  @Test fun test() {
    val validator = JavaModuleRenameValidator()
    val module = (myFixture.configureByText("module-info.java", "module M { }") as PsiJavaFile).moduleDeclaration!!
    val context = ProcessingContext()

    assertTrue(validator.isInputValid("M", module, context))
    assertTrue(validator.isInputValid("M.M.M", module, context))
    assertTrue(validator.isInputValid("M42", module, context))

    assertFalse(validator.isInputValid("", module, context))
    assertFalse(validator.isInputValid(" ", module, context))
    assertFalse(validator.isInputValid("\n", module, context))
    assertFalse(validator.isInputValid("42", module, context))
    assertFalse(validator.isInputValid("M.42", module, context))
    assertFalse(validator.isInputValid("M.", module, context))
    assertFalse(validator.isInputValid(".M", module, context))
    assertFalse(validator.isInputValid("M.M.", module, context))
    assertFalse(validator.isInputValid("M..M", module, context))
  }
}