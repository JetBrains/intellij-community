// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.javadoc;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class MissingDeprecatedAnnotationInspectionTest extends LightJavaInspectionTestCase {

  public void testMissingDeprecatedAnnotation() {
    doTest();
    checkQuickFixAll();
  }

  public void testModuleInfo() {
    doNamedTest("module-info");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final MissingDeprecatedAnnotationInspection inspection = new MissingDeprecatedAnnotationInspection();
    inspection.warnOnMissingJavadoc = true;
    return inspection;
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_9;
  }
}