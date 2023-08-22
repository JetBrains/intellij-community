// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.template;

import com.intellij.codeInsight.template.Result;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;


public interface RecalculatableResult extends Result {
  void handleRecalc(final PsiFile psiFile, final Document document, final int segmentStart, final int segmentEnd);
}
