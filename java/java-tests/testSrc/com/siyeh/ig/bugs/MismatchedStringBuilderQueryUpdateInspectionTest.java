// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class MismatchedStringBuilderQueryUpdateInspectionTest extends LightJavaInspectionTestCase {

  public void testMismatchedStringBuilderQueryUpdate() {
    doTest();
  }
  public void testRepeatAbstractStringBuilder() {
    myFixture.addClass("""
                         package java.lang;
                        
                         public final class StringBuilder2 extends AbstractStringBuilder {
                             public native StringBuilder2 repeat(CharSequence cs, int count);
                         }
                         """);
    doTest();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new MismatchedStringBuilderQueryUpdateInspection();
  }
}