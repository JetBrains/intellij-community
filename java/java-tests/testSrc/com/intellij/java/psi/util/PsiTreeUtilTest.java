// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.util;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTreeUtilKt;
import com.intellij.testFramework.EqualsToFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class PsiTreeUtilTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  @Test
  public void testFindCommonParentWhenOneElementIsInjectedMustNotReturnFile() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    @Language("JAVA")
    String text = "class S { String s= \"\".replaceAll(\"[<caret>]\", \"\"); }";
    daemonFixture.get().configureFromFileText("x.java", text);
    ReadAction.runBlocking(() -> {
      InjectedLanguageManager ilm = InjectedLanguageManager.getInstance(myFixture.getProject());
      assertTrue(ilm.isInjectedFragment(myFixture.getFile()));
      PsiElement element = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
      assertNotNull(element);
      PsiFile file = ilm.getTopLevelFile(element);
      PsiElement parent = PsiTreeUtil.findCommonParent(element, file);
      assertNull(parent);
    });
  }

  @Test
  public void testPreorderTraversal() {
    doTraversalTest(false);
  }

  @Test
  public void testPostorderTraversal() {
    doTraversalTest(true);
  }

  private void doTraversalTest(boolean childrenFirst) {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    PsiFile psiFile = daemonFixture.get().configureFromFileText("C.java", "class C { int answer = 42; }");
    StringBuilder result = new StringBuilder();
    ReadAction.runBlocking(() -> {
      Iterator<PsiElement> descendants = PsiTreeUtilKt.descendants(psiFile, childrenFirst, _ -> true).iterator();
      while (descendants.hasNext()) {
        PsiElement next = descendants.next();
        result.append(next.getTextRange()).append(" ").append(next.getNode().getElementType()).append("\n");
      }
    });
    EqualsToFile.assertEqualsToFile(
      "PSI traversal",
      new File(JavaTestUtil.getJavaTestDataPath() + "/util/psiTree/" + myFixture.getTestName(true) + ".txt"),
      result.toString()
    );
  }

  @Test
  public void testProcessElementsForDeepDummyHoldersOrNonPhysicalRootsIteratesInDepthFirstOrderAndDoesNotThrowSOEStress() {
    int N = 30_000;
    ReadAction.runBlocking(() -> {
      // create very deep non-physical PSI
      PsiElementFactory factory = PsiElementFactory.getInstance(daemonFixture.get().getFixture().getProject());
      PsiClass aClass = factory.createClassFromText("", null);
      assertFalse(aClass.isPhysical());
      PsiElement e = aClass;
      PsiMethod method = factory.createMethod("m", PsiTypes.voidType());
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
    });
  }
}
