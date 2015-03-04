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
package com.intellij.psi.codeStyle;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

public class CodeStyleSettingsCodeFragmentFilter {
  private static final Logger LOG = Logger.getInstance(CodeStyleSettingsCodeFragmentFilter.class);

  private final Project myProject;
  private final PsiFile myFile;
  private final Document myDocument;
  private final RangeMarker myTextRangeMarker;
  private final LanguageCodeStyleSettingsProvider myProvider;

  private CommonCodeStyleSettings myCommonSettings;

  public CodeStyleSettingsCodeFragmentFilter(@NotNull PsiFile file, @NotNull TextRange range) {
    myProvider = LanguageCodeStyleSettingsProvider.forLanguage(file.getLanguage());
    myProject = file.getProject();
    myFile = PsiFileFactory.getInstance(myProject).createFileFromText("copy" + file.getName(), file.getLanguage(), file.getText(), true, false);
    myDocument = PsiDocumentManager.getInstance(myProject).getDocument(myFile);
    LOG.assertTrue(myDocument != null);
    myTextRangeMarker = myDocument.createRangeMarker(range.getStartOffset(), range.getEndOffset());
  }

  @NotNull
  public List<String> getFieldNamesAffectingCodeFragment(LanguageCodeStyleSettingsProvider.SettingsType type) {
    CodeStyleSettingsManager codeStyleSettingsManager = CodeStyleSettingsManager.getInstance(myProject);
    CodeStyleSettings clonedSettings = codeStyleSettingsManager.getCurrentSettings().clone();
    myCommonSettings = clonedSettings.getCommonSettings(myProvider.getLanguage());

    try {
      codeStyleSettingsManager.setTemporarySettings(clonedSettings);
      Set<String> fields = myProvider.getSupportedFields(type);
      return filterAffectingFields(fields);
    }
    finally {
      codeStyleSettingsManager.dropTemporarySettings();
    }
  }

  private List<String> filterAffectingFields(Set<String> fields) {
    List<String> affectingFields = ContainerUtil.newArrayList();
    for (String field : fields) {
      try {
        Field classField = CommonCodeStyleSettings.class.getField(field);
        boolean value = classField.getBoolean(myCommonSettings);
        classField.set(myCommonSettings, !value);

        if (formattingChangedFragment()) {
          affectingFields.add(field);
        }
      }
      catch (Exception ignored) {
      }
    }
    return affectingFields;
  }

  private boolean formattingChangedFragment() {
    final int rangeStart = myTextRangeMarker.getStartOffset();
    final int rangeEnd = myTextRangeMarker.getEndOffset();
    CharSequence textBefore = myDocument.getCharsSequence();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        CodeStyleManager.getInstance(myProject).reformatText(myFile, rangeStart, rangeEnd);
      }
    });

    if (rangeStart != myTextRangeMarker.getStartOffset() || rangeEnd != myTextRangeMarker.getEndOffset()) {
      return true;
    }
    else {
      CharSequence fragmentBefore = textBefore.subSequence(rangeStart, rangeEnd);
      CharSequence fragmentAfter = myDocument.getCharsSequence().subSequence(rangeStart, rangeEnd);
      return !StringUtil.equals(fragmentBefore, fragmentAfter);
    }
  }

}
