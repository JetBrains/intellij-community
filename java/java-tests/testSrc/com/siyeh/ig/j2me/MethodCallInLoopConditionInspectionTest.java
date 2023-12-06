// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.j2me;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class MethodCallInLoopConditionInspectionTest extends LightJavaInspectionTestCase {

  public void testMethodCallInLoopCondition() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new MethodCallInLoopConditionInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package java.lang.ref;" +
      "public class ReferenceQueue<T> {" +
      "  public Reference<? extends T> poll() {" +
      "    return null;" +
      "  }" +
      "}"
    };
  }
}