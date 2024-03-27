// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.EnumValuesInspection;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.siyeh.ig.LightJavaInspectionTestCase;

public class EnumValuesInspectionTest extends LightJavaInspectionTestCase {
  public static final String TEST_DATA_DIR = "/inspection/enumValuesCall/";

  public void testEnumValuesCall() {
    doTest();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new EnumValuesInspection();
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + TEST_DATA_DIR;
  }
}
