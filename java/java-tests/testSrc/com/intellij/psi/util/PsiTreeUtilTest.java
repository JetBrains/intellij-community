// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.JavaTestUtil;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.testFramework.EqualsToFile;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import org.intellij.lang.annotations.Language;

import java.io.File;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

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

  public void testPreorderTraversal() {
    doTraversalTest(false);
  }

  public void testPostorderTraversal() {
    doTraversalTest(true);
  }

  private void doTraversalTest(boolean childrenFirst) {
    configureFromFileText("C.java", "class C { int answer = 42; }");
    StringBuilder result = new StringBuilder();
    Iterator<PsiElement> descendants = PsiTreeUtilKt.descendants(getFile(), childrenFirst, __ -> true).iterator();
    while (descendants.hasNext()) {
      PsiElement next = descendants.next();
      result.append(next.getTextRange()).append(" ").append(next.getNode().getElementType()).append("\n");
    }
    EqualsToFile.assertEqualsToFile(
      "PSI traversal",
      new File(JavaTestUtil.getJavaTestDataPath() + "/util/psiTree/" + getTestName(true) + ".txt"),
      result.toString()
    );
  }

  public void testProcessElementsForDeepDummyHoldersOrNonPhysicalRootsIteratesInDepthFirstOrderAndDoesNotThrowSOEStress() {
    int N = 30_000;
    // create very deep non-physical PSI
    PsiClass aClass = PsiElementFactory.getInstance(getProject()).createClassFromText("", null);
    assertFalse(aClass.isPhysical());
    PsiElement e = aClass;
    PsiMethod method = PsiElementFactory.getInstance(getProject()).createMethod("m", PsiTypes.voidType());
    Key<Integer> num = Key.create("TEST_NUM");
    for (int i = 0; i < N; i++) {
      e = e.add(method);
      e.putUserData(num, i);
    }
    assertFalse(aClass.isPhysical());
    AtomicInteger count = new AtomicInteger();
    boolean res = PsiTreeUtil.processElements(aClass, element -> {
      if (element instanceof PsiMethod) {
        assertEquals(count.get(), element.getUserData(num).intValue());
        count.incrementAndGet();
      }
      return true;
    });
    assertTrue(res);
    assertEquals(N,count.get());
  }
}
