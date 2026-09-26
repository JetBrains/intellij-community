// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethodCallExpression;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

public class RecursiveVisitorTest extends LightDaemonAnalyzerTestCase {
  public void testStopWalking() {
    @Language("JAVA")
    String text = """
      class Test {
        Test() {
          super();
          super();
          {
            super();
          }
        }
      }""";
    PsiJavaFile psiJavaFile = (PsiJavaFile)createFile("Test.java", text);
    int[] count = {0};
    PsiCodeBlock body = psiJavaFile.getClasses()[0].getMethods()[0].getBody();
    body.acceptChildren(new JavaRecursiveElementWalkingVisitor() {

      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
        count[0]++;
        stopWalking();
      }
    });
    assertEquals(1, count[0]);
  }
}
