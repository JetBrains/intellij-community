// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.threading;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class ExtendsThreadInspectionTest extends LightJavaInspectionTestCase {
  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new ExtendsThreadInspection();
  }

  public void testExtendsThread() {
    doTest();
    String message = InspectionGadgetsBundle.message("replace.inheritance.with.delegation.quickfix");
    final IntentionAction intention = myFixture.getAvailableIntention(message);
    assertNotNull(intention);
    String text = myFixture.getIntentionPreviewText(intention);
    assertEquals("""
                   class MainThread {
                       private final Thread thread = new Thread();
                   }""", text);
  }
}
