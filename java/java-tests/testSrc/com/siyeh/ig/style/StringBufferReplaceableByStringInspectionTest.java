// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.JavaCodeFragmentFactory;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StringBufferReplaceableByStringInspectionTest extends LightJavaInspectionTestCase {

  public void testStringBufferReplaceableByString() {
    doTest();
  }

  public void testFragment() {
    String text = """
      StringBuilder <warning descr="'StringBuilder sb' can be replaced with 'String'">sb</warning> = new StringBuilder();
      sb.append("foo");
      System.out.println(sb.toString());""";
    JavaCodeFragment fragment = JavaCodeFragmentFactory.getInstance(getProject()).createCodeBlockCodeFragment(text, null, true);
    myFixture.configureFromExistingVirtualFile(fragment.getVirtualFile());
    myFixture.testHighlighting(true, false, false);
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new StringBufferReplaceableByStringInspection();
  }
}