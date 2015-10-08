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

import com.intellij.codeInsight.lookup.LookupManager
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

  public void "test magic constant in equality"() {
    addMagicConstant()

    myFixture.configureByText "a.java", """
class Bar {
  static void foo() {
    if (getConstant() == <caret>) {}
  }

  @org.intellij.lang.annotations.MagicConstant(flagsFromClass = Foo.class)
  public native int getConstant();
}

interface Foo {
    int FOO = 1;
    int BAR = 2;
}
"""
    myFixture.complete(CompletionType.SMART)
    myFixture.assertPreferredCompletionItems 0, 'BAR', 'FOO'
    LookupManager.getInstance(project).hideActiveLookup()

    myFixture.complete(CompletionType.BASIC)
    myFixture.assertPreferredCompletionItems 0, 'BAR', 'FOO'
  }

  public void "test magic constant in equality before another equality"() {
    addMagicConstant()

    myFixture.configureByText "a.java", """
class Bar {
  static void foo() {
    if (getConstant() == <caret>getConstant() == 2) {}
  }

  @org.intellij.lang.annotations.MagicConstant(flagsFromClass = Foo.class)
  public native int getConstant();
}

interface Foo {
    int FOO = 1;
    int BAR = 2;
}
"""
    myFixture.complete(CompletionType.SMART)
    myFixture.assertPreferredCompletionItems 0, 'BAR', 'FOO'
  }

  private PsiClass addModifierList() {
    addMagicConstant()

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

  private PsiClass addMagicConstant() {
    myFixture.addClass """
package org.intellij.lang.annotations;
public @interface MagicConstant {
  String[] stringValues() default {};
  Class flagsFromClass() default void.class;
}
"""
  }
}
