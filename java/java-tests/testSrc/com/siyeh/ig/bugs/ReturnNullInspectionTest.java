// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class ReturnNullInspectionTest extends LightJavaInspectionTestCase {

  public void testReturnNull() {
    final NullableNotNullManager nnnManager = NullableNotNullManager.getInstance(getProject());
    nnnManager.setNullables("com.siyeh.igtest.bugs.Nullable");
    Disposer.register(myFixture.getTestRootDisposable(), nnnManager::setNullables);
    doTest();
  }

  public void testWarnOptional() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final ReturnNullInspection inspection = new ReturnNullInspection();
    inspection.m_reportObjectMethods = !"WarnOptional".equals(getTestName(false));
    inspection.m_ignorePrivateMethods = "WarnOptional".equals(getTestName(false));
    return inspection;
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      """
package com.siyeh.igtest.bugs;
import java.lang.annotation.*;
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE_USE, ElementType.METHOD})
public @interface Nullable {}"""
    };
  }
}