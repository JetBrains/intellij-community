// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.classlayout;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class PublicConstructorInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/ig/com/siyeh/igtest/classlayout/public_constructor");
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new PublicConstructorInspection());
    myFixture.testHighlighting(myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testPublicAnnotation() {
    doTest();
  }

  @Test
  public void testPublicConstructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testPublicExplicitConstructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testPublicEnum() {
    doTest();
  }

  @Test
  public void testPublicInterface() {
    doTest();
  }

}
