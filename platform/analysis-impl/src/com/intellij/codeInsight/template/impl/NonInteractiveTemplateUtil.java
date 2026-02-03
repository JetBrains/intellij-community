// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.impl;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Template;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class NonInteractiveTemplateUtil {
  public static void runNonInteractively(PsiFile file, Document document, Template template, RangeMarker containerElement) {
    TemplateState state = new TemplateState(file.getProject(), null, document, new NonInteractiveTemplateStateProcessor());

    //noinspection unchecked
    state.getProperties().put(ExpressionContext.SELECTION, null);

    Runnable r = () -> {
      state.start((TemplateImpl) template, null, null, containerElement.getStartOffset());
      while (!state.isFinished()) {
        state.nextTab();
      }
    };
    CommandProcessor.getInstance().executeCommand(file.getProject(),
                                                  r,
                                                  AnalysisBundle.message("insert.code.template.command"),
                                                  null);
  }
}
