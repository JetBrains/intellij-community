// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection.classCanBeRecord;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInspection.classCanBeRecord.ClassCanBeRecordInspection;
import com.intellij.codeInspection.classCanBeRecord.ClassCanBeRecordInspection.ConversionStrategy;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.java.JavaBundle;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNull;

/// Regression test for IDEA-386737.
@TestApplication
public class ClassCanBeRecordFixAllAvailabilityTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testSuppressAnnotationFixHasNoFixAll() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ClassCanBeRecordInspection inspection = new ClassCanBeRecordInspection(ConversionStrategy.DO_NOT_SUGGEST, true);
    myFixture.enableInspections(inspection);
    myFixture.configureByText("SomeService.java", """
      package my.annotation1;

      @MyAnn
      public class Some<caret>Service {
        private final String name;

        public SomeService(String name) {
          this.name = name;
        }

        String getName() {
          return name;
        }
      }

      @interface MyAnn {
      }
      """);

    IntentionAction suppressFix = myFixture.findSingleIntention(
      JavaBundle.message("class.can.be.record.suppress.conversion.if.annotated.fix.name", "my.annotation1.MyAnn"));
    IntentionAction fixAll = IntentionManager.getInstance().createFixAllIntention(new LocalInspectionToolWrapper(inspection), suppressFix);
    assertNull(fixAll);
  }
}
