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
package com.intellij.psi.codeStyle.extractor.processor;

import com.intellij.lang.Language;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.extractor.Utils;
import com.intellij.psi.codeStyle.extractor.differ.Differ;
import com.intellij.psi.codeStyle.extractor.differ.LangCodeStyleExtractor;
import com.intellij.psi.codeStyle.extractor.values.Value;
import com.intellij.psi.codeStyle.extractor.values.ValuesExtractionResult;
import com.intellij.psi.codeStyle.extractor.values.ValuesExtractionResultImpl;

import java.util.List;
import java.util.Random;

/**
 * @author Roman.Shein
 * @since 04.08.2015.
 */
public class BruteForceProcessor extends CodeStyleDeriveProcessor {

  public BruteForceProcessor(LangCodeStyleExtractor langExtractor) {
    super(langExtractor);
  }

  @Override
  public ValuesExtractionResult runWithProgress(Project project, CodeStyleSettings settings, PsiFile file, ProgressIndicator indicator) {
    List<Value> values = getFormattingValues(settings, file.getLanguage());
    Differ differ = myLangExtractor.getDiffer(project, file, settings);
    ValuesExtractionResult container = new ValuesExtractionResultImpl(values);
    Utils.adjustValuesMin(container, differ, indicator);
    return container;
  }

  public void randomizeSettings(CodeStyleSettings settings, Language language) {
    List<Value> values = getFormattingValues(settings, language);
    Random rand = new Random();
    for (Value value : values) {
      Object[] possible = value.getPossibleValues();
      int index = rand.nextInt(possible.length);
      value.value = possible[index];
      value.write(false);
    }
  }
}
