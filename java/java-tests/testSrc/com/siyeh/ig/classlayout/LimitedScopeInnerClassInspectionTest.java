// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class LimitedScopeInnerClassInspectionTest extends LightJavaInspectionTestCase {

  public void testSimple() {
    doTest("""
             class X {
               void x() {
                 class /*Local class 'Y'*/Y/**/ {}
               }
             }""");
  }

  public void testEnum() {
    doTest("""
             class X {
                 void x() {
                     enum /*Local class 'E'*//*_*/E/**/ {
                        A, B
                     }
                 }
             }""");
    final IntentionAction intention = myFixture.getAvailableIntention(InspectionGadgetsBundle.message("move.local.to.inner.quickfix"));
    assertEquals("""
                   class X {
                       void x() {
                       }
                  
                       private enum E {
                          A, B
                       }
                   }""", myFixture.getIntentionPreviewText(intention));
  }

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new LimitedScopeInnerClassInspection();
  }
}