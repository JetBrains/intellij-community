// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInspection.javaDoc.JavadocLinkAsPlainTextInspection;
import com.intellij.openapi.vcs.IssueNavigationConfiguration;
import com.intellij.openapi.vcs.IssueNavigationLink;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class JavadocLinkAsPlainTextInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture, () -> List.of(new JavadocLinkAsPlainTextInspection()));

  @NotNull
  private String getFilePath() {
    return "/inspection/javadocLinkAsPlainText/" + daemonFixture.get().getFixture().getTestName(false) + ".java";
  }

  private void doTest() {
    daemonFixture.get().doTest(getFilePath(), true, false);
  }

  @Test
  public void testLeadingAsterisks() {
    doTest();
  }

  @Test
  public void testNoLeadingAsterisks() {
    doTest();
  }

  @Test
  public void testIssueLinksInJavaDoc() {
    IssueNavigationConfiguration configuration = IssueNavigationConfiguration.getInstance(daemonFixture.get().getFixture().getProject());
    List<IssueNavigationLink> oldLinks = configuration.getLinks();
    try {
      configuration.setLinks(List.of(new IssueNavigationLink("IDEA-\\d+", "https://youtrack.jetbrains.com/issue/$0")));
      daemonFixture.get().configureByFile(getFilePath());
      daemonFixture.get().doTestConfiguredFile(true, false, null);
    }
    finally {
      configuration.setLinks(oldLinks);
    }
  }

  @Test
  public void testMarkdownElements() {
    doTest();
  }
}
