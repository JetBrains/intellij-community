// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class FieldMayBeStaticInspectionTest extends LightJavaInspectionTestCase {

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_11;
  }

  public void testFieldMayBeStatic() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_11, this::doTest);
  }
  public void testFieldMayBeStaticJava17() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_17, this::doTest);
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new FieldMayBeStaticInspection();
  }
}
