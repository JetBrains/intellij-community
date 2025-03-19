// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.ArrayUtilRt;
import one.util.streamex.StreamEx;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author anna
 */
@TestDataPath("$CONTENT_ROOT/../test")
public abstract class IGQuickFixesTestCase extends JavaCodeInsightFixtureTestCase {
  protected String myDefaultHint = null;
  protected String myRelativePath = null;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    for (@Language("JAVA") String environmentClass : getEnvironmentClasses()) {
      myFixture.addClass(environmentClass);
    }
    myFixture.enableInspections(getInspections());
  }

  protected BaseInspection getInspection() {
    return null;
  }

  protected BaseInspection[] getInspections() {
    final BaseInspection inspection = getInspection();
    return inspection != null ? new BaseInspection[]{inspection} : new BaseInspection[0];
  }

  @Language("JAVA")
  protected String[] getEnvironmentClasses() {
    return ArrayUtilRt.EMPTY_STRING_ARRAY;
  }

  @Override
  protected void tuneFixture(final JavaModuleFixtureBuilder builder) throws Exception {
    builder.setLanguageLevel(LanguageLevel.JDK_1_8);
  }

  @Override
  protected String getTestDataPath() {
    return PathManager.getCommunityHomePath() + "/java/java-tests/testData/ig/com/siyeh/igfixes/";
  }

  protected void assertQuickfixNotAvailable() {
    assertQuickfixNotAvailable(myDefaultHint);
  }

  protected void assertQuickfixNotAvailable(final String quickfixName) {
    final String testName = getTestName(false);
    myFixture.configureByFile(getRelativePath() + "/" + testName + ".java");
    assertEmpty("Quickfix '" + quickfixName + "' is available but should not",
                myFixture.filterAvailableIntentions(quickfixName));
  }

  protected void assertQuickfixNotAvailable(String quickfixName, @Language("JAVA") @NotNull String text) {
    text = text.replace("/**/", "<caret>");
    myFixture.configureByText(JavaFileType.INSTANCE, text);
    assertEmpty("Quickfix '" + quickfixName + "' is available but should not",
                myFixture.filterAvailableIntentions(quickfixName));
  }

  protected void doTest() {
    if (myDefaultHint == null) throw new NullPointerException("myDefaultHint");
    final String testName = getTestName(false);
    doTest(testName, myDefaultHint);
  }

  protected void doTest(String hint) {
    final String testName = getTestName(false);
    doTest(testName, hint);
  }

  protected void doTest(final String testName, final String hint) {
    myFixture.configureByFile(getRelativePath() + "/" + testName + ".java");
    IndexingTestUtil.waitUntilIndexesAreReady(getProject());
    IntentionAction action = myFixture.getAvailableIntention(hint);
    if (action == null) {
      fail("No action '"+hint+"' found among "+
           StreamEx.of(myFixture.getAvailableIntentions()).map(IntentionAction::getText).joining(", "));
    }
    assertNotNull(action);
    Path previewPath = Path.of(myFixture.getTestDataPath(), getRelativePath(), testName + ".preview.java");
    if (Files.exists(previewPath)) {
      String previewText = myFixture.getIntentionPreviewText(action);
      assertSameLinesWithFile(previewPath.toString(), previewText);
      myFixture.launchAction(action);
    } else {
      myFixture.checkPreviewAndLaunchAction(action);
    }
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    myFixture.checkResultByFile(getRelativePath() + "/" + testName + ".after.java");
  }

  protected void doExpressionTest(
    @NotNull String hint,
    @Language(value = "JAVA", prefix = "/** @noinspection ALL*/class $X$ {static {System.out.print(", suffix = ");}}") @NotNull String before,
    @Language(value = "JAVA", prefix = "class $X$ {static {System.out.print(", suffix = ");}}") @NotNull String after
  ) {
    doTest(hint, "class $X$ {static {System.out.print(" + before + ");}}", "class $X$ {static {System.out.print(" + after + ");}}");
  }

  protected void doMemberTest(
    @NotNull String hint,
    @Language(value = "JAVA", prefix = "/** @noinspection ALL*/class $X$ {", suffix = "}") @NotNull String before,
    @Language(value = "JAVA", prefix = "class $X$ {", suffix = "}") @NotNull String after
  ) {
    doTest(hint, "class $X$ {" + before + "}", "class $X$ {" + after + "}");
  }

  protected void doTest(@NotNull String hint, @Language("JAVA") @NotNull String before, @Language("JAVA") @NotNull String after) {
    doTest(hint, before, after, getClass().getName() + "_" + getTestName(false) + ".java");
  }

  protected void doTest(@NotNull String hint,
                        @Language("JAVA") @NotNull String before,
                        @Language("JAVA") @NotNull String after,
                        @NotNull String fileName) {
    before = before.replace("/**/", "<caret>");
    myFixture.configureByText(fileName, before);
    IntentionAction intention = myFixture.getAvailableIntention(hint);
    assertNotNull(intention);
    myFixture.checkPreviewAndLaunchAction(intention);
    myFixture.checkResult(after);
  }

  protected String getRelativePath() {
    assertNotNull(myRelativePath);
    return myRelativePath;
  }
}