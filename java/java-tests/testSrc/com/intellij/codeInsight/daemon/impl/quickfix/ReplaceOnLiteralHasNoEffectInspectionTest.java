// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import com.siyeh.ig.redundancy.ReplaceOnLiteralHasNoEffectInspection;
import org.jetbrains.annotations.Nullable;

public class ReplaceOnLiteralHasNoEffectInspectionTest extends LightJavaInspectionTestCase {
  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ReplaceOnLiteralHasNoEffectInspection();
  }

  public void testReplaceOnLiteral() {doTest();}

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/replaceOnLiteral/";
  }
}