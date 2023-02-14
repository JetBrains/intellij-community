// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.application.options.CodeStyle;
import com.intellij.formatting.CoreFormatterUtil;
import com.intellij.formatting.FormattingMode;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageFormatting;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.DocumentBasedFormattingModel;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtilBase;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    if (document instanceof DocumentWindow documentWindow) {
      final PsiFile topLevelFile = InjectedLanguageManager.getInstance(file.getProject()).getTopLevelFile(file);
      if (!file.equals(topLevelFile)) {
        if (range != null) {
          range = documentWindow.injectedToHost(range);
        }
        if (offset != -1) {
          offset = documentWindow.injectedToHost(offset);
        }
        // see `InjectedLanguageBlockBuilder` to configure injected blocks formatting in the host formatter
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
      element = CoreCodeStyleUtil.findElementInTreeWithFormatterEnabled(file, offset);
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
      mySettings = CodeStyle.getSettings(file);

      mySignificantRange = offset != -1 ? getSignificantRange(file, offset) : null;
      myIndentOptions = CodeFormatterFacade.getIndentOptions(mySettings, myCodeStyleManager.getProject(), file, document, mySignificantRange);

      FormattingMode currentMode = myCodeStyleManager.getCurrentFormattingMode();
      myCodeStyleManager.setCurrentFormattingMode(myMode);
      try {
        TextRange modelRange = range != null ? range :
                               offset != -1 ? TextRange.create(offset, offset) :
                               file.getTextRange();
        myModel = buildModel(builder, file, modelRange, document);
        T result = doPerform(offset, range);
        if (result != null) {
          return result;
        }
      }
      finally {
        myCodeStyleManager.setCurrentFormattingMode(currentMode);
      }
    }
    return defaultValue;
  }

  @NotNull
  private FormattingModel buildModel(@NotNull FormattingModelBuilder builder,
                                     @NotNull PsiFile file,
                                     @NotNull TextRange range,
                                     @Nullable Document document) {
    FormattingModel model = CoreFormatterUtil.buildModel(builder, file, range, mySettings, myMode);
    if (document != null && useDocumentBaseFormattingModel()) {
      model = new DocumentBasedFormattingModel(model, document, myCodeStyleManager.getProject(), mySettings,
                                               file.getFileType(), file);
    }
    return model;
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
    if (!(element instanceof PsiComment) || element instanceof PsiDocCommentBase || !element.getTextRange().contains(offset - 1)) {
      return false;
    }

    if (element instanceof PsiLanguageInjectionHost && InjectedLanguageUtilBase.hasInjections((PsiLanguageInjectionHost)element)) {
      return false;
    }

    return true;
  }

  private static TextRange getSignificantRange(final PsiFile file, final int offset) {
    final ASTNode elementAtOffset =
      SourceTreeToPsiMap.psiElementToTree(CoreCodeStyleUtil.findElementInTreeWithFormatterEnabled(file, offset));
    if (elementAtOffset == null) {
      int significantRangeStart = CharArrayUtil.shiftBackward(file.getText(), offset - 1, "\n\r\t ");
      return new TextRange(Math.max(significantRangeStart, 0), offset);
    }

    final FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(file);
    if (builder != null) {
      final TextRange textRange = builder.getRangeAffectingIndent(file, offset, elementAtOffset);
      if (textRange != null) {
        return textRange;
      }
    }

    final TextRange elementRange = elementAtOffset.getTextRange();
    if (isWhiteSpace(elementAtOffset)) {
      return extendRangeAtStartOffset(file, elementRange);
    }
    
    return elementRange;
  }

  private static boolean isWhiteSpace(ASTNode elementAtOffset) {
    return elementAtOffset instanceof PsiWhiteSpace 
           || CharArrayUtil.containsOnlyWhiteSpaces(elementAtOffset.getChars());
  }

  @NotNull
  private static TextRange extendRangeAtStartOffset(@NotNull final PsiFile file, @NotNull final TextRange range) {
    int startOffset = range.getStartOffset();
    if (startOffset > 0) {
      String text = file.getText();
      startOffset = CharArrayUtil.shiftBackward(text, startOffset, "\n\r\t ");
    }

    return new TextRange(startOffset + 1, range.getEndOffset());
  }
}
