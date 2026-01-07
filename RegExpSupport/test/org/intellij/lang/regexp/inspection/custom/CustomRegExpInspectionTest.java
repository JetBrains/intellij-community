// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.inspection.custom;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.find.FindModel.SearchContext;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * @author Bas Leijdekkers
 */
public class CustomRegExpInspectionTest extends BasePlatformTestCase {

  public void testMultipleCustomInspections() {
    myFixture.enableInspections(new CustomRegExpInspection());
    Project project = myFixture.getProject();
    InspectionProfileImpl profile = InspectionProfileManager.getInstance(project).getCurrentProfile();
    CustomRegExpInspection inspection = CustomRegExpInspection.getCustomRegExpInspection(profile);
    RegExpInspectionConfiguration one = new RegExpInspectionConfiguration("one");
    one.addPattern(new RegExpInspectionConfiguration.InspectionPattern("one", null, 0, SearchContext.ANY, null));
    inspection.addConfiguration(one);
    CustomRegExpInspection.addInspectionToProfile(project, profile, one);
    RegExpInspectionConfiguration two = new RegExpInspectionConfiguration("two");
    two.addPattern(new RegExpInspectionConfiguration.InspectionPattern("two", null, 0, SearchContext.ANY, null));
    inspection.addConfiguration(two);
    // configuration "two" is not yet added to the inspection profile
    myFixture.configureByText("dummy.txt", """
      <warning descr="one">one</warning>
      two
      three
      """);
    myFixture.testHighlighting(true, true, true);

    CustomRegExpInspection.addInspectionToProfile(project, profile, two);
    myFixture.configureByText("dummy.txt", """
      <warning descr="one">one</warning>
      <warning descr="two">two</warning>
      three
      """);
    myFixture.testHighlighting(true, true, true);
  }
}