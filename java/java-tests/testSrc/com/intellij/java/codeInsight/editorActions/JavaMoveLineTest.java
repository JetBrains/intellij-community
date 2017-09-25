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

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.LightCodeInsightTestCase;

public class JavaMoveLineTest extends LightCodeInsightTestCase {
  private static final String BASE_PATH = "/codeInsight/editorActions/moveLine/";

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_7; // to enable folding of lambdas
  }

  public void testMoveThroughFolding() {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    CodeFoldingManager.getInstance(ourProject).buildInitialFoldings(myEditor);
    FoldRegion lambdaStart = myEditor.getFoldingModel().getFoldRegion(140, 227);
    assertNotNull(lambdaStart);
    assertFalse(lambdaStart.isExpanded());
    FoldRegion lambdaEnd = myEditor.getFoldingModel().getFoldRegion(248, 272);
    assertNotNull(lambdaEnd);
    assertFalse(lambdaEnd.isExpanded());

    executeAction(IdeActions.ACTION_MOVE_LINE_UP_ACTION);

    checkResultByFile(BASE_PATH + "/" + getTestName(false) + "-after.java");
  }
}
