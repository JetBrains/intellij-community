/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.refactoring.extractMethodObject;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.refactoring.extractMethod.AbstractExtractDialog;
import com.intellij.refactoring.extractMethod.InputVariables;
import com.intellij.refactoring.util.VariableData;
import org.jetbrains.annotations.Nullable;

public class ExtractLightMethodObjectHandler {
  private static final Logger LOG = Logger.getInstance("#" + ExtractLightMethodObjectHandler.class.getName());

  public static class ExtractedData {
    private String myGeneratedCallText;
    private PsiClass myGeneratedInnerClass;

    public ExtractedData(String generatedCallText, PsiClass generatedInnerClass) {
      myGeneratedCallText = generatedCallText;
      myGeneratedInnerClass = generatedInnerClass;
    }

    public String getGeneratedCallText() {
      return myGeneratedCallText;
    }

    public PsiClass getGeneratedInnerClass() {
      return myGeneratedInnerClass;
    }
  }

  @Nullable
  public static ExtractedData extractLightMethodObject(final Project project,
                                                       final Editor editor,
                                                       final PsiFile file,
                                                       final PsiElement[] elements,
                                                       final String methodName) {
    if (elements == null || elements.length == 0) {
      return null;
    }

    final PsiFile copy = PsiFileFactory.getInstance(project)
      .createFileFromText(file.getName(), file.getFileType(), file.getText(), file.getModificationStamp(), true);

    final PsiElement[] elementsCopy = new PsiElement[elements.length];
    for (int i = 0; i < elements.length; i++) {
      PsiElement element = elements[i];
      final TextRange textRange = element.getTextRange();
      elementsCopy[i] = CodeInsightUtil.findElementInRange(copy, textRange.getStartOffset(), textRange.getEndOffset(), element.getClass());
    }

    final Document document = PsiDocumentManager.getInstance(project).getDocument(copy);
    LOG.assertTrue(document != null);

    final int startOffset = elements[0].getTextRange().getStartOffset();
    final int endOffset = elements[elements.length - 1].getTextRange().getEndOffset();

    final RangeMarker callSiteMarker = document.createRangeMarker(startOffset, endOffset);
    callSiteMarker.setGreedyToLeft(true);
    callSiteMarker.setGreedyToRight(true);

    try {
      final ExtractMethodObjectProcessor extractMethodObjectProcessor = new ExtractMethodObjectProcessor(project, editor, elementsCopy, "") {
        @Override
        protected AbstractExtractDialog createExtractMethodObjectDialog(MyExtractMethodProcessor processor) {
          return new LightExtractMethodObjectDialog(this, methodName);
        }
      };
      extractMethodObjectProcessor.getExtractProcessor().setShowErrorDialogs(false);

      ExtractMethodObjectHandler.extractMethodObject(project, editor, file, extractMethodObjectProcessor);

      final String generatedCall = document.getText(new TextRange(callSiteMarker.getStartOffset(), callSiteMarker.getEndOffset()));
      return new ExtractedData(generatedCall, extractMethodObjectProcessor.getInnerClass());
    }
    finally {
      callSiteMarker.dispose();
    }
  }

  private static class LightExtractMethodObjectDialog implements AbstractExtractDialog {
    private final ExtractMethodObjectProcessor myProcessor;
    private final String myMethodName;

    public LightExtractMethodObjectDialog(ExtractMethodObjectProcessor processor, String methodName) {
      myProcessor = processor;
      myMethodName = methodName;
    }

    @Override
    public String getChosenMethodName() {
      return myMethodName;
    }

    @Override
    public VariableData[] getChosenParameters() {
      final InputVariables inputVariables = myProcessor.getExtractProcessor().getInputVariables();
      return inputVariables.getInputVariables().toArray(new VariableData[inputVariables.getInputVariables().size()]);
    }

    @Override
    public String getVisibility() {
      return PsiModifier.PUBLIC;
    }

    @Override
    public boolean isMakeStatic() {
      return false;
    }

    @Override
    public boolean isChainedConstructor() {
      return false;
    }

    @Override
    public void show() {}

    @Override
    public boolean isOK() {
      return true;
    }
  }
}
