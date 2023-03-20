// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.intention;

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
}
