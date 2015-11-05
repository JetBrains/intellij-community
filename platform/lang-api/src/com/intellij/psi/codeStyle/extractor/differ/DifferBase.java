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
import com.intellij.psi.codeStyle.extractor.Utils;
import com.intellij.psi.codeStyle.extractor.values.ValuesExtractionResult;

/**
 * @author Roman.Shein
 * @since 30.07.2015.
 */
public abstract class DifferBase implements Differ {
  protected final Project myProject;
  protected final String myOrigText;
  protected final CodeStyleSettings mySettings;
  protected final PsiFile myFile;

  public DifferBase(Project project, PsiFile file, CodeStyleSettings settings) {
    myProject = project;
    myFile = file;
    myOrigText = myFile.getText();
    mySettings = settings;
  }

  public abstract String reformattedText();

  @Override
  public int getDifference(ValuesExtractionResult container) {
    final ValuesExtractionResult orig = container.apply(true);
    String newText = reformattedText();
    int result = Utils.getDiff(mySettings.getCommonSettings(myFile.getLanguage()).getIndentOptions(), myOrigText, newText);
    orig.apply(false);
    return result;
  }
}
