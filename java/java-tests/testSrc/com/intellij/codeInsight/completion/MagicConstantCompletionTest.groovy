/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion

import com.intellij.psi.PsiClass
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

/**
 * @author peter
 */
class MagicConstantCompletionTest extends LightCodeInsightFixtureTestCase {

  public void "test in method argument"() {
    addModifierList()
    myFixture.configureByText "a.java", """
class Foo {
  static void foo(ModifierList ml) {
    ml.hasModifierProperty(<caret>)
  }
}
"""
    myFixture.complete(CompletionType.SMART)
    myFixture.assertPreferredCompletionItems 0, 'PROTECTED', 'PUBLIC'
  }

  public void "test nothing after dot"() {
    addModifierList()
    myFixture.configureByText "a.java", """
class Foo {
  static void foo(ModifierList ml) {
    ml.hasModifierProperty(Foo.<caret>)
  }
}
"""
    assert !myFixture.complete(CompletionType.SMART)
  }

  private PsiClass addModifierList() {
    myFixture.addClass """
package org.intellij.lang.annotations;
public @interface MagicConstant {
  String[] stringValues() default {};
}
"""

    myFixture.addClass """
import org.intellij.lang.annotations.MagicConstant;

interface PsiModifier {
  @NonNls String PUBLIC = "public";
  @NonNls String PROTECTED = "protected";

  @MagicConstant(stringValues = { PUBLIC, PROTECTED })
  @interface ModifierConstant { }
}

interface ModifierList {
  boolean hasModifierProperty(@PsiModifier.ModifierConstant String m) {}
}
"""
  }
}
