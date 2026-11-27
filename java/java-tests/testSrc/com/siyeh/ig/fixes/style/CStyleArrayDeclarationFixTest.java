// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.style;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.style.CStyleArrayDeclarationInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class CStyleArrayDeclarationFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new CStyleArrayDeclarationInspection()), "style/cstyle_array_declaration",
                        "Fix all 'C-style array declaration' problems in file", builder -> builder.setLanguageLevel(LanguageLevel.HIGHEST),
                        getEnvironmentClasses());

  @Test
  public void testMethod() { ig.get().doTest(); }
  @Test
  public void testLocalVariable() { ig.get().doTest(); }
  @Test
  public void testField() { ig.get().doTest(); }
  @Test
  public void testInForLoop() { ig.get().doTest(); }
  @Test
  public void testMultipleVariablesSingleDeclaration() { ig.get().doTest(); }
  @Test
  public void testMultipleFieldsSingleDeclaration() { ig.get().doTest(); }
  @Test
  public void testParameter() { ig.get().doTest(); }

  private static String[] getEnvironmentClasses() {
    return new String[]{
      """
      import java.lang.annotation.ElementType;
      import java.lang.annotation.Retention;
      import java.lang.annotation.RetentionPolicy;
      import java.lang.annotation.Target;
      
      @Retention(RetentionPolicy.CLASS)
      @Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE})
      public @interface Required {
      }
      """,

      """
      import java.lang.annotation.ElementType;
      import java.lang.annotation.Retention;
      import java.lang.annotation.RetentionPolicy;
      import java.lang.annotation.Target;
      
      @Retention(RetentionPolicy.CLASS)
      @Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE})
      public @interface Preliminary {
      }
      """
    };
  }

}
