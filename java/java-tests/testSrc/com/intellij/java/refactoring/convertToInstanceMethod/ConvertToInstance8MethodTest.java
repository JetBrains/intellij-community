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
package com.intellij.java.refactoring.convertToInstanceMethod;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.refactoring.BaseRefactoringProcessor;

public class ConvertToInstance8MethodTest extends ConvertToInstanceMethodTest {
  @Override
  protected String getBasePath() {
    return "/refactoring/convertToInstance8Method/";
  }

  public void testThisInsteadOfNoQualifier() {
    doTest(0);
  }

  public void testMethodReferenceAcceptableBySecondSearch() {
    doTest(0);
  }

  public void testConvertToInstanceMethodOfTheSameClass() {
    doTest(-1);
  }

  public void testConvertToInstanceMethodOfTheSameClassWithTypeParams() {
    try {
      doTest(-1);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals(StringUtil.trimEnd(StringUtil.repeat("Impossible to infer class type arguments. When proceed, raw Bar would be created\n", 3), "\n"), e.getMessage());
    }
  }

  public void testMethodReferenceToLambda() {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> doTest(1));
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_8;
  }
}
