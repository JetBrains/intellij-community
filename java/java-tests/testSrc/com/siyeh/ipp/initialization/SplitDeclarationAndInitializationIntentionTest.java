// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.initialization;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @see com.siyeh.ipp.initialization.SplitDeclarationAndInitializationIntention
 * @author Bas Leijdekkers
 */
public class SplitDeclarationAndInitializationIntentionTest extends IPPTestCase {

  public void testArrayInitializer() { doTest(); }
  public void testArray() { doTest(); }
  public void testFieldUsedBeforeInitializer() { doTest(); }
  public void testFieldUsedBeforeInitializer1() { doTest(); }
  public void testMultipleFieldsSingleDeclaration() { doTest(); }
  public void testMultipleFieldsSingleDeclaration2() { doTest(); }
  public void testMultipleFieldsSingleDeclaration3() { doTest(); }
  public void testNotInsideCodeBlock() { doTest(); }
  public void testRecordStaticField() { doTest(); }
  public void testInsideCodeBlock() { assertIntentionNotAvailable(); }
  public void testRecord() { assertIntentionNotAvailable(); }
  public void testImplicitClass() { assertIntentionNotAvailable(); }

  @Override
  protected String getRelativePath() {
    return "initialization";
  }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("split.declaration.and.initialization.intention.name");
  }
}
