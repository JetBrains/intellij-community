// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Random;

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

  @Override
  public @NotNull String getHTMLReport() {
    return "";
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
