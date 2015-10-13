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

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.extractor.Utils;
import com.intellij.psi.codeStyle.extractor.differ.Differ;
import com.intellij.psi.codeStyle.extractor.differ.LangCodeStyleExtractor;
import com.intellij.psi.codeStyle.extractor.values.Gens;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author Roman.Shein
 * @since 29.07.2015.
 */
public class GenProcessor extends CodeStyleDeriveProcessor {

  private static DateFormat formatter = new SimpleDateFormat("mm:ss");

  public GenProcessor(LangCodeStyleExtractor langExtractor) {
    super(langExtractor);
  }

  @Override
  public Gens runWithProgress(Project project, CodeStyleSettings settings, PsiFile file, ProgressIndicator indicator) {
    final Gens origGens = new Gens(getFormattingValues(settings, file.getLanguage()));
    final Gens forSelection = origGens.copy();

    final Differ differ = myLangExtractor.getDiffer(project, file, settings);
    forSelection.dropToInitial();
    Utils.resetRandom();

    long startTime = System.nanoTime();
    Utils.adjustValuesGA(forSelection, differ, indicator);
    reportResult("GA", forSelection, differ, startTime, file.getName());

    startTime = System.nanoTime();
    Utils.adjustValuesMin(forSelection, differ, indicator);
    reportResult("MIN", forSelection, differ, startTime, file.getName());

    return forSelection;
  }

  private void reportResult(String label, Gens gens, Differ differ, long startTime, String fileName) {
    Date date = new Date((System.nanoTime() - startTime) / 1000000);
    System.out.println(fileName + ": " + label + " range:" + differ.getDifference(gens) + "  Execution Time: " + formatter.format(date));
  }
}
