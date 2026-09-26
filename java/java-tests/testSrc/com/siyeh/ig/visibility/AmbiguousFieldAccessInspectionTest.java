// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.visibility;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class AmbiguousFieldAccessInspectionTest extends LightJavaInspectionTestCase {

  public void testAmbiguousFieldAccess() { doTest(); }
  public void testStaticallyImported() { doTest(); }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new AmbiguousFieldAccessInspection();
  }
}