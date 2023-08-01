// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.serialization;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MissingSerialAnnotationInspectionJava14Test extends LightJavaInspectionTestCase {

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST_WITH_LATEST_JDK;
  }

  public void testSerializableClassInJava14Positive() {
    doTest();
  }

  public void testSerializableClassInJava14Negative() {
    doTest();
  }

  public void testExternalizableClassInJava14Positive() {
    doTest();
  }

  public void testExternalizableClassInJava14Negative() {
    doTest();
  }

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new MissingSerialAnnotationInspection();
  }
}
