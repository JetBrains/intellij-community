// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.completion.modcommand;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.completion.JavaCompletionContributor;
import com.intellij.codeInsight.lookup.EqTailType;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcompletion.CommonCompletionItem;
import com.intellij.modcompletion.ModCompletionItemPresentation;
import com.intellij.modcompletion.ModCompletionResult;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.MarkupText;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiAnnotationMethod;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.JavaDeprecationUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

@NotNullByDefault
final class AnnotationAttributeItemProvider extends JavaModCompletionItemProvider {

  @Override
  public void provideItems(CompletionContext context, ModCompletionResult sink) {
    PsiElement position = context.getPosition();
    if (!context.isSmart() && position instanceof PsiIdentifier) {
      PsiAnnotation anno = JavaCompletionContributor.findAnnotationWhoseAttributeIsCompleted(position);
      if (anno != null) {
        PsiClass annoClass = anno.resolveAnnotationType();
        if (annoClass != null) {
          completeAnnotationAttributeName(sink, position, anno, annoClass);
        }
      }
    }
  }

  private static void completeAnnotationAttributeName(ModCompletionResult sink,
                                                      PsiElement position,
                                                      PsiAnnotation anno,
                                                      PsiClass annoClass) {
    PsiNameValuePair[] existingPairs = anno.getParameterList().getAttributes();

    methods:
    for (PsiMethod method : annoClass.getMethods()) {
      if (!(method instanceof PsiAnnotationMethod)) continue;

      String attrName = method.getName();
      for (PsiNameValuePair existingAttr : existingPairs) {
        if (PsiTreeUtil.isAncestor(existingAttr, position, false)) break;
        if (Objects.equals(existingAttr.getName(), attrName) ||
            PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(attrName) && existingAttr.getName() == null) {
          continue methods;
        }
      }

      PsiAnnotationMemberValue defaultValue = ((PsiAnnotationMethod)method).getDefaultValue();
      String defText = defaultValue == null ? null : defaultValue.getText();
      if (JavaKeywords.TRUE.equals(defText) || JavaKeywords.FALSE.equals(defText)) {
        sink.accept(createAnnotationAttributeElement(method,
                                                     JavaKeywords.TRUE.equals(defText) ? JavaKeywords.FALSE : JavaKeywords.TRUE,
                                                     "", position));
        sink.accept(createAnnotationAttributeElement(method, defText, " (default)", position)
                      .withPriority(-1));
      }
      else {
        sink.accept(createAnnotationAttributeElement(method, null, defText == null ? "" : " default " + defText, position));
      }
    }
  }

  private static CommonCompletionItem createAnnotationAttributeElement(PsiMethod annoMethod,
                                                                       @Nullable @NlsSafe String value,
                                                                       @NlsSafe String grayTail,
                                                                       PsiElement position) {
    CommonCodeStyleSettings styleSettings = CodeStyle.getLanguageSettings(annoMethod.getContainingFile());
    String space = styleSettings.SPACE_AROUND_ASSIGNMENT_OPERATORS ? " " : "";
    String lookupString = annoMethod.getName() + (value == null ? "" : space + "=" + space + value);
    ModCompletionItemPresentation presentation = new ModCompletionItemPresentation(
      MarkupText.plainText(lookupString)
        .highlightAll(JavaDeprecationUtils.isDeprecated(annoMethod, position) ? MarkupText.Kind.STRIKEOUT : MarkupText.Kind.NORMAL)
        .concat(grayTail, MarkupText.Kind.GRAYED))
      .withMainIcon(() -> annoMethod.getIcon(0))
      .withDetailText(JavaModCompletionUtils.typeMarkup(annoMethod.getReturnType()));
    return new CommonCompletionItem(lookupString)
      .withObject(annoMethod)
      .withPresentation(presentation)
      .withAdditionalUpdater((completionStart, updater) -> {
        if (value == null) {
          new EqTailType().processTail(updater, updater.getCaretOffset());
        }
        //context.setAddCompletionChar(false);

        Document document = updater.getDocument();
        PsiDocumentManager.getInstance(updater.getProject()).commitDocument(document);

        PsiAnnotationParameterList paramList =
          PsiTreeUtil.findElementOfClassAtOffset(updater.getPsiFile(), completionStart, PsiAnnotationParameterList.class, false);
        if (paramList != null && paramList.getAttributes().length > 0 && paramList.getAttributes()[0].getName() == null) {
          int valueOffset = paramList.getAttributes()[0].getTextRange().getStartOffset();
          document.insertString(valueOffset, PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
          new EqTailType().processTail(updater, valueOffset + PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.length());
        }
        int offset = updater.getCaretOffset();
        CharSequence sequence = document.getCharsSequence();
        if (hasAttributeNameAt(sequence, offset)) {
          document.insertString(offset, styleSettings.SPACE_AFTER_COMMA ? ", " : ",");
        }
      });
  }

  private static boolean hasAttributeNameAt(CharSequence sequence, int offset) {
    int length = sequence.length();
    if (length <= offset) return false;
    char nextChar = sequence.charAt(offset);
    if (!StringUtil.isJavaIdentifierStart(nextChar)) return false;
    while (offset < length - 1 && StringUtil.isJavaIdentifierPart(sequence.charAt(offset + 1))) {
      offset++;
    }
    while (offset < length - 1 && StringUtil.isWhiteSpace(sequence.charAt(offset + 1))) {
      offset++;
    }
    return offset < length - 1 && sequence.charAt(offset + 1) == '=';
  }
}
