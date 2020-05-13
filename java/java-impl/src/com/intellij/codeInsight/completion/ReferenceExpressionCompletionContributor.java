// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.ExpressionLookupItem;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.*;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.element.ModifierFilter;
import com.intellij.psi.filters.types.AssignableFromFilter;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;

/**
 * @author peter
 */
public class ReferenceExpressionCompletionContributor {
  private static final Logger LOG = Logger.getInstance(ReferenceExpressionCompletionContributor.class);

  @NotNull
  static ElementFilter getReferenceFilter(PsiElement element, boolean allowRecursion) {
    //throw foo
    if (psiElement().withParent(psiElement(PsiReferenceExpression.class).withParent(PsiThrowStatement.class)).accepts(element)) {
      return TrueFilter.INSTANCE;
    }

    if (psiElement().inside(StandardPatterns.or(psiElement(PsiAnnotationParameterList.class), JavaCompletionContributor.IN_SWITCH_LABEL)).accepts(element)) {
      return new ElementExtractorFilter(new AndFilter(
          new ClassFilter(PsiField.class),
          new ModifierFilter(PsiKeyword.STATIC, PsiKeyword.FINAL)
      ));
    }

    final PsiForeachStatement foreach = PsiTreeUtil.getParentOfType(element, PsiForeachStatement.class);
    if (foreach != null && !PsiTreeUtil.isAncestor(foreach.getBody(), element, false)) {
      return new ElementExtractorFilter(new ElementFilter() {
        @Override
        public boolean isAcceptable(Object element, @Nullable PsiElement context) {
          return element != foreach.getIterationParameter();
        }

        @Override
        public boolean isClassAcceptable(Class hintClass) {
          return true;
        }
      });
    }

    if (!allowRecursion) {
      final ElementFilter filter = RecursionWeigher.recursionFilter(element);
      if (filter != null) {
        return new ElementExtractorFilter(filter);
      }
    }

    return TrueFilter.INSTANCE;
  }

  @Nullable
  public static Runnable fillCompletionVariants(final JavaSmartCompletionParameters parameters, final Consumer<? super LookupElement> result) {
    final PsiElement element = parameters.getPosition();
    if (JavaSmartCompletionContributor.INSIDE_TYPECAST_EXPRESSION.accepts(element)) return null;
    if (JavaKeywordCompletion.isAfterPrimitiveOrArrayType(element)) return null;

    final int offset = parameters.getParameters().getOffset();
    final PsiJavaCodeReferenceElement reference = PsiTreeUtil.findElementOfClassAtOffset(element.getContainingFile(), offset, PsiJavaCodeReferenceElement.class, false);
    if (reference != null) {
      ElementFilter filter = getReferenceFilter(element, false);
      if (CheckInitialized.isInsideConstructorCall(element)) {
        filter = new AndFilter(filter, new CheckInitialized(element));
      }

      for (final LookupElement item : completeFinalReference(element, reference, filter, parameters)) {
        result.consume(item);
      }

      final boolean secondTime = parameters.getParameters().getInvocationCount() >= 2;

      final Set<LookupElement> base =
        JavaSmartCompletionContributor.completeReference(element, reference, filter, false, true, parameters.getParameters(), PrefixMatcher.ALWAYS_TRUE);
      for (final LookupElement item : new LinkedHashSet<>(base)) {
        ExpressionLookupItem access = ArrayMemberAccess.accessFirstElement(element, item);
        if (access != null) {
          base.add(access);
          PsiType type = access.getType();
          if (type != null && parameters.getExpectedType().isAssignableFrom(type)) {
            result.consume(access);
          }
        }
      }

      if (secondTime) {
        return new SlowerTypeConversions(base, element, reference, parameters, result);
      }
    }
    return null;
  }

