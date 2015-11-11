package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
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
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.tree.java.PsiEmptyExpressionImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author peter
 */
class SmartCastProvider extends CompletionProvider<CompletionParameters> {
  static final ElementPattern<PsiElement> TYPECAST_TYPE_CANDIDATE = psiElement().afterLeaf("(");
  
  static boolean shouldSuggestCast(CompletionParameters parameters) {
    PsiElement position = parameters.getPosition();
    PsiElement parent = getParenthesisOwner(position);
    if (parent instanceof PsiTypeCastExpression) return true;
    if (parent instanceof PsiParenthesizedExpression) {
      return parameters.getOffset() == position.getTextRange().getStartOffset();
    }
    return false;
  }

  private static PsiElement getParenthesisOwner(PsiElement position) {
    PsiElement lParen = PsiTreeUtil.prevVisibleLeaf(position);
    return lParen == null || !lParen.textMatches("(") ? null : lParen.getParent();
  }

  @Override
  protected void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
    addCastVariants(parameters, result);
  }

  static void addCastVariants(@NotNull CompletionParameters parameters, @NotNull Consumer<LookupElement> result) {
    if (!shouldSuggestCast(parameters)) return;

    PsiElement position = parameters.getPosition();
    PsiElement parenthesisOwner = getParenthesisOwner(position);
    final boolean insideCast = parenthesisOwner instanceof PsiTypeCastExpression;

    if (insideCast) {
      PsiElement parent = parenthesisOwner.getParent();
      if (parent instanceof PsiParenthesizedExpression && parent.getParent() instanceof PsiReferenceExpression) {
        for (ExpectedTypeInfo info : ExpectedTypesProvider.getExpectedTypes((PsiParenthesizedExpression)parent, false)) {
          result.consume(PsiTypeLookupItem.createLookupItem(info.getType(), parent));
        }
        return;
      }
    }

    for (final ExpectedTypeInfo info : JavaSmartCompletionContributor.getExpectedTypes(parameters)) {
      PsiType type = info.getDefaultType();
      if (type instanceof PsiWildcardType) {
        type = ((PsiWildcardType)type).getBound();
      }

      if (type == null || PsiType.VOID.equals(type)) {
        continue;
      }

      if (type instanceof PsiPrimitiveType) {
        final PsiType castedType = getCastedExpressionType(parenthesisOwner);
        if (castedType != null && !(castedType instanceof PsiPrimitiveType)) {
          final PsiClassType boxedType = ((PsiPrimitiveType)type).getBoxedType(position);
          if (boxedType != null) {
            type = boxedType;
          }
        }
      }
      result.consume(createSmartCastElement(parameters, insideCast, type));
    }
  }

  @Nullable
  private static PsiType getCastedExpressionType(PsiElement parenthesisOwner) {
    if (parenthesisOwner instanceof PsiTypeCastExpression) {
      final PsiExpression operand = ((PsiTypeCastExpression)parenthesisOwner).getOperand();
      return operand == null ? null : operand.getType();
    }

    if (parenthesisOwner instanceof PsiParenthesizedExpression) {
      PsiElement next = parenthesisOwner.getNextSibling();
      while (next != null && (next instanceof PsiEmptyExpressionImpl || next instanceof PsiErrorElement || next instanceof PsiWhiteSpace)) {
        next = next.getNextSibling();
      }
      if (next instanceof PsiExpression) {
        return ((PsiExpression)next).getType();
      }
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

        final CommonCodeStyleSettings csSettings = context.getCodeStyleSettings();
        final int oldTail = context.getTailOffset();
        context.setTailOffset(RParenthTailType.addRParenth(editor, oldTail, csSettings.SPACE_WITHIN_CAST_PARENTHESES));

        getDelegate().handleInsert(CompletionUtil.newContext(context, getDelegate(), context.getStartOffset(), oldTail));

        PostprocessReformattingAspect.getInstance(context.getProject()).doPostponedFormatting();
        if (csSettings.SPACE_AFTER_TYPE_CAST) {
          context.setTailOffset(TailType.insertChar(editor, context.getTailOffset(), ' '));
        }

        if (parameters.getCompletionType() == CompletionType.SMART) {
          editor.getCaretModel().moveToOffset(context.getTailOffset());
        }
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      }
    });
  }
}
