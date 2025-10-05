// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public class RemoveUnusedParameterMainMethodTest extends LightJavaInspectionTestCase {

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_25;
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new UnusedDeclarationInspection(true);
  }

  public void testSimple() {
    myFixture.configureByText("Main.java", """
      void main(String[] args<caret>) { }
      """);
    List<IntentionAction> intentions = myFixture.getAvailableIntentions();
    assertEquals("Safe delete 'args'", intentions.get(0).getText());
  }
}
