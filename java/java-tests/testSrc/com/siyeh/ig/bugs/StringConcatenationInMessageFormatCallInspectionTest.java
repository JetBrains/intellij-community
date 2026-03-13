// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class StringConcatenationInMessageFormatCallInspectionTest extends LightJavaInspectionTestCase {

  public void testStringConcatenationInMessageFormatCall() {
    doTest();
    checkQuickFixAll();
  }

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new StringConcatenationInMessageFormatCallInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      """
      package java.text;
      public final class MessageFormat {
        public static String format(String format, Object... params);
      }"""
    };
  }
}