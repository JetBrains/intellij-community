// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import org.intellij.lang.annotations.Language;

public class PsiTreeUtilTest extends LightJavaCodeInsightTestCase {
  public void testFindCommonParentWhenOneElementIsInjectedMustNotReturnFile() {
    @Language("JAVA")
    String text = "class S { String s= \"\".replaceAll(\"[<caret>]\", \"\"); }";
    configureFromFileText("x.java", text);
    InjectedLanguageManager ilm = InjectedLanguageManager.getInstance(getProject());
    assertTrue(ilm.isInjectedFragment(getFile()));
    PsiElement element = getFile().findElementAt(getEditor().getCaretModel().getOffset());
    assertNotNull(element);
    PsiFile file = ilm.getTopLevelFile(element);
    PsiElement parent = PsiTreeUtil.findCommonParent(element, file);
    assertNull(parent);
  }

}
