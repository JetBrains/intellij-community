/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.scope.CompletionElement;
import com.intellij.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.javaDoc.JavaDocLocalInspection;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.javadoc.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 05.03.2003
 * Time: 21:40:11
 * To change this template use Options | File Templates.
 */
public class JavaDocCompletionContributor extends CompletionContributor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.JavaDocCompletionContributor");
  private static final @NonNls String VALUE_TAG = "value";
  private static final @NonNls String LINK_TAG = "link";

  public JavaDocCompletionContributor() {
    extend(CompletionType.BASIC, PsiJavaPatterns.psiElement(PsiDocToken.DOC_TAG_NAME), new TagChooser());

    extend(CompletionType.BASIC, PsiJavaPatterns.psiElement().inside(PsiDocTagValue.class), new CompletionProvider<CompletionParameters>(
      true) {
      protected void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        final PsiElement position = parameters.getPosition();
        boolean isArg = PsiJavaPatterns.psiElement().afterLeaf("(").accepts(position);
        PsiDocTag tag = PsiTreeUtil.getParentOfType(position, PsiDocTag.class);
        boolean onlyConstants = !isArg && tag != null && tag.getName().equals(VALUE_TAG);

        final PsiReference ref = position.getContainingFile().findReferenceAt(parameters.getOffset());
        if (ref instanceof PsiJavaReference) {
          result.stopHere();

          final JavaCompletionProcessor processor = new JavaCompletionProcessor(position, TrueFilter.INSTANCE, false, null);
          ((PsiJavaReference) ref).processVariants(processor);

          for (final CompletionElement _item : processor.getResults()) {
            final Object element = _item.getElement();
            LookupItem item = element instanceof PsiMethod ? new JavaMethodCallElement((PsiMethod)element) {
              @Override
              public void handleInsert(InsertionContext context) {
                new MethodSignatureInsertHandler().handleInsert(context, this);
              }
            } : (LookupItem)LookupItemUtil.objectToLookupItem(element);
            if (onlyConstants) {
              Object o = item.getObject();
              if (!(o instanceof PsiField)) continue;
              PsiField field = (PsiField) o;
              if (!(field.hasModifierProperty(PsiModifier.STATIC) && field.getInitializer() != null &&
                  JavaConstantExpressionEvaluator.computeConstantExpression(field.getInitializer(), false) != null)) continue;
            }

            item.putUserData(LookupItem.FORCE_SHOW_SIGNATURE_ATTR, Boolean.TRUE);
            if (isArg) {
              item.setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
            }
            result.addElement(item);
          }
        }
      }
    });
  }

  @Override
  public void fillCompletionVariants(final CompletionParameters parameters, final CompletionResultSet result) {
    if (PsiJavaPatterns.psiElement(PsiDocToken.DOC_COMMENT_DATA).accepts(parameters.getPosition())) return;

    super.fillCompletionVariants(parameters, result);
  }

  private static class TagChooser extends CompletionProvider<CompletionParameters> {

    protected void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
      List<String> ret = new ArrayList<String>();
      final PsiElement position = parameters.getPosition();
      final PsiDocComment comment = PsiTreeUtil.getParentOfType(position, PsiDocComment.class);
      final PsiElement parent = comment.getContext();
      final boolean isInline = position.getContext() instanceof PsiInlineDocTag;

      final JavadocManager manager = JavaPsiFacade.getInstance(position.getProject()).getJavadocManager();
      final JavadocTagInfo[] infos = manager.getTagInfos(parent);
      for (JavadocTagInfo info : infos) {
        if (info.getName().equals(SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME)) continue;
        if (isInline != (info.isInline())) continue;
        ret.add(info.getName());
      }

      InspectionProfile inspectionProfile =
        InspectionProjectProfileManager.getInstance(position.getProject()).getInspectionProfile();
      final InspectionProfileEntry inspectionTool = inspectionProfile.getInspectionTool(JavaDocLocalInspection.SHORT_NAME, position);
      JavaDocLocalInspection inspection = (JavaDocLocalInspection)((LocalInspectionToolWrapper)inspectionTool).getTool();
      final StringTokenizer tokenizer = new StringTokenizer(inspection.myAdditionalJavadocTags, ", ");
      while (tokenizer.hasMoreTokens()) {
        ret.add(tokenizer.nextToken());
      }
      for (final String s : ret) {
        if (isInline) {
          result.addElement(TailTypeDecorator.withInsertHandler(LookupElementBuilder.create(s), new InlineInsertHandler()));
        } else {
          result.addElement(TailTypeDecorator.withTail(LookupElementBuilder.create(s), TailType.SPACE));
        }
      }
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public String toString() {
      return "javadoc-tag-chooser";
    }
  }

  private static class InlineInsertHandler implements InsertHandler<LookupElement> {
    public void handleInsert(InsertionContext context, LookupElement item) {
      if (context.getCompletionChar() == Lookup.REPLACE_SELECT_CHAR) {
        final Project project = context.getProject();
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        final Editor editor = context.getEditor();
        final CaretModel caretModel = editor.getCaretModel();
        final int offset = caretModel.getOffset();
        final PsiElement element = context.getFile().findElementAt(offset - 1);
        PsiDocTag tag = PsiTreeUtil.getParentOfType(element, PsiDocTag.class);

        for (PsiElement child = tag.getFirstChild(); child != null; child = child.getNextSibling()) {
          if (child instanceof PsiDocToken) {
            PsiDocToken token = (PsiDocToken)child;
            if (token.getTokenType() == JavaDocTokenType.DOC_INLINE_TAG_END) return;
          }
        }

        final String name = tag.getName();

        final CharSequence chars = editor.getDocument().getCharsSequence();
        final int currentOffset = caretModel.getOffset();
        if (chars.charAt(currentOffset) == '}') {
          caretModel.moveToOffset(offset + 1);
        }
        else if (chars.charAt(currentOffset + 1) == '}' && chars.charAt(currentOffset) == ' ') {
          caretModel.moveToOffset(offset + 2);
        }
        else if (name.equals(LINK_TAG)) {
          EditorModificationUtil.insertStringAtCaret(editor, " }");
          caretModel.moveToOffset(offset + 1);
          editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
          editor.getSelectionModel().removeSelection();
        }
        else {
          EditorModificationUtil.insertStringAtCaret(editor, "}");
          caretModel.moveToOffset(offset + 1);
        }
      }
    }
  }

  private static class MethodSignatureInsertHandler implements InsertHandler<LookupItem> {
    public void handleInsert(InsertionContext context, LookupItem item) {
      if (!(item.getObject() instanceof PsiMethod)) {
        return;
      }
      PsiDocumentManager.getInstance(context.getProject()).commitDocument(context.getEditor().getDocument());
      final Editor editor = context.getEditor();
      final PsiMethod method = (PsiMethod)((LookupItem)item).getObject();

      final PsiParameter[] parameters = method.getParameterList().getParameters();
      final StringBuffer buffer = new StringBuffer();

      final CharSequence chars = editor.getDocument().getCharsSequence();
      int endOffset = editor.getCaretModel().getOffset();
      final Project project = context.getProject();
      int afterSharp = CharArrayUtil.shiftBackwardUntil(chars, endOffset - 1, "#") + 1;
      int signatureOffset = afterSharp;

      PsiElement element = context.getFile().findElementAt(signatureOffset - 1);
      final CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(element.getProject());
      PsiDocTag tag = PsiTreeUtil.getParentOfType(element, PsiDocTag.class);
      if (context.getCompletionChar() == Lookup.REPLACE_SELECT_CHAR) {
        final PsiDocTagValue valueElement = tag.getValueElement();
        endOffset = valueElement.getTextRange().getEndOffset();
      }
      editor.getDocument().deleteString(afterSharp, endOffset);
      editor.getCaretModel().moveToOffset(signatureOffset);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      editor.getSelectionModel().removeSelection();
      buffer.append(method.getName() + "(");
      final int afterParenth = afterSharp + buffer.length();
      for (int i = 0; i < parameters.length; i++) {
        final PsiType type = TypeConversionUtil.erasure(parameters[i].getType());
        buffer.append(type.getCanonicalText());

        if (i < parameters.length - 1) {
          buffer.append(",");
          if (styleSettings.SPACE_AFTER_COMMA) buffer.append(" ");
        }
      }
      buffer.append(")");
      if (!(tag instanceof PsiInlineDocTag)) {
        buffer.append(" ");
      }
      else {
        final int currentOffset = editor.getCaretModel().getOffset();
        if (chars.charAt(currentOffset) == '}') {
          afterSharp++;
        }
        else {
          buffer.append("} ");
        }
      }
      String insertString = buffer.toString();
      EditorModificationUtil.insertStringAtCaret(editor, insertString);
      editor.getCaretModel().moveToOffset(afterSharp + buffer.length());
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());

      shortenReferences(project, editor, context, afterParenth);
    }

    private static void shortenReferences(final Project project, final Editor editor, InsertionContext context, int offset) {
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
      final PsiElement element = context.getFile().findElementAt(offset);
      final PsiDocTagValue tagValue = PsiTreeUtil.getParentOfType(element, PsiDocTagValue.class);
      if (tagValue != null) {
        try {
          JavaCodeStyleManager.getInstance(project).shortenClassReferences(tagValue);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
      PsiDocumentManager.getInstance(context.getProject()).commitAllDocuments();
    }
  }
}
