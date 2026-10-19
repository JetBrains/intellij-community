// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser;

import com.intellij.JavaTestUtil;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixture;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixtureKt.parsingFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;

public abstract class JavaParsingTestCase {
  private static final TestFixture<Project> projectFixture = projectFixture();

  protected final TestFixture<ParsingTestFixture> parsingFixture;
  private LanguageLevel myLanguageLevel;

  public JavaParsingTestCase(String dataPath, String fileExt, ParserDefinition... parserDefinitions) {
    parsingFixture = parsingFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/" + "psi/" + dataPath, fileExt, false,
                                    parserDefinitions);
  }

  @BeforeEach
  protected void setUpLanguageLevel() {
    myLanguageLevel = JavaTestUtil.getMaxRegisteredLanguageLevel();
  }

  protected PsiFile createFile(@NotNull String name, @NotNull String text) {
    PsiFile file = parsingFixture.get().createFile(name, text);
    file.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, myLanguageLevel);
    return file;
  }

  protected void doTest(boolean checkResult, boolean ensureNoErrorElements) {
    ParsingTestFixture fixture = parsingFixture.get();
    String name = fixture.getTestName();
    String text = fixture.loadFile(name + "." + fixture.getFileExt());
    PsiFile file = fixture.checkParsedFile(createFile(name + "." + fixture.getFileExt(), text), text);
    if (checkResult) {
      fixture.checkResult(name, file);
      if (ensureNoErrorElements) {
        fixture.ensureNoErrorElements();
      }
    }
    else {
      ReadAction.computeBlocking(
        () -> ParsingTestFixture.toParseTreeText(file, fixture.getSkipSpaces(), fixture.getIncludeRanges()));
    }
  }

  protected void setLanguageLevel(@NotNull LanguageLevel languageLevel) {
    myLanguageLevel = languageLevel;
  }
}