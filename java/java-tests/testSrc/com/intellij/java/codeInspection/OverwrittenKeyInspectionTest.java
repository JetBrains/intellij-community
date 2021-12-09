// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.OverwrittenKeyInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class OverwrittenKeyInspectionTest extends LightJavaInspectionTestCase {
  public void testOverwrittenKey() {
    myFixture.addClass("package com.google.common.collect;\n" +
                       "public abstract class ImmutableSet<E> implements Set<E> {\n" +
                       "  public static <E> ImmutableSet<E> of(E... e) {\n" +
                       "    throw new UnsupportedOperationException();\n" +
                       "  }\n" +
                       "}");
    doTest();
  }
  public void testOverwrittenKeyArray() {
    doTest();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new OverwrittenKeyInspection();
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/overwrittenKey/";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_9;
  }
}
