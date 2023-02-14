// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class JoinLinesPerformanceTest extends LightJavaCodeInsightTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testHugeArray() {
    int count = 2000;
    String bytesOriginal = String.join(",\n", Collections.nCopies(count, "0"));
    String bytesResult = String.join(", ", Collections.nCopies(count, "0"));
    String text = """
      class X {
        <selection>byte[] data = {$bytes$}</selection>;
      }""";
    String inputText = text.replace("$bytes$", bytesOriginal);

    PlatformTestUtil.startPerformanceTest(getTestName(false), 2500,
                                          () -> executeAction(IdeActions.ACTION_EDITOR_JOIN_LINES))
      .setup(() -> configureFromFileText("X.java", inputText))
      .assertTiming();
    String outputText = text.replace("$bytes$", bytesResult);
    checkResultByText(outputText);
  }
}
