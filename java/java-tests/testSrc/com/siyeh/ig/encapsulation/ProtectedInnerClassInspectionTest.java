// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.encapsulation;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class ProtectedInnerClassInspectionTest extends LightJavaInspectionTestCase {

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final ProtectedInnerClassInspection tool = new ProtectedInnerClassInspection();
    tool.ignoreEnums = true;
    tool.ignoreInterfaces = true;
    return tool;
  }

  public void testProtectedInnerClass() {
    doTest();
  }

  public void testPreviewStaticClass() {
    myFixture.configureByText("Outer.java", "public class Outer { protected static class N<caret>ested {} }");
    IntentionAction action = myFixture.findSingleIntention("Move class");
    myFixture.checkIntentionPreviewHtml(action, "Move inner class 'Nested' to the top level of a package of your choice or to an another class.");
  }

  public void testPreviewNonStaticClass() {
    myFixture.configureByText("Outer.java", "public class Outer { protected class In<caret>ner {} }");
    IntentionAction action = myFixture.findSingleIntention("Move class");
    myFixture.checkIntentionPreviewHtml(action, "Move inner class 'Inner' to the top level of a package of your choice.");
  }
}
