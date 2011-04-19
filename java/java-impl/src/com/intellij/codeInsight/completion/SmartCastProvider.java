package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.simple.RParenthTailType;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.tree.java.PsiEmptyExpressionImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
class SmartCastProvider extends CompletionProvider<CompletionParameters> {
  static final PsiElementPattern.Capture<PsiElement> INSIDE_TYPECAST_TYPE = PlatformPatterns.psiElement().afterLeaf(
    PlatformPatterns.psiElement().withText("(").withParent(
      PsiTypeCastExpression.class));

  protected void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
    for (final ExpectedTypeInfo info : JavaSmartCompletionContributor.getExpectedTypes(parameters)) {
      final PsiElement originalPosition = parameters.getOriginalPosition();
      final boolean overwrite = INSIDE_TYPECAST_TYPE.accepts(originalPosition);

      PsiType type = info.getDefaultType();
      if (type instanceof PsiWildcardType) {
        type = ((PsiWildcardType)type).getBound();
      }

      if (type == null || type == PsiType.VOID) {
        continue;
      }

      result.addElement(createSmartCastElement(parameters, overwrite, type));
      if (type instanceof PsiPrimitiveType) {
        final PsiType castedType = getCastedExpressionType(originalPosition);
        if (castedType != null && !(castedType instanceof PsiPrimitiveType)) {
          final PsiClassType boxedType = ((PsiPrimitiveType)type).getBoxedType(originalPosition);
          if (boxedType != null) {
            result.addElement(createSmartCastElement(parameters, overwrite, boxedType));
          }
        }
      }
    }
  }

  @Nullable
  private static PsiType getCastedExpressionType(PsiElement originalPosition) {
    if (INSIDE_TYPECAST_TYPE.accepts(originalPosition)) {
      final PsiTypeCastExpression cast = PsiTreeUtil.getParentOfType(originalPosition, PsiTypeCastExpression.class);
      if (cast != null) {
        final PsiExpression operand = cast.getOperand();
        return operand == null ? null : operand.getType();
      }
    }
    final PsiParenthesizedExpression parens = PsiTreeUtil.getParentOfType(originalPosition, PsiParenthesizedExpression.class, true, PsiStatement.class);
    if (parens != null) {
      final PsiExpression rightSide = parens.getExpression();
      if (rightSide != null) {
        return rightSide.getType();
      }
      PsiElement next = parens.getNextSibling();
      while (next != null && (next instanceof PsiEmptyExpressionImpl || next instanceof PsiErrorElement || next instanceof PsiWhiteSpace)) {
        next = next.getNextSibling();
      }
      if (next instanceof PsiExpression) {
        return ((PsiExpression)next).getType();
      }
      return null;
    }
    return null;
  }

  private static LookupElement createSmartCastElement(final CompletionParameters parameters, final boolean overwrite, final PsiType type) {
    return AutoCompletionPolicy.ALWAYS_AUTOCOMPLETE.applyPolicy(new LookupElementDecorator<PsiTypeLookupItem>(
      PsiTypeLookupItem.createLookupItem(type, parameters.getPosition())) {

      @Override
      public void handleInsert(InsertionContext context) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.smarttype.casting");

        final Editor editor = context.getEditor();
        final Document document = editor.getDocument();
        if (overwrite) {
          document.deleteString(context.getSelectionEndOffset(),
                                context.getOffsetMap().getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET));
        }

        final CodeStyleSettings csSettings = CodeStyleSettingsManager.getSettings(context.getProject());
        final int oldTail = context.getTailOffset();
        context.setTailOffset(RParenthTailType.addRParenth(editor, oldTail, csSettings.SPACE_WITHIN_CAST_PARENTHESES));

        getDelegate().handleInsert(CompletionUtil.newContext(context, getDelegate(), context.getStartOffset(), oldTail));

        PostprocessReformattingAspect.getInstance(context.getProject()).doPostponedFormatting();
        if (csSettings.SPACE_AFTER_TYPE_CAST) {
          context.setTailOffset(TailType.insertChar(editor, context.getTailOffset(), ' '));
        }

        editor.getCaretModel().moveToOffset(context.getTailOffset());
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      }
    });
  }
}
