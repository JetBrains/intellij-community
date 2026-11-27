// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.style;

import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.style.MultipleVariablesInDeclarationInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class MultipleVariablesInDeclarationFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new MultipleVariablesInDeclarationInspection()), "style/multiple_declaration",
                        InspectionGadgetsBundle.message("normalize.declaration.quickfix"), null, getEnvironmentClasses());

  @Test
  public void testLocalVariable() { ig.get().doTest(); }
  @Test
  public void testField() { ig.get().doTest(); }
  @Test
  public void testMultipleDeclarationsLocalWithComments() { ig.get().doTest(); }
  @Test
  public void testMutuallyDependentForDeclarations() { ig.get().doTest(); }
  @Test
  public void testOptimizedForLoop() { ig.get().doTest(); }
  @Test
  public void testAnnotatedArrayField() { ig.get().doTest(); }
  @Test
  public void testAnnotatedArrayLocalVariable() { ig.get().doTest(); }
  @Test
  public void testAnnotatedVariableInForLoop() { ig.get().doTest(); }
  
  @Test
  public void testForLoopInitializer() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    JavaCodeStyleSettings customSettings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
    customSettings.GENERATE_FINAL_LOCALS = true;
    ig.get().doTest();
  }

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
        }""",
      """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        import java.lang.annotation.Target;
        
        @Retention(RetentionPolicy.CLASS)
        @Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE})
        public @interface Preliminary {
        }"""
    };
  }
}
