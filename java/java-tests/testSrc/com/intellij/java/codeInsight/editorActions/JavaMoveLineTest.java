/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInsight.editorActions;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class JavaMoveLineTest {
  private static final String BASE_PATH = "/codeInsight/editorActions/moveLine/";

  // JDK_1_7 enables the folding of lambdas
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_1_7));
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  @Test
  public void testMoveThroughFolding() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    LightJavaCodeInsightTestFixture myFixture = daemon.getFixture();
    daemon.configureByFile(BASE_PATH + "/" + myFixture.getTestName(false) + ".java");
    EdtTestUtil.runInEdtAndWait(() -> {
      Editor editor = myFixture.getEditor();
      EditorTestUtil.buildInitialFoldingsInBackground(editor);
      FoldRegion lambdaStart = editor.getFoldingModel().getFoldRegion(140, 227);
      assertNotNull(lambdaStart);
      assertFalse(lambdaStart.isExpanded());
      FoldRegion lambdaEnd = editor.getFoldingModel().getFoldRegion(248, 272);
      assertNotNull(lambdaEnd);
      assertFalse(lambdaEnd.isExpanded());
    });

    daemon.executeAction(IdeActions.ACTION_MOVE_LINE_UP_ACTION);

    myFixture.checkResultByFile(BASE_PATH + "/" + myFixture.getTestName(false) + "-after.java");
  }
}
