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
import com.intellij.psi.codeStyle.extractor.FUtils;
import com.intellij.psi.codeStyle.extractor.differ.FDiffer;
import com.intellij.psi.codeStyle.extractor.differ.FLangCodeStyleExtractor;
import com.intellij.psi.codeStyle.extractor.values.FGens;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author Roman.Shein
 * @since 29.07.2015.
 */
public class FGenProcessor extends FCodeStyleDeriveProcessor{

  private static DateFormat formatter = new SimpleDateFormat("mm:ss");

  public FGenProcessor(FLangCodeStyleExtractor langExtractor) {
    super(langExtractor);
  }

  @Override
  public FGens runWithProgress(Project project, CodeStyleSettings settings, PsiFile file, ProgressIndicator indicator) {
    final FGens origGens = new FGens(getFormattingValues(settings, file.getLanguage()));
    final FGens forSelection = origGens.copy();

    final FDiffer differ = myLangExtractor.getDiffer(project, file, settings);
    forSelection.dropToInitial();
    FUtils.resetRandom();

    long startTime = System.nanoTime();
    FUtils.adjustValuesGA(forSelection, differ, indicator);
    reportResult("GA", forSelection, differ, startTime, file.getName());

    startTime = System.nanoTime();
    FUtils.adjustValuesMin(forSelection, differ, indicator);
    reportResult("MIN", forSelection, differ, startTime, file.getName());

    return forSelection;
  }

  private void reportResult(String label, FGens gens, FDiffer differ, long startTime, String fileName) {
    Date date = new Date((System.nanoTime() - startTime) / 1000000);
    System.out.println(fileName + ": " + label + " range:" + differ.getDifference(gens) + "  Execution Time: " + formatter.format(date));
  }
}
