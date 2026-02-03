// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SortedCollectionWithNonComparableKeysInspectionTest extends LightJavaInspectionTestCase {

  public void testSortedCollectionWithNonComparableKeys() {
    doTest();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    SortedCollectionWithNonComparableKeysInspection inspection = new SortedCollectionWithNonComparableKeysInspection();
    inspection.IGNORE_TYPE_PARAMETERS = true;
    return inspection;
  }
}