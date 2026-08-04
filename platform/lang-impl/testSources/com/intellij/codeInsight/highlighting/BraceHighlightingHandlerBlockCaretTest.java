// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.bazel.runfiles.BazelLabel;
import com.intellij.testFramework.FileBasedTestCaseHelper;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.common.BazelTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(com.intellij.testFramework.Parameterized.class)
@TestDataPath("/testData/editor/braceHighlighterBlock/")
public class BraceHighlightingHandlerBlockCaretTest extends LightPlatformCodeInsightTestCase implements FileBasedTestCaseHelper {
  @Override
  @NotNull
  protected String getTestDataPath() {
    if (BazelTestUtil.isUnderBazelTest()) {
      var label = BazelLabel.Companion.fromString("@community//platform/lang-impl:testData");
      return BazelTestUtil.getFileFromBazelRuntime(label).toAbsolutePath().resolve("editor/braceHighlighterBlock").toString().concat("/");
    }
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/platform/lang-impl/testData/editor/braceHighlighterBlock/";
  }

  @Test
  public void testAction() {
    configureByFile(myFileSuffix);
    Editor editor = getEditor();
    editor.getSettings().setBlockCursor(true);
    String result = BraceHighlightingHandlerTest.getEditorTextWithHighlightedBraces(getEditor(), getFile());
    UsefulTestCase.assertSameLinesWithFile(getAnswerFilePath(), result);
  }

  @Nullable
  @Override
  public String getFileSuffix(@NotNull String fileName) {
    return StringUtil.endsWith(fileName, ".txt") ? null : fileName;
  }

  @Override
  public @Nullable String getBaseName(@NotNull String fileAfterSuffix) {
    return StringUtil.endsWith(fileAfterSuffix, ".txt") ? fileAfterSuffix.substring(0, fileAfterSuffix.length() - 4) : null;
  }
}