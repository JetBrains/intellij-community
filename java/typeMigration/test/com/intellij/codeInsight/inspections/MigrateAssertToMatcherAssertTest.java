/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.inspections;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.refactoring.typeMigration.inspections.MigrateAssertToMatcherAssertInspection;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.ArrayUtil;

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
    moduleBuilder.addLibrary("test-env", ArrayUtil.toStringArray(IntelliJProjectConfiguration.getProjectLibraryClassesRootPaths("JUnit4")));
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
