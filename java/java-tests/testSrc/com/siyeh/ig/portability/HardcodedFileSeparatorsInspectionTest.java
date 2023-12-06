// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.portability;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class HardcodedFileSeparatorsInspectionTest extends LightJavaInspectionTestCase {

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package java.net;" +
      "public final class URL implements java.io.Serializable { " +
      "public URL(String spec) throws MalformedURLException { " +
      " this(null, spec);" +
      "}}",

      "package java.net;" +
      "public final class URI implements java.io.Serializable { " +
      "public URI(String str) throws URISyntaxException { " +
      " new Parser(str).parse(false);" +
      "}}",
    };
  }

  public void testHardcodedFileSeparators() {
    doTest();
  }

  public void testNoCrashOnUnclosedLiteral() { doTest();}

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new HardcodedFileSeparatorsInspection();
  }
}