// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public final class InstantiationOfUtilityClassInspectionTest extends LightJavaInspectionTestCase {

  public void testRecord() {
    doTest("""
        class X {
          record R() {
            static boolean isUtility() {
              return false;
            }
          }
          
          void foo() {
            final R r = new  R();
          }
        }
        """);
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new InstantiationOfUtilityClassInspection();
  }
}
