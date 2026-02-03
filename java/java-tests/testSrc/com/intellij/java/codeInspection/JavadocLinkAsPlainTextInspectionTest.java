// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.javaDoc.JavadocLinkAsPlainTextInspection;
import com.intellij.openapi.vcs.IssueNavigationConfiguration;
import com.intellij.openapi.vcs.IssueNavigationLink;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class JavadocLinkAsPlainTextInspectionTest extends LightDaemonAnalyzerTestCase {
  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new JavadocLinkAsPlainTextInspection()};
  }

  @NotNull
  private String getFilePath() {
    return "/inspection/javadocLinkAsPlainText/" + getTestName(false) + ".java";
  }

  private void doTest() {
    doTest(getFilePath(), true, false);
  }

  public void testLeadingAsterisks() {
    doTest();
  }

  public void testNoLeadingAsterisks() {
    doTest();
  }

  public void testIssueLinksInJavaDoc() {
    IssueNavigationConfiguration configuration = IssueNavigationConfiguration.getInstance(getProject());
    List<IssueNavigationLink> oldLinks = configuration.getLinks();
    try {
      configuration.setLinks(List.of(new IssueNavigationLink("IDEA-\\d+", "https://youtrack.jetbrains.com/issue/$0")));
      configureByFile(getFilePath());
      doTestConfiguredFile(true, false, null);
    }
    finally {
      configuration.setLinks(oldLinks);
    }
  }

  public void testMarkdownLinks() {
    doTest();
  }
}
