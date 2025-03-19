// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;

public class SimplifiableAnnotationInspectionTest extends LightJavaInspectionTestCase {

  public void testSimplifiableAnnotation() {
    doTest();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new SimplifiableAnnotationInspection();
  }
}