// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE.txt file.
package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class TypeParameterExtendsObjectInspectionTest extends LightJavaInspectionTestCase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  public void testTypeParameterExtendsObject() {
    final TypeParameterExtendsObjectInspection inspection = getTypeParameterExtendsObjectInspection(false);
    myFixture.enableInspections(inspection);

    doTest();
  }

  public void testTypeParameterExtendsObjectIgnoreAnnotated() {
    final TypeParameterExtendsObjectInspection inspection = getTypeParameterExtendsObjectInspection(true);
    myFixture.enableInspections(inspection);

    doTest();
  }

  private TypeParameterExtendsObjectInspection getTypeParameterExtendsObjectInspection(boolean ignoreAnnotatedObject) {
    final TypeParameterExtendsObjectInspection inspection = new TypeParameterExtendsObjectInspection();
    inspection.ignoreAnnotatedObject = ignoreAnnotatedObject;
    return inspection;
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return getTypeParameterExtendsObjectInspection(false);
  }
}