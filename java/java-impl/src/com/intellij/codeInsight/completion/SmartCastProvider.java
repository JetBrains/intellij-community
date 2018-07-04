package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypeInfoImpl;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.simple.RParenthTailType;
import com.intellij.codeInsight.guess.GuessManager;
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
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

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
    addCastVariants(parameters, result.getPrefixMatcher(), result, false);
  }

  static void addCastVariants(@NotNull CompletionParameters parameters, PrefixMatcher matcher, @NotNull Consumer<LookupElement> result, boolean quick) {
    if (!shouldSuggestCast(parameters)) return;

    PsiElement position = parameters.getPosition();
    PsiElement parenthesisOwner = getParenthesisOwner(position);
    final boolean insideCast = parenthesisOwner instanceof PsiTypeCastExpression;

    if (insideCast) {
      PsiElement parent = parenthesisOwner.getParent();
      if (parent instanceof PsiParenthesizedExpression) {
        if (parent.getParent() instanceof PsiReferenceExpression) {
          for (ExpectedTypeInfo info : ExpectedTypesProvider.getExpectedTypes((PsiParenthesizedExpression)parent, false)) {
            result.consume(PsiTypeLookupItem.createLookupItem(info.getType(), parent));
          }
        }
        for (ExpectedTypeInfo info : getParenthesizedCastExpectationByOperandType(position)) {
          addHierarchyTypes(parameters, matcher, info, type -> result.consume(PsiTypeLookupItem.createLookupItem(type, parent)), quick);
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

  @NotNull
  static List<ExpectedTypeInfo> getParenthesizedCastExpectationByOperandType(PsiElement position) {
    PsiElement parenthesisOwner = getParenthesisOwner(position);
    PsiExpression operand = getCastedExpression(parenthesisOwner);
    if (operand == null || !(parenthesisOwner.getParent() instanceof PsiParenthesizedExpression)) return Collections.emptyList();

    List<PsiType> dfaTypes = GuessManager.getInstance(operand.getProject()).getControlFlowExpressionTypeConjuncts(operand);
    if (!dfaTypes.isEmpty()) {
      return ContainerUtil.map(dfaTypes, dfaType -> 
        new ExpectedTypeInfoImpl(dfaType, ExpectedTypeInfo.TYPE_OR_SUPERTYPE, dfaType, TailType.NONE, null, () -> null));
    }

    PsiType type = operand.getType();
    return type == null || type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) ? Collections.emptyList() :
           Collections.singletonList(new ExpectedTypeInfoImpl(type, ExpectedTypeInfo.TYPE_OR_SUBTYPE, type, TailType.NONE, null, () -> null));
  }

  private static void addHierarchyTypes(CompletionParameters parameters, PrefixMatcher matcher, ExpectedTypeInfo info, Consumer<PsiType> result, boolean quick) {
    PsiType infoType = info.getType();
    PsiClass infoClass = PsiUtil.resolveClassInClassTypeOnly(infoType);
    if (info.getKind() == ExpectedTypeInfo.TYPE_OR_SUPERTYPE) {
      InheritanceUtil.processSupers(infoClass, true, superClass -> {
        if (!CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName())) {
          result.consume(JavaPsiFacade.getElementFactory(superClass.getProject()).createType(CompletionUtil.getOriginalOrSelf(superClass)));
        }
        return true;
      });
    } else if (infoType instanceof PsiClassType && !quick) {
      JavaInheritorsGetter.processInheritors(parameters, Collections.singleton((PsiClassType)infoType), matcher, type -> {
        if (!infoType.equals(type)) {
          result.consume(type);
        }
      });
    }
  }

  private static PsiType getCastedExpressionType(PsiElement parenthesisOwner) {
    PsiExpression operand = getCastedExpression(parenthesisOwner);
    return operand == null ? null : operand.getType();
  }

  private static PsiExpression getCastedExpression(PsiElement parenthesisOwner) {
    if (parenthesisOwner instanceof PsiTypeCastExpression) {
      return ((PsiTypeCastExpression)parenthesisOwner).getOperand();
    }

    if (parenthesisOwner instanceof PsiParenthesizedExpression) {
      PsiElement next = parenthesisOwner.getNextSibling();
      while ((next instanceof PsiEmptyExpressionImpl || next instanceof PsiErrorElement || next instanceof PsiWhiteSpace)) {
        next = next.getNextSibling();
      }
      if (next instanceof PsiExpression) {
        return (PsiExpression)next;
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
