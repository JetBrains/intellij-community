// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.intention;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class AddJavadocToModuleAndPackageTest extends LightJavaCodeInsightFixtureTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_9;
  }

  public void testPackageInfo() {
    myFixture.configureByText("package-info.java", "package org.some.awe<caret>some;");
    myFixture.launchAction(myFixture.findSingleIntention("Add Javadoc"));
    myFixture.checkResult("""
                            /**
                             *\s
                             */
                            package org.some.awesome;""");
  }

  public void testModuleInfo() {
    myFixture.configureByText("module-info.java", "module org.some.awe<caret>some{}");
    myFixture.launchAction(myFixture.findSingleIntention("Add Javadoc"));
    myFixture.checkResult("""
                            /**
                             *\s
                             */
                            module org.some.awesome{}""");
  }

  public void testPackageInfoMarkdown() {
    myFixture.configureByText("package-info.java", "package org.some.awe<caret>some;");
    myFixture.launchAction(myFixture.findSingleIntention("Add Javadoc"));
    myFixture.checkResult("""
                            /// <caret>
                            package org.some.awesome;""");
  }

  public void testModuleInfoMarkdown() {
    myFixture.configureByText("module-info.java", "module org.some.awe<caret>some{}");
    myFixture.launchAction(myFixture.findSingleIntention("Add Javadoc"));
    myFixture.checkResult("""
                            /// <caret>
                            module org.some.awesome{}""");
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    if (getQualifiedTestMethodName().endsWith("Markdown")) {
      CodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject());
      settings.getCommonSettings(JavaLanguage.INSTANCE).DOCUMENTATION_LINE_COMMENT_PREFERRED = true;
      CodeStyle.setTemporarySettings(getProject(), settings);
    }
  }
}
