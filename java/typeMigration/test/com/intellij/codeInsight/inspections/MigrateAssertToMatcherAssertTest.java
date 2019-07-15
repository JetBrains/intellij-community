// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.inspections;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.refactoring.typeMigration.inspections.MigrateAssertToMatcherAssertInspection;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.ArrayUtilRt;

/**
 * @author Dmitry Batkovich
 */
public class MigrateAssertToMatcherAssertTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath()  {
    return PlatformTestUtil.getCommunityPath() + "/java/typeMigration/testData/inspections/migrateAssertsToAssertThat";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addLibrary("test-env",
                             ArrayUtilRt.toStringArray(IntelliJProjectConfiguration.getProjectLibraryClassesRootPaths("JUnit4")));
  }

  public void testAll() {
    myFixture.configureByFile(getTestName(true) + ".java");
    myFixture.enableInspections(new MigrateAssertToMatcherAssertInspection());
    for (IntentionAction wrapper : myFixture.getAllQuickFixes()) {
      if (wrapper instanceof QuickFixWrapper) {
        final LocalQuickFix fix = ((QuickFixWrapper)wrapper).getFix();
        if (fix instanceof MigrateAssertToMatcherAssertInspection.MyQuickFix) {
          myFixture.launchAction(wrapper);
        }
      }
    }
    myFixture.checkResultByFile(getTestName(true) + "_after.java");
  }

}
