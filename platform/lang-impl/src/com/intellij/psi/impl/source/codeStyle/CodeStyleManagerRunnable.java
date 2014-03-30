/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.formatting.*;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageFormatting;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.DocumentBasedFormattingModel;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
abstract class CodeStyleManagerRunnable<T> {
  protected CodeStyleSettings mySettings;
  protected CommonCodeStyleSettings.IndentOptions myIndentOptions;
  protected FormattingModel myModel;
  protected TextRange mySignificantRange;
  private final CodeStyleManagerImpl myCodeStyleManager;
  @NotNull private final FormattingMode myMode;

  CodeStyleManagerRunnable(CodeStyleManagerImpl codeStyleManager, @NotNull FormattingMode mode) {
    myCodeStyleManager = codeStyleManager;
    myMode = mode;
  }

  public T perform(PsiFile file, int offset, @Nullable TextRange range, T defaultValue) {
    if (file instanceof PsiCompiledFile) {
      file = ((PsiCompiledFile)file).getDecompiledPsiFile();
    }

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myCodeStyleManager.getProject());
    Document document = documentManager.getDocument(file);
    if (document instanceof DocumentWindow) {
      final DocumentWindow documentWindow = (DocumentWindow)document;
      final PsiFile topLevelFile = InjectedLanguageManager.getInstance(file.getProject()).getTopLevelFile(file);
      if (!file.equals(topLevelFile)) {
        if (range != null) {
          range = documentWindow.injectedToHost(range);
        }
        if (offset != -1) {
          offset = documentWindow.injectedToHost(offset);
        }
        return adjustResultForInjected(perform(topLevelFile, offset, range, defaultValue), documentWindow);
      }
    }

    final PsiFile templateFile = PsiUtilCore.getTemplateLanguageFile(file);
    if (templateFile != null) {
      file = templateFile;
      document = documentManager.getDocument(templateFile);
    }

    PsiElement element = null;
    if (offset != -1) {
      element = CodeStyleManagerImpl.findElementInTreeWithFormatterEnabled(file, offset);
      if (element == null && offset != file.getTextLength()) {
        return defaultValue;
      }
      if (isInsidePlainComment(offset, element)) {
        return computeValueInsidePlainComment(file, offset, defaultValue);
      }
    }

    final FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(file);
    FormattingModelBuilder elementBuilder = element != null ? LanguageFormatting.INSTANCE.forContext(element) : builder;
    if (builder != null && elementBuilder != null) {
      mySettings = CodeStyleSettingsManager.getSettings(myCodeStyleManager.getProject());

      mySignificantRange = offset != -1 ? getSignificantRange(file, offset) : null;

      if (builder instanceof FormattingModelBuilderEx) {
        myIndentOptions = ((FormattingModelBuilderEx)builder).getIndentOptionsToUse(file, new FormatTextRanges(mySignificantRange, true), mySettings);
      }
      if (myIndentOptions == null) {
        myIndentOptions = mySettings.getIndentOptions(file.getFileType());
      }

      myModel = CoreFormatterUtil.buildModel(builder, file, mySettings, myMode);

      if (document != null && useDocumentBaseFormattingModel()) {
        myModel = new DocumentBasedFormattingModel(myModel.getRootBlock(), document, myCodeStyleManager.getProject(), mySettings,
                                                   file.getFileType(), file);
      }

      final T result = doPerform(offset, range);
      if (result != null) {
        return result;
      }
    }
    return defaultValue;
  }

  protected boolean useDocumentBaseFormattingModel() {
    return true;
  }

  protected T adjustResultForInjected(T result, DocumentWindow documentWindow) {
    return result;
  }

  protected T computeValueInsidePlainComment(PsiFile file, int offset, T defaultValue) {
    return defaultValue;
  }

  @Nullable
  protected abstract T doPerform(int offset, TextRange range);

  private static boolean isInsidePlainComment(int offset, @Nullable PsiElement element) {
    if (!(element instanceof PsiComment) || element instanceof PsiDocCommentBase || !element.getTextRange().contains(offset)) {
      return false;
    }

    if (element instanceof PsiLanguageInjectionHost && InjectedLanguageUtil.hasInjections((PsiLanguageInjectionHost)element)) {
      return false;
    }

    return true;
  }

  private static TextRange getSignificantRange(final PsiFile file, final int offset) {
    final ASTNode elementAtOffset =
      SourceTreeToPsiMap.psiElementToTree(CodeStyleManagerImpl.findElementInTreeWithFormatterEnabled(file, offset));
    if (elementAtOffset == null) {
      int significantRangeStart = CharArrayUtil.shiftBackward(file.getText(), offset - 1, "\r\t ");
      return new TextRange(significantRangeStart, offset);
    }

    final FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(file);
    final TextRange textRange = builder.getRangeAffectingIndent(file, offset, elementAtOffset);
    if (textRange != null) {
      return textRange;
    }

    return elementAtOffset.getTextRange();
  }
}
