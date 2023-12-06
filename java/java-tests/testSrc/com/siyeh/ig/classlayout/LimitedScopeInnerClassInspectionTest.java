// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class LimitedScopeInnerClassInspectionTest extends LightJavaInspectionTestCase {

  public void testSimple() {
    doTest("class X {" +
           "  void x() {" +
           "    class /*Local class 'Y'*/Y/**/ {}" +
           "  }" +
           "}");
  }

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new LimitedScopeInnerClassInspection();
  }
}