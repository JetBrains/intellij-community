// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.structuralsearch.inspection;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.inspection.SSBasedInspection;
import com.intellij.structuralsearch.inspection.StructuralSearchProfileActionProvider;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchConfiguration;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_1_7;

/**
 * @author Bas Leijdekkers
 */
public abstract class SSBasedInspectionTestCase extends UsefulTestCase {
  protected CodeInsightTestFixture myFixture;
  private SSBasedInspection myInspection;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    final TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder =
      factory.createLightFixtureBuilder(JAVA_1_7, getTestName(false));
    final IdeaProjectTestFixture fixture = fixtureBuilder.getFixture();
    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture, new LightTempDirTestFixtureImpl(true));
    myInspection = new SSBasedInspection();
    myFixture.setUp();
    myFixture.enableInspections(myInspection);
    myFixture.setTestDataPath(getTestDataPath());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myFixture.tearDown();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myFixture = null;
      myInspection = null;
      super.tearDown();
    }
  }

  protected void doTest(JavaFileType fileType, String searchPattern, String patternName) {
    doTest(fileType, searchPattern, patternName, null);
  }

  protected void doTest(JavaFileType fileType, String search, String name, String replacement) {
    final Configuration configuration = replacement == null ? new SearchConfiguration() : new ReplaceConfiguration();
    configuration.setName(name);

    final MatchOptions matchOptions = configuration.getMatchOptions();
    matchOptions.setFileType(fileType);
    matchOptions.fillSearchCriteria(search);
    if (replacement != null) {
      configuration.getReplaceOptions().setReplacement(replacement);
    }

    inspectionTest(configuration, null);
  }

  protected void inspectionTest(Configuration configuration, @Nullable HighlightDisplayLevel level) {
    Project project = myFixture.getProject();
    InspectionProfileImpl profile = InspectionProfileManager.getInstance(project).getCurrentProfile();
    StructuralSearchProfileActionProvider.createNewInspection(configuration, project, profile);
    if (level != null) {
      final String shortName = configuration.getUuid();
      HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
      profile.setErrorLevel(key, level, project);
    }
    myFixture.testHighlighting(true, false, false, getTestName(false) + getExtension());
    if (configuration instanceof ReplaceConfiguration rc) {
      final String replacement = rc.getReplaceOptions().getReplacement();
      String intentionName = CommonQuickFixBundle.message("fix.replace.with.x", replacement);
      quickFixTest(intentionName);
    }
  }

  protected void quickFixTest(String intentionName) {
    final IntentionAction intention = myFixture.getAvailableIntention(intentionName);
    assertNotNull(intention);
    myFixture.checkPreviewAndLaunchAction(intention);
    myFixture.checkResultByFile(getTestName(false) + ".after" + getExtension());
  }

  @NotNull
  protected abstract String getExtension();

  protected abstract String getTestDataPath();
}
