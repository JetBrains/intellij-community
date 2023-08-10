// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class PublicConstructorInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return LightJavaInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/classlayout/public_constructor";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  private void doTest() {
    myFixture.enableInspections(new PublicConstructorInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testPublicAnnotation() {
    doTest();
  }

  public void testPublicConstructor() {
    doTest();
    final IntentionAction intention = myFixture.getAvailableIntention(InspectionGadgetsBundle.message("public.constructor.quickfix"));
    assertNotNull(intention);
    String text = myFixture.getIntentionPreviewText(intention);
    assertEquals("""
                   package com.siyeh.igtest.classlayout.public_constructor;
                                      
                   public class PublicConstructor {
                       private PublicConstructor() {
                       }
                                      
                       public static PublicConstructor createPublicConstructor() {
                           return new PublicConstructor();
                       }
                   }
                   abstract class X implements java.io.Externalizable {
                     public X() {}
                   }
                   class Y {
                     public Y() {}
                   }
                   abstract class Z {
                     public Z() {}
                   }""", text);
  }

  public void testPublicExplicitConstructor() {
    doTest();
    final IntentionAction intention = myFixture.getAvailableIntention(InspectionGadgetsBundle.message("public.constructor.quickfix"));
    assertNotNull(intention);
    String text = myFixture.getIntentionPreviewText(intention);
    assertEquals("""
                   package com.siyeh.igtest.classlayout.public_explicit_constructor;
                                      
                   public class PublicExplicitConstructor {
                     private PublicExplicitConstructor() {}
                                      
                       public static PublicExplicitConstructor createPublicExplicitConstructor() {
                           return new PublicExplicitConstructor();
                       }
                   }""", text);
  }

  public void testPublicEnum() {
    doTest();
  }

  public void testPublicInterface() {
    doTest();
  }

}
