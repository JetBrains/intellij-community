/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.extractor.differ;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.extractor.Utils;
import com.intellij.psi.codeStyle.extractor.values.ValuesExtractionResult;
import org.jetbrains.annotations.NotNull;

public abstract class Differ {
  public static final int UGLY_FORMATTING = Integer.MAX_VALUE;
  
  protected final Project myProject;
  protected final String myOrigText;
  protected final CodeStyleSettings mySettings;
  protected final PsiFile myFile;

  public Differ(@NotNull Project project, @NotNull PsiFile file, @NotNull CodeStyleSettings settings) {
    myProject = project;
    myFile = file;
    myOrigText = myFile.getText();
    mySettings = settings;
  }

  @NotNull
  public abstract String reformattedText();

  public int getDifference(@NotNull ValuesExtractionResult container) {
    final CommonCodeStyleSettings.IndentOptions indentOptions = mySettings.getCommonSettings(myFile.getLanguage()).getIndentOptions();
    final int origTabSize = Utils.getTabSize(indentOptions);
    final ValuesExtractionResult orig = container.apply(true);
    final int newTabSize = Utils.getTabSize(indentOptions);
    String newText = reformattedText();
    int result = Utils.getDiff(origTabSize, myOrigText, newTabSize, newText);
    orig.apply(false);
    return result;
  }
}
