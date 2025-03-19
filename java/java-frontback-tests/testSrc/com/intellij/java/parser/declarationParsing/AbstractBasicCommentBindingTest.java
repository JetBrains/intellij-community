// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.declarationParsing;

import com.intellij.java.parser.AbstractBasicJavaParsingTestCase;
import com.intellij.java.parser.AbstractBasicJavaParsingTestConfigurator;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractBasicCommentBindingTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicCommentBindingTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-full/declarationParsing/commentBinding", configurator);
  }

  public void testBindBefore1() { doTest(true); }
  public void testBindBefore2() { doTest(true); }
  public void testBindBefore3() { doTest(true); }
  public void testBindBefore3a() { doTest(true); }
  public void testBindBefore4() { doTest(true); }
  public void testBindBefore5() { doTest(true); }

  public void testBindBeforeClass1() { doTest(true); }
  public void testBindBeforeClass2() { doTest(true); }
  public void testBindBeforeClass3() { doTest(true); }
  public void testBindBeforeClass4() { doTest(true); }
  public void testBindBeforeClass5() { doTest(true); }
  public void testBindBeforeClass6() { doTest(true); }
}