// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.FileBasedTestCaseHelper;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(com.intellij.testFramework.Parameterized.class)
@TestDataPath("/testData/../../../platform/lang-impl/testData/editor/braceHighlighterBlock/")
public class BraceHighlightingHandlerBlockCaretTest extends LightPlatformCodeInsightTestCase implements FileBasedTestCaseHelper {
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
  public String getFileSuffix(String fileName) {
    return StringUtil.endsWith(fileName, ".txt") ? null : fileName;
  }

  @Override
  public @Nullable String getBaseName(@NotNull String fileAfterSuffix) {
    return StringUtil.endsWith(fileAfterSuffix, ".txt") ? fileAfterSuffix.substring(0, fileAfterSuffix.length() - 4) : null;
  }
}