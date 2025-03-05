// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethodCallExpression;
import org.jetbrains.annotations.NotNull;

public class RecursiveVisitorTest extends LightDaemonAnalyzerTestCase {
  public void testStopWalking() {
    PsiJavaFile file = (PsiJavaFile)createFile("Test.java", """
      class Test {
        Test() {
          super();
          super();
          {
            super();
          }
        }
      }""");
    int[] count = {0};
    PsiCodeBlock body = file.getClasses()[0].getMethods()[0].getBody();
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