  static Set<LookupElement> completeFinalReference(final PsiElement element, PsiJavaCodeReferenceElement reference, ElementFilter filter,
                                                           final JavaSmartCompletionParameters parameters) {
    final Set<PsiField> used = parameters.getParameters().getInvocationCount() < 2 ? findConstantsUsedInSwitch(element) : Collections.emptySet();

    final Set<LookupElement> elements =
      JavaSmartCompletionContributor.completeReference(element, reference, new AndFilter(filter, new ElementFilter() {
        @Override
        public boolean isAcceptable(Object o, PsiElement context) {
          if (o instanceof CandidateInfo) {
            final CandidateInfo info = (CandidateInfo)o;
            final PsiElement member = info.getElement();

            final PsiType expectedType = parameters.getExpectedType();
            if (expectedType.equals(PsiType.VOID)) {
              return member instanceof PsiMethod;
            }

            //noinspection SuspiciousMethodCalls
            if (member instanceof PsiEnumConstant && used.contains(CompletionUtil.getOriginalOrSelf(member))) {
              return false;
            }

            return AssignableFromFilter.isAcceptable(member, element, expectedType, info.getSubstitutor());
          }
          return false;
        }

        @Override
        public boolean isClassAcceptable(Class hintClass) {
          return true;
        }
      }), false, true, parameters.getParameters(), PrefixMatcher.ALWAYS_TRUE);
    for (LookupElement lookupElement : elements) {
      if (lookupElement.getObject() instanceof PsiMethod) {
        final JavaMethodCallElement item = lookupElement.as(JavaMethodCallElement.CLASS_CONDITION_KEY);
        if (item != null) {
          item.setInferenceSubstitutorFromExpectedType(element, parameters.getExpectedType());
          if (JavaCompletionSorting.isTooGeneric(lookupElement, item.getObject())) {
            item.setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
          }
        }
      }
    }

    return elements;
  }

  @NotNull
  public static Set<PsiField> findConstantsUsedInSwitch(@Nullable PsiElement position) {
    return JavaCompletionContributor.IN_SWITCH_LABEL.accepts(position)
           ? findConstantsUsedInSwitch(Objects.requireNonNull(PsiTreeUtil.getParentOfType(position, PsiSwitchBlock.class)))
           : Collections.emptySet();
  }

  @NotNull
  public static Set<PsiField> findConstantsUsedInSwitch(@NotNull PsiSwitchBlock sw) {
    final PsiCodeBlock body = sw.getBody();
    if (body == null) return Collections.emptySet();

    Set<PsiField> used = new LinkedHashSet<>();
    for (PsiStatement statement : body.getStatements()) {
      if (statement instanceof PsiSwitchLabelStatementBase) {
        final PsiExpressionList values = ((PsiSwitchLabelStatementBase)statement).getCaseValues();
        if (values != null) {
          for (PsiExpression value : values.getExpressions()) {
            if (value instanceof PsiReferenceExpression) {
              final PsiElement target = ((PsiReferenceExpression)value).resolve();
              if (target instanceof PsiField) {
                used.add(CompletionUtil.getOriginalOrSelf((PsiField)target));
              }
            }
          }
        }
      }
    }
    return used;
  }

  static PsiExpression createExpression(String text, PsiElement element) {
    return JavaPsiFacade.getElementFactory(element.getProject()).createExpressionFromText(text, element);
  }

  static String getQualifierText(@Nullable final PsiElement qualifier) {
    return qualifier == null ? "" : qualifier.getText() + ".";
  }

  @Nullable
  static PsiReferenceExpression createMockReference(PsiElement place, @NotNull PsiType qualifierType, LookupElement qualifierItem) {
    return createMockReference(place, qualifierType, qualifierItem, ".");
  }

  @Nullable
  static PsiReferenceExpression createMockReference(PsiElement place, @NotNull PsiType qualifierType, LookupElement qualifierItem, String separator) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(place.getProject());
    if (qualifierItem.getObject() instanceof PsiClass) {
      final String qname = ((PsiClass)qualifierItem.getObject()).getQualifiedName();
      if (qname == null) return null;

      String text = qname + separator + "xxx";
      try {
        final PsiExpression expr = factory.createExpressionFromText(text, place);
        if (expr instanceof PsiReferenceExpression) {
          return (PsiReferenceExpression)expr;
        }
        return null; // ignore ill-formed qualified names like "org.spark-project.jetty" that can't be used from Java code anyway
      }
      catch (IncorrectOperationException e) {
        LOG.info(e);
        return null;
      }
    }

    return (PsiReferenceExpression) factory.createExpressionFromText("xxx" + separator + "xxx", JavaCompletionUtil
      .createContextWithXxxVariable(place, qualifierType));
  }

  static String getSpace(boolean needSpace) {
    return needSpace ? " " : "";
  }
}
