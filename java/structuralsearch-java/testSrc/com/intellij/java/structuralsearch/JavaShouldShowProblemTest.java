// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.structuralsearch;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.JavaCodeFragmentFactory;
import com.intellij.structuralsearch.JavaStructuralSearchProfile;
import com.intellij.structuralsearch.plugin.ui.StructuralSearchDialogKeys;
import com.intellij.structuralsearch.plugin.ui.StructuralSearchHighlightInfoFilter;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @see StructuralSearchHighlightInfoFilter
 * @see JavaStructuralSearchProfile#shouldShowProblem(com.intellij.codeInsight.daemon.impl.HighlightInfo, com.intellij.psi.PsiFile, com.intellij.structuralsearch.PatternContext)
 * @author Bas Leijdekkers
 */
public class JavaShouldShowProblemTest extends LightJavaCodeInsightFixtureTestCase {

  public void testSimpleError() {
    doTest("class A {<EOLError descr=\"'}' expected\"></EOLError>");
  }

  public void testClassContent() {
    doTest("class $X$ { $Content$ }");
  }

  public void testNakedTry() {
    doTest("try { $st$; }");
  }

  public void testSymbol() {
    doTest("BigDecimal");
  }

  public void testAnnotation() {
    doTest("@SuppressWarnings");
  }

  public void testType() {
    doTest("List<String>");
  }

  public void testUnexpectedToken() {
    doTest("java.math.BigDecimal<error descr=\"Unexpected token\">)</error>");
  }

  private void doTest(@NotNull String code) {
    doTest(code, "");
  }

  private void doTest(@NotNull String code, String contextId) {
    final JavaCodeFragment fragment = JavaCodeFragmentFactory.getInstance(getProject()).createCodeBlockCodeFragment(code, null, true);
    myFixture.configureFromExistingVirtualFile(fragment.getVirtualFile());
    final Editor editor = myFixture.getEditor();
    final Document document = editor.getDocument();
    document.putUserData(StructuralSearchDialogKeys.STRUCTURAL_SEARCH_PATTERN_CONTEXT_ID, contextId);
    myFixture.testHighlighting(false, false, false);
  }
}
