// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.statementParsing;

import com.intellij.java.parser.AbstractBasicJavaParsingTestCase;
import com.intellij.java.parser.AbstractBasicJavaParsingTestConfigurator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public abstract class AbstractBasicIfParsingTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicIfParsingTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-full/statementParsing/if", configurator);
  }

  public void testNormalWithElse() { doTest(true); }
  public void testNormalNoElse() { doTest(true); }
  public void testUncomplete1() { doTest(true); }
  public void testUncomplete2() { doTest(true); }
  public void testUncomplete3() { doTest(true); }
  public void testUncomplete4() { doTest(true); }
  public void testUncomplete5() { doTest(true); }
  public void testUncomplete6() { doTest(true); }
  public void testUncomplete7() { doTest(true); }

  public void testBigIf() throws IOException {
    String name = getTestName();
    PsiFile file = createPsiFile(name, loadFile(name + "." + myFileExt));
    var visitor = new PsiRecursiveElementWalkingVisitor() {
      int count = 0;
      @Override
      public void visitElement(@NotNull PsiElement element) {
        // visit all elements to make sure the file is parsed, because createPsiFile() is lazy
        super.visitElement(element);
        count++;
      }
    };
    file.accept(visitor);
    // psi tree is too big and too deeply nested to fit a debug string into memory
    assertEquals(46946, visitor.count);
  }
}