// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.statementParsing;

import com.intellij.java.parser.AbstractBasicJavaParsingTestCase;
import com.intellij.java.parser.AbstractBasicJavaParsingTestConfigurator;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class AbstractBasicIfParsingTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicIfParsingTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-full/statementParsing/if", configurator);
  }

  @Test
  public void testNormalWithElse() { doTest(true); }
  @Test
  public void testNormalNoElse() { doTest(true); }
  @Test
  public void testUncomplete1() { doTest(true); }
  @Test
  public void testUncomplete2() { doTest(true); }
  @Test
  public void testUncomplete3() { doTest(true); }
  @Test
  public void testUncomplete4() { doTest(true); }
  @Test
  public void testUncomplete5() { doTest(true); }
  @Test
  public void testUncomplete6() { doTest(true); }
  @Test
  public void testUncomplete7() { doTest(true); }

  @Test
  public void testBigIf() {
    String name = getTestName();
    PsiFile file = createPsiFile(name, loadFile(name + "." + getFileExt()));
    var visitor = new PsiRecursiveElementWalkingVisitor() {
      int count = 0;
      @Override
      public void visitElement(@NotNull PsiElement element) {
        // visit all elements to make sure the file is parsed, because createPsiFile() is lazy
        super.visitElement(element);
        count++;
      }
    };
    ReadAction.runBlocking(() -> file.accept(visitor));
    // psi tree is too big and too deeply nested to fit a debug string into memory
    assertEquals(46946, visitor.count);
  }
}