// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.completion.modcommand;

import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.completion.CompletionMemory;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.completion.JavaSmartCompletionContributor;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcompletion.CommonCompletionItem;
import com.intellij.modcompletion.ModCompletionItemPresentation;
import com.intellij.modcompletion.ModCompletionResult;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.MarkupText;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiCall;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.impl.source.tree.java.PsiEmptyExpressionImpl;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.IconManager;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.PlatformIcons;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * Completes the parameters of an enclosing method as the arguments of a call with the same signature:
 * {@code super(|)} or {@code delegate.foo(|)} inside {@code foo(String s, File file)} offers {@code s, file}.
 */
@ApiStatus.Internal
@NotNullByDefault
public final class SameSignatureCallParametersItemProvider extends JavaModCompletionItemProvider implements DumbAware {
  private static final PsiElementPattern.Capture<PsiElement> IN_CALL_ARGUMENT =
    psiElement().afterLeaf("(").withParent(
      psiElement(PsiReferenceExpression.class).withParent(
        psiElement(PsiExpressionList.class).withParent(PsiCall.class))).with(
      new PatternCondition<>("Method call completed with parameter hints") {
        @Override
        public boolean accepts(PsiElement element, ProcessingContext context) {
          PsiElement e = element.getParent();
          while ((e = e.getNextSibling()) != null) {
            if (e instanceof PsiExpression && !(e instanceof PsiEmptyExpressionImpl)) return false;
          }
          return true;
        }
      });

  /** The context object of an item; the Java weighers rank the item by it. */
  public record Arguments(String text) {
  }

  @Override
  public void provideItems(CompletionContext context, ModCompletionResult sink) {
    PsiElement position = context.getPosition();
    if (!JavaSmartCompletionContributor.INSIDE_EXPRESSION.accepts(position) ||
        JavaCompletionUtil.AFTER_DOT.accepts(position) ||
        inCastContext(position, context.getOffset()) ||
        !IN_CALL_ARGUMENT.accepts(position)) {
      return;
    }
    PsiCall methodCall = PsiTreeUtil.getParentOfType(position, PsiCall.class);
    if (methodCall == null) return;
    Set<Pair<PsiMethod, PsiSubstitutor>> candidates = getCallCandidates(methodCall);

    PsiMethod container = PsiTreeUtil.getParentOfType(methodCall, PsiMethod.class);
    while (container != null) {
      for (Pair<PsiMethod, PsiSubstitutor> candidate : candidates) {
        if (container.getParameterList().getParametersCount() > 1 && candidate.first.getParameterList().getParametersCount() > 1) {
          PsiMethod from = getMethodToTakeParametersFrom(container, candidate.first, candidate.second);
          if (from != null) {
            sink.accept(createParametersItem(from, methodCall, candidate.first));
          }
        }
      }
      container = PsiTreeUtil.getParentOfType(container, PsiMethod.class);
    }
  }

  /** The check of {@code SmartCastProvider.inCastContext}: the position follows the {@code (} of a cast or a parenthesized expression. */
  private static boolean inCastContext(PsiElement position, int offset) {
    PsiElement lParen = PsiTreeUtil.prevVisibleLeaf(position);
    PsiElement parent = lParen == null || !lParen.textMatches("(") ? null : lParen.getParent();
    if (parent instanceof PsiTypeCastExpression) return true;
    return parent instanceof PsiParenthesizedExpression && offset == position.getTextRange().getStartOffset();
  }

  private static CommonCompletionItem createParametersItem(PsiMethod takeParametersFrom, PsiCall call, PsiMethod invoked) {
    PsiParameter[] parameters = takeParametersFrom.getParameterList().getParameters();
    String lookupString = StringUtil.join(parameters, PsiNamedElement::getName, ", ");
    // the parameters are made final only in the enclosing method; its start offset is before the caret, so the edit does not move it
    int makeFinalMethodOffset = PsiTreeUtil.isAncestor(takeParametersFrom, call, true) ? takeParametersFrom.getTextRange().getStartOffset() : -1;
    return new CommonCompletionItem(lookupString)
      .withPresentation(new ModCompletionItemPresentation(MarkupText.plainText(lookupString))
                          .withMainIcon(SameSignatureCallParametersItemProvider::createIcon))
      .withObject(new Arguments(lookupString))
      .withTail(ExpectedTypesProvider.getFinalCallParameterTailType(call, invoked.getReturnType(), invoked))
      .withAdditionalUpdater((ignored, updater) -> removeRestArguments(updater, makeFinalMethodOffset));
  }

  private static LayeredIcon createIcon() {
    IconManager iconManager = IconManager.getInstance();
    int w = iconManager.getPlatformIcon(PlatformIcons.Parameter).getIconWidth();
    LayeredIcon icon = new LayeredIcon(2);
    icon.setIcon(iconManager.getPlatformIcon(PlatformIcons.Parameter), 0, 2 * w / 5, 0);
    icon.setIcon(iconManager.getPlatformIcon(PlatformIcons.Parameter), 1);
    return icon;
  }

  /** Deletes the text between the inserted arguments and the {@code )} of the call, and makes the parameters final if needed. */
  private static void removeRestArguments(ModPsiUpdater updater, int makeFinalMethodOffset) {
    PsiFile file = updater.getPsiFile();
    int tailOffset = updater.getCaretOffset();
    PsiExpressionList exprList = PsiTreeUtil.findElementOfClassAtOffset(file, tailOffset - 1, PsiExpressionList.class, false);
    PsiElement rParen = exprList == null ? null : exprList.getLastChild();
    if (rParen != null && rParen.textMatches(")")) {
      updater.getDocument().deleteString(tailOffset, rParen.getTextRange().getStartOffset());
    }
    if (makeFinalMethodOffset < 0) return;
    PsiDocumentManager.getInstance(updater.getProject()).commitDocument(updater.getDocument());
    PsiMethod method = PsiTreeUtil.getParentOfType(file.findElementAt(makeFinalMethodOffset), PsiMethod.class, false);
    PsiElement place = file.findElementAt(tailOffset - 1);
    if (method == null || place == null) return;
    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      VariableCompletionItem.makeFinalIfNeeded(place, parameter);
    }
  }

  private static Set<Pair<PsiMethod, PsiSubstitutor>> getCallCandidates(PsiCall expression) {
    PsiMethod chosenMethod = CompletionMemory.getChosenMethod(expression);
    Set<Pair<PsiMethod, PsiSubstitutor>> candidates = new LinkedHashSet<>();
    JavaResolveResult[] results = expression.multiResolve(false);

    PsiMethod toExclude =
      JavaPsiConstructorUtil.isConstructorCall(expression) ? PsiTreeUtil.getParentOfType(expression, PsiMethod.class) : null;

    for (JavaResolveResult candidate : results) {
      PsiElement element = candidate.getElement();
      if (element instanceof PsiMethod method) {
        PsiClass psiClass = method.getContainingClass();
        if (psiClass != null) {
          for (Pair<PsiMethod, PsiSubstitutor> overload : psiClass.findMethodsAndTheirSubstitutorsByName(method.getName(), true)) {
            if (overload.first != toExclude && (chosenMethod == null || chosenMethod.isEquivalentTo(overload.first))) {
              candidates.add(Pair.create(overload.first, candidate.getSubstitutor().putAll(overload.second)));
            }
          }
          break;
        }
      }
    }
    return candidates;
  }

  private static @Nullable PsiMethod getMethodToTakeParametersFrom(PsiMethod place, PsiMethod invoked, PsiSubstitutor substitutor) {
    if (PsiSuperMethodUtil.isSuperMethod(place, invoked)) {
      return place;
    }

    Map<String, PsiType> requiredNames = new HashMap<>();
    PsiParameter[] parameters = place.getParameterList().getParameters();
    PsiParameter[] callParams = invoked.getParameterList().getParameters();
    if (callParams.length > parameters.length) {
      return null;
    }

    boolean checkNames = invoked.isConstructor();
    boolean sameTypes = true;
    for (int i = 0; i < callParams.length; i++) {
      PsiParameter callParam = callParams[i];
      PsiParameter parameter = parameters[i];
      requiredNames.put(callParam.getName(), substitutor.substitute(callParam.getType()));
      if (checkNames && !Objects.equals(parameter.getName(), callParam.getName()) ||
          !Comparing.equal(parameter.getType(), substitutor.substitute(callParam.getType()))) {
        sameTypes = false;
      }
    }

    if (sameTypes && callParams.length == parameters.length) {
      return place;
    }

    for (PsiParameter parameter : parameters) {
      PsiType type = requiredNames.remove(parameter.getName());
      if (type != null && !parameter.getType().equals(type)) {
        return null;
      }
    }

    return requiredNames.isEmpty() ? invoked : null;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}
