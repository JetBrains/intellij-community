// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight;

import com.intellij.testFramework.TestIndexingModeSupporter;
import junit.framework.Test;
import junit.framework.TestSuite;

@SuppressWarnings("NewClassNamingConvention") // to run on TeamCity
public class DumbParameterInfoTest {
  public static Test suite() {
    TestSuite suite = new TestSuite();
    TestIndexingModeSupporter.addTest(ParameterInfoTest.class, new TestIndexingModeSupporter.FullIndexSuite(), suite);
    TestIndexingModeSupporter.addTest(ParameterInfoTest.class, new TestIndexingModeSupporter.RuntimeOnlyIndexSuite(), suite);
    TestIndexingModeSupporter.addTest(ParameterInfoTest.class, new TestIndexingModeSupporter.EmptyIndexSuite(), suite);
    return suite;
  }

}
