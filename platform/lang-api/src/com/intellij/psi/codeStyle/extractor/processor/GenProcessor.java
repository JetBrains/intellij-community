// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.extractor.processor;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.extractor.Utils;
import com.intellij.psi.codeStyle.extractor.differ.Differ;
import com.intellij.psi.codeStyle.extractor.differ.LangCodeStyleExtractor;
import com.intellij.psi.codeStyle.extractor.values.Gens;
import org.jetbrains.annotations.NotNull;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class GenProcessor extends CodeStyleDeriveProcessor {
  private String myReport = "No result";

  public GenProcessor(LangCodeStyleExtractor langExtractor) {
    super(langExtractor);
  }

  @Override
  public Gens runWithProgress(Project project, CodeStyleSettings settings, PsiFile file, ProgressIndicator indicator) {
    final Gens origGens = new Gens(getFormattingValues(settings, file.getLanguage()));
    final Gens forSelection = origGens.copy();

    final Differ differ = myLangExtractor.getDiffer(project, file, settings);
    forSelection.dropToInitial();

    long startTime = System.nanoTime();
    Utils.adjustValuesGA(forSelection, differ, indicator);
    myReport = "<br> Genetic phase: " + reportResult(forSelection, differ, startTime);

    startTime = System.nanoTime();
    Utils.adjustValuesMin(forSelection, differ, indicator);
    myReport += "<br> Minimization Phase: " + reportResult(forSelection, differ, startTime);

    return forSelection;
  }

  @Override
  public @NotNull String getHTMLReport() {
    return myReport;
  }

  private static @NotNull String reportResult(@NotNull Gens gens, @NotNull Differ differ, long startTime) {
    DateFormat formatter = new SimpleDateFormat("mm:ss");
    Date date = new Date((System.nanoTime() - startTime) / 1000000);
    return "Difference in spaces with the original:" + differ.getDifference(gens) + " Execution Time:" + formatter.format(date);
  }
}
