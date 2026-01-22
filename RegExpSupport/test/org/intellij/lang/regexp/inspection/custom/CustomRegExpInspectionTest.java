// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.inspection.custom;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.find.FindModel.SearchContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.testFramework.InspectionTestUtil;
import com.intellij.testFramework.InspectionsKt;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.fixtures.impl.GlobalInspectionContextForTests;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class CustomRegExpInspectionTest extends BasePlatformTestCase {

  public void testMultipleCustomInspections() {
    myFixture.enableInspections(CustomRegExpInspection.class);
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

  public void testBatchInspectionTree() {
    myFixture.enableInspections(CustomRegExpInspection.class);
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
    CustomRegExpInspection.addInspectionToProfile(project, profile, two);

    @NotNull InspectionToolWrapper<?, ?> toolWrapper = new LocalInspectionToolWrapper(inspection);
    List<LocalInspectionToolWrapper> children = inspection.getChildren();
    String path = "RegExpSupport/testData/inspections/custom";
    VirtualFile sourceDir = myFixture.copyDirectoryToProject(new File(path, "src").getPath(), "");
    AnalysisScope scope = new AnalysisScope(myFixture.getPsiManager().findDirectory(sourceDir));
    GlobalInspectionContextForTests context = InspectionsKt.createGlobalContextForTool(scope, myFixture.getProject(), List.of(toolWrapper));
    InspectionTestUtil.runTool(toolWrapper, scope, context);
    InspectionTestUtil.compareToolResults(context, false, new File(myFixture.getTestDataPath(), path).getPath(), 
                                          List.of(children.get(0), children.get(1))); // check results come from children
  }

  @Override
  protected boolean isCommunity() {
    return true;
  }
}