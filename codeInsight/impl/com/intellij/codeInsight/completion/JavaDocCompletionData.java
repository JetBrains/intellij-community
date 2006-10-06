package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.javaDoc.JavaDocLocalInspection;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.position.LeftNeighbour;
import com.intellij.psi.filters.position.TokenTypeFilter;
import com.intellij.psi.impl.ConstantExpressionEvaluator;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.javadoc.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;

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
public class JavaDocCompletionData extends CompletionData {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.JavaDocCompletionData");
  private static final @NonNls String VALUE_TAG = "value";
  private static final @NonNls String LINK_TAG = "link";

  public JavaDocCompletionData() {
    declareFinalScope(PsiDocTag.class);
    declareFinalScope(PsiDocTagValue.class);

    {
      final CompletionVariant variant = new CompletionVariant(new TokenTypeFilter(PsiDocToken.DOC_COMMENT_DATA));
      variant.includeScopeClass(PsiDocToken.class, true);
      registerVariant(variant);
    }

    {
      final ElementFilter position = new AndFilter(new NotFilter(new ScopeFilter(new ClassFilter(PsiInlineDocTag.class))),
                                                   new TokenTypeFilter(PsiDocToken.DOC_TAG_NAME));
      final CompletionVariant variant = new CompletionVariant(PsiDocTag.class, position);
      variant.addCompletion(new TagChooser());
      registerVariant(variant);
    }

    {
      final ElementFilter position = new AndFilter(new ScopeFilter(new ClassFilter(PsiInlineDocTag.class)),
                                                   new TokenTypeFilter(PsiDocToken.DOC_TAG_NAME));

      final CompletionVariant variant = new CompletionVariant(PsiDocTag.class, position);
      variant.setInsertHandler(new InlineInsertHandler());
      variant.addCompletion(new TagChooser());
      registerVariant(variant);
    }

    {
      final CompletionVariant variant = new CompletionVariant(PsiDocTagValue.class, new LeftNeighbour(new TextFilter("(")));
      variant.addCompletionFilter(TrueFilter.INSTANCE);
      variant.setInsertHandler(new MethodSignatureInsertHandler());
      variant.setItemProperty(LookupItem.FORCE_SHOW_SIGNATURE_ATTR, Boolean.TRUE);
      variant.setItemProperty(LookupItem.DO_NOT_AUTOCOMPLETE_ATTR, Boolean.TRUE);
      registerVariant(variant);
    }

    {
      final CompletionVariant variant = new CompletionVariant(PsiDocTagValue.class, new NotFilter(new LeftNeighbour(new TextFilter("("))));
      variant.addCompletionFilter(new ElementFilter() {
        public boolean isAcceptable(Object element, PsiElement context) {
          if (element instanceof CandidateInfo) {
            PsiDocTag tag = PsiTreeUtil.getParentOfType(context, PsiDocTag.class);
            if (tag != null && tag.getName().equals(VALUE_TAG)) {
              CandidateInfo cInfo = (CandidateInfo) element;
              if (!(cInfo.getElement() instanceof PsiField)) return false;
              PsiField field = (PsiField) cInfo.getElement();
              return field.getModifierList().hasModifierProperty(PsiModifier.STATIC) &&
                     field.getInitializer() != null &&
                     ConstantExpressionEvaluator.computeConstantExpression(field.getInitializer(), null, false) != null;
            }
          }

          return true;
        }

        public boolean isClassAcceptable(Class hintClass) {
          return true;
        }
      });
      variant.setInsertHandler(new MethodSignatureInsertHandler());
      variant.setItemProperty(LookupItem.FORCE_SHOW_SIGNATURE_ATTR, Boolean.TRUE);
      registerVariant(variant);
    }
  }

  private class TagChooser implements KeywordChooser {
    public String[] getKeywords(CompletionContext context, PsiElement position) {
      List<String> ret = new ArrayList<String>();
      final PsiDocComment comment = PsiTreeUtil.getParentOfType(position, PsiDocComment.class);
      final PsiElement parent = comment.getContext();
      final boolean isInline = position.getContext() instanceof PsiInlineDocTag;

      final JavadocManager manager = context.file.getManager().getJavadocManager();
      final JavadocTagInfo[] infos = manager.getTagInfos(parent);
      for (JavadocTagInfo info : infos) {
        if (info.getName().equals(GlobalInspectionContextImpl.SUPPRESS_INSPECTIONS_TAG_NAME)) continue;
        if (isInline != (info.isInline())) continue;
        ret.add(info.getName());
      }

      InspectionProfile inspectionProfile =
        InspectionProjectProfileManager.getInstance(position.getProject()).getInspectionProfile(position);
      final InspectionProfileEntry inspectionTool = inspectionProfile.getInspectionTool(JavaDocLocalInspection.SHORT_NAME);
      JavaDocLocalInspection inspection = (JavaDocLocalInspection)((LocalInspectionToolWrapper)inspectionTool).getTool();
      final StringTokenizer tokenizer = new StringTokenizer(inspection.myAdditionalJavadocTags, ", ");
      while (tokenizer.hasMoreTokens()) {
        ret.add(tokenizer.nextToken());
      }
      return ret.toArray(new String[ret.size()]);
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public String toString() {
      return "javadoc-tag-chooser";
    }
  }

  private class InlineInsertHandler extends BasicInsertHandler {
    public void handleInsert(CompletionContext context,
                             int startOffset,
                             LookupData data,
                             LookupItem item,
                             boolean signatureSelected, char completionChar) {
      super.handleInsert(context, startOffset, data, item, signatureSelected, completionChar);

      if (completionChar == Lookup.REPLACE_SELECT_CHAR) {
        final Project project = context.project;
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        final Editor editor = context.editor;
        final CaretModel caretModel = editor.getCaretModel();
        final int offset = caretModel.getOffset();
        final PsiElement element = context.file.findElementAt(offset - 1);
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

  private class MethodSignatureInsertHandler extends BasicInsertHandler {
    public void handleInsert(CompletionContext context,
                             int startOffset,
                             LookupData data,
                             LookupItem item,
                             boolean signatureSelected,
                             char completionChar) {
      super.handleInsert(context, startOffset, data, item, signatureSelected, completionChar);
      if (!(item.getObject() instanceof PsiMethod)) {
        return;
      }
      PsiDocumentManager.getInstance(context.project).commitDocument(context.editor.getDocument());
      final Editor editor = context.editor;
      final PsiMethod method = (PsiMethod)item.getObject();

      final PsiParameter[] parameters = method.getParameterList().getParameters();
      final StringBuffer buffer = new StringBuffer();

      final CharSequence chars = editor.getDocument().getCharsSequence();
      int endOffset = editor.getCaretModel().getOffset();
      final Project project = context.project;
      int afterSharp = CharArrayUtil.shiftBackwardUntil(chars, endOffset, "#") + 1;
      int signatureOffset = afterSharp;

      PsiElement element = context.file.findElementAt(signatureOffset - 1);
      final CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(element.getProject());
      PsiDocTag tag = PsiTreeUtil.getParentOfType(element, PsiDocTag.class);
      if (completionChar == Lookup.REPLACE_SELECT_CHAR) {
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

    private void shortenReferences(final Project project, final Editor editor, CompletionContext context, int offset) {
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
      final PsiElement element = context.file.findElementAt(offset);
      final PsiDocTagValue tagValue = PsiTreeUtil.getParentOfType(element, PsiDocTagValue.class);
      if (tagValue != null) {
        try {
          tagValue.getManager().getCodeStyleManager().shortenClassReferences(tagValue);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
      PsiDocumentManager.getInstance(context.project).commitAllDocuments();
    }
  }
}
