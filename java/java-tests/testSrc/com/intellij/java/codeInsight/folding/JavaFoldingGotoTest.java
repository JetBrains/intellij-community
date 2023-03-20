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
package com.intellij.java.codeInsight.folding;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

public class JavaFoldingGotoTest extends JavaCodeInsightFixtureTestCase {

  public void testIDEA127145() {
    PsiFile file = myFixture.addFileToProject("Program.java",
                                              """
                                                import java.io.InputStream;
                                                import java.util.HashMap;
                                                import java.util.Map;

                                                class Program {
                                                  private static InputStream getFile(String name, Map<String, Object> args) {
                                                    return Program.class.getResourceAsStream(name);
                                                  }

                                                  public static void main(String[] args) {
                                                    // Ctrl + B or Ctrl + Left Mouse Button work correctly for following string:
                                                    final String name = "file.sql";
                                                    // But it jumps only to folder in following case:
                                                    final InputStream inputStream = getFile("dir/fil<caret>e.sql", new HashMap<String, Object>());
                                                  }
                                                }""");

    PsiFile fileSql = myFixture.addFileToProject("dir/file.sql", "select 1;");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    Editor editor = myFixture.getEditor();
    CodeFoldingManager.getInstance(getProject()).buildInitialFoldings(editor);
    FoldingModelEx foldingModel = (FoldingModelEx)editor.getFoldingModel();
    foldingModel.rebuild();
    myFixture.doHighlighting();

    PsiElement element = GotoDeclarationAction.findTargetElement(getProject(), editor, editor.getCaretModel().getOffset());
    assertTrue("Should navigate to: file.sql instead of " + element, element != null && element.equals(fileSql));
  }

}
