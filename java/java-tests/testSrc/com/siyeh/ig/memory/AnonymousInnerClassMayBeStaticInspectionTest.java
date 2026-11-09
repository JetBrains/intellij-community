/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.memory;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class AnonymousInnerClassMayBeStaticInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_17);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new AnonymousInnerClassMayBeStaticInspection());

  @Test
  public void testAnonymousInnerClassMayBeStatic() { inspectionFixture.get().doTest(); }

  @Test
  public void testUnresolvedReference() { inspectionFixture.get().doTest(); }

  @Test
  public void testAnonymousInnerClassMayBeStaticInsideInterface() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    inspectionFixture.get().doTest();
    String message = InspectionGadgetsBundle.message("anonymous.inner.may.be.named.static.inner.class.quickfix");
    final IntentionAction intention = myFixture.getAvailableIntention(message);
    assertNotNull(intention);
    String text = myFixture.getIntentionPreviewText(intention);
    assertEquals("""
                   interface A {
                       default void sample() {
                           Thread thread = new Thread(new MyRunnable());
                       }
                   
                       class MyRunnable implements Runnable {
                           @Override
                           public void run() {
                           }
                       }
                   }""", text);
  }
}
