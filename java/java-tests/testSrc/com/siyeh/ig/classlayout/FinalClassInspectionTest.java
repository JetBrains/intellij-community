// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class FinalClassInspectionTest extends LightJavaInspectionTestCase {

  public void testSimple() {
    doTest("/*Class 'Main' declared 'final'*/final/**/ class Main {}");
  }

  public void testSealedClass() {
    doTest("final class Sealed extends X {}" +
           "sealed class X {}");
  }
  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new FinalClassInspection();
  }
}
