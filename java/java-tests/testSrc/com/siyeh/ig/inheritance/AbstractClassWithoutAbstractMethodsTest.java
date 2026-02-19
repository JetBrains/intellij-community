// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.inheritance;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class AbstractClassWithoutAbstractMethodsTest extends LightJavaInspectionTestCase {

  public void testAbstractClassWithoutAbstractMethods() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final AbstractClassWithoutAbstractMethodsInspection inspection = new AbstractClassWithoutAbstractMethodsInspection();
    inspection.ignoreUtilityClasses = true;
    return inspection;
  }
}