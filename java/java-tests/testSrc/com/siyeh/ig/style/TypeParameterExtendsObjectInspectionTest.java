// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.java.codeInsight.JSpecifyTestUtil;
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

  public void testTypeParameterExtendsObjectWithContainerAnnotation() {
    final TypeParameterExtendsObjectInspection inspection = getTypeParameterExtendsObjectInspection(true);
    myFixture.enableInspections(inspection);
    JSpecifyTestUtil.addJSpecifyNullMarked(myFixture);

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