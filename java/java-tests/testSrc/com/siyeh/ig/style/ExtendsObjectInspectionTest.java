// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

/// @author Bas Leijdekkers
public final class ExtendsObjectInspectionTest extends LightJavaInspectionTestCase {
  @Override
  protected @NotNull InspectionProfileEntry getInspection() {
    return new ExtendsObjectInspection();
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }

  public void testExtendsObject() {
    doTest();
  }
}
