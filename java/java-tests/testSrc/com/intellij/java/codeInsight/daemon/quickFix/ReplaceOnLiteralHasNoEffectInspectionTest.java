// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix;

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