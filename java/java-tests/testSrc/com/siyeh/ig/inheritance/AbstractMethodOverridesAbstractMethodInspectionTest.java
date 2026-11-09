/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.ig.inheritance;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class AbstractMethodOverridesAbstractMethodInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection());

  @Test
  public void testAbstractMethodOverridesAbstractMethod() {
    inspectionFixture.get().doTest();
  }
  
  @Test
  public void testFix() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    myFixture.addClass("""
      /**
       * @see Sub#run
       */
      abstract class Super {
        abstract void run();
      }
      """);
    myFixture.configureByText("Sub.java", """
      /**
       * @see Sub#run
       */
      abstract class Sub extends Super {
        @Override abstract void run<caret>();
      }
      """);
    IntentionAction intention = myFixture.findSingleIntention("Remove redundant abstract method declaration");
    assertEquals(
      """
        /**
         * @see Super#run
         */
        abstract class Sub extends Super {
        }

        ----------
        /**
         * @see Super#run
         */
        abstract class Super {
          abstract void run();
        }
        """, myFixture.getIntentionPreviewText(intention));
    myFixture.launchAction(intention);
    myFixture.checkResult("""
        /**
         * @see Super#run
         */
        abstract class Sub extends Super {
        }
        """);
  }
  
  @Test
  public void testFixQualifiedCall() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    myFixture.configureByText("Test.java", """
      class Test {
        interface Super {
          void charInput();
        }
      
        interface Sub extends Super {
          @Override
          void cha<caret>rInput();
        }
      
        void use(Sub sub) {
          sub.charInput();
        }
      }
      """);
    IntentionAction intention = myFixture.findSingleIntention("Remove redundant abstract method declaration");
    myFixture.launchAction(intention);
    myFixture.checkResult("""
      class Test {
        interface Super {
          void charInput();
        }
      
        interface Sub extends Super {
        }
      
        void use(Sub sub) {
          sub.charInput();
        }
      }
      """);
  }

  @Nullable
  private static InspectionProfileEntry getInspection() {
    final AbstractMethodOverridesAbstractMethodInspection inspection = new AbstractMethodOverridesAbstractMethodInspection();
    inspection.ignoreJavaDoc = true;
    return inspection;
  }
}
