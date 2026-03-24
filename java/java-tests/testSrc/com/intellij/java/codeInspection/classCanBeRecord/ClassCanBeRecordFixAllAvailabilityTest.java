// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection.classCanBeRecord;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInspection.classCanBeRecord.ClassCanBeRecordInspection;
import com.intellij.codeInspection.classCanBeRecord.ClassCanBeRecordInspection.ConversionStrategy;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.java.JavaBundle;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/// Regression test for IDEA-386737.
public class ClassCanBeRecordFixAllAvailabilityTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }

  public void testSuppressAnnotationFixHasNoFixAll() {
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
