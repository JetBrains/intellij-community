// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.*;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.element.ModifierFilter;
import com.intellij.psi.filters.types.AssignableFromFilter;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;

public final class ReferenceExpressionCompletionContributor {
  private static final Logger LOG = Logger.getInstance(ReferenceExpressionCompletionContributor.class);

  static @NotNull ElementFilter getReferenceFilter(PsiElement element, boolean allowRecursion) {
    //throw foo
    if (psiElement().withParent(psiElement(PsiReferenceExpression.class).withParent(PsiThrowStatement.class)).accepts(element)) {
      return TrueFilter.INSTANCE;
    }

    if (psiElement().inside(StandardPatterns.or(psiElement(PsiAnnotationParameterList.class), JavaCompletionContributor.IN_SWITCH_LABEL)).accepts(element)) {
      return new ElementExtractorFilter(new AndFilter(
          new ClassFilter(PsiField.class),
          new ModifierFilter(JavaKeywords.STATIC, JavaKeywords.FINAL)
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

  static List<LookupElement> smartCompleteReference(List<LookupElement> allRefSuggestions, Set<ExpectedTypeInfo> infos) {
    List<LookupElement> result = new ArrayList<>();
    for (LookupElement item : allRefSuggestions) {
      if (matchesExpectedType(item, infos)) {
        if (item instanceof JavaMethodCallElement) {
          checkTooGeneric((JavaMethodCallElement)item);
        }
        result.add(JavaSmartCompletionContributor.decorate(item, infos));
      }
    }
    return result;
  }

  static boolean matchesExpectedType(LookupElement item, Set<ExpectedTypeInfo> infos) {
    return ContainerUtil.exists(infos, info -> matchesExpectedType(item, info.getType()));
  }

  private static boolean matchesExpectedType(LookupElement item, PsiType type) {
    Object object = item.getObject();
    if (object instanceof PsiClass) return false;
    if (PsiTypes.voidType().equals(type)) return object instanceof PsiMethod;

    PsiType itemType = JavaCompletionUtil.getLookupElementType(item);
    return itemType != null && type.isAssignableFrom(itemType);
  }

  static Set<LookupElement> completeFinalReference(PsiElement element,
                                                   PsiJavaCodeReferenceElement reference,
                                                   ElementFilter filter,
                                                   PsiType expectedType,
                                                   CompletionParameters parameters) {
    final Set<PsiField> used = parameters.getInvocationCount() < 2 ? findConstantsUsedInSwitch(element) : Collections.emptySet();

    final Set<LookupElement> elements =
      JavaSmartCompletionContributor.completeReference(element, reference, new AndFilter(filter, new ElementFilter() {
        @Override
        public boolean isAcceptable(Object o, PsiElement context) {
          if (o instanceof CandidateInfo info) {
            final PsiElement member = info.getElement();

            if (expectedType.equals(PsiTypes.voidType())) {
              return member instanceof PsiMethod;
            }

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
      }), false, true, parameters, PrefixMatcher.ALWAYS_TRUE);
    for (LookupElement lookupElement : elements) {
      if (lookupElement.getObject() instanceof PsiMethod) {
        final JavaMethodCallElement item = lookupElement.as(JavaMethodCallElement.CLASS_CONDITION_KEY);
        if (item != null) {
          item.setInferenceSubstitutorFromExpectedType(element, expectedType);
          checkTooGeneric(item);
        }
      }
    }

    return elements;
  }

  private static void checkTooGeneric(JavaMethodCallElement item) {
    if (JavaCompletionSorting.isTooGeneric(item, item.getObject())) {
      item.setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
    }
  }

  public static @NotNull Set<PsiField> findConstantsUsedInSwitch(@Nullable PsiElement position) {
    return JavaCompletionContributor.IN_SWITCH_LABEL.accepts(position)
           ? findConstantsUsedInSwitch(Objects.requireNonNull(PsiTreeUtil.getParentOfType(position, PsiSwitchBlock.class)))
           : Collections.emptySet();
  }

  public static @NotNull Set<PsiField> findConstantsUsedInSwitch(@NotNull PsiSwitchBlock sw) {
    final PsiCodeBlock body = sw.getBody();
    if (body == null) return Collections.emptySet();

    Set<PsiField> used = new LinkedHashSet<>();
    for (PsiStatement statement : body.getStatements()) {
      if (statement instanceof PsiSwitchLabelStatementBase) {
        final PsiCaseLabelElementList labelElementList = ((PsiSwitchLabelStatementBase)statement).getCaseLabelElementList();
        if (labelElementList != null) {
          for (PsiCaseLabelElement labelElement : labelElementList.getElements()) {
            if (labelElement instanceof PsiReferenceExpression) {
              final PsiElement target = ((PsiReferenceExpression)labelElement).resolve();
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

  static String getQualifierText(final @Nullable PsiElement qualifier) {
    return qualifier == null ? "" : qualifier.getText() + ".";
  }

  static @Nullable PsiReferenceExpression createMockReference(PsiElement place, @NotNull PsiType qualifierType, LookupElement qualifierItem) {
    return createMockReference(place, qualifierType, qualifierItem, ".");
  }

  static @Nullable PsiReferenceExpression createMockReference(PsiElement place, @NotNull PsiType qualifierType, LookupElement qualifierItem, String separator) {
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
