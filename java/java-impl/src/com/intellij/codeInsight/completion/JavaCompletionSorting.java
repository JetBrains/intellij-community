/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementWeigher;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.searches.DeepestSuperMethodsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class JavaCompletionSorting {
  private JavaCompletionSorting() {
  }

  public static CompletionResultSet addJavaSorting(final CompletionParameters parameters, CompletionResultSet result) {
    final PsiElement position = parameters.getPosition();
    final ExpectedTypeInfo[] expectedTypes = PsiJavaPatterns.psiElement().beforeLeaf(PsiJavaPatterns.psiElement().withText(".")).accepts(position) ? ExpectedTypeInfo.EMPTY_ARRAY : JavaSmartCompletionContributor.getExpectedTypes(parameters);
    final CompletionType type = parameters.getCompletionType();

    final boolean smart = type == CompletionType.SMART;
    CompletionSorter sorter = CompletionSorter.defaultSorter(parameters);
    if (!smart && JavaSmartCompletionContributor.AFTER_NEW.accepts(position)) {
      sorter = sorter.weighBefore("liftShorter", new LookupElementWeigher("expectedAfterNew") {
        @NotNull
        @Override
        public Comparable weigh(@NotNull LookupElement element) {
          return -((Enum)PreferExpectedTypeWeigher.weigh(element, expectedTypes)).ordinal();
        }
      });
    }
    sorter = sorter.weighAfter("negativeStats", new PreferLocalVariablesLiteralsAndAnnoMethodsWeigher(type));
    if (!smart) {
      sorter = preferStatics(sorter, position);
    }
    if (smart) {
      sorter = sorter.weighAfter("negativeStats", new PreferDefaultTypeWeigher(expectedTypes, parameters));
    }
    sorter = recursion(sorter, parameters, expectedTypes);
    return result.withRelevanceSorter(sorter);
  }

  static CompletionSorter recursion(CompletionSorter sorter, CompletionParameters parameters, final ExpectedTypeInfo[] expectedInfos) {

    final PsiElement position = parameters.getPosition();
    final PsiMethod positionMethod = PsiTreeUtil.getParentOfType(position, PsiMethod.class, false);
    final ElementFilter filter = JavaCompletionUtil.recursionFilter(position);
    final PsiMethodCallExpression expression = PsiTreeUtil.getParentOfType(position, PsiMethodCallExpression.class, true, PsiClass.class);
    final PsiReferenceExpression reference = expression != null ? expression.getMethodExpression() : PsiTreeUtil.getParentOfType(position, PsiReferenceExpression.class);
    if (reference == null) return sorter;

    return sorter.weighAfter("local", new RecursionWeigher(filter, position, reference, expression, positionMethod, expectedInfos));
  }

  static CompletionSorter preferStatics(CompletionSorter sorter, PsiElement position) {
    if (PsiTreeUtil.getParentOfType(position, PsiDocComment.class) != null) {
      return sorter;
    }
    if (position.getParent() instanceof PsiReferenceExpression) {
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)position.getParent();
      final PsiElement qualifier = refExpr.getQualifier();
      if (qualifier == null) {
        return sorter;
      }
      if (!(qualifier instanceof PsiJavaCodeReferenceElement) || !(((PsiJavaCodeReferenceElement)qualifier).resolve() instanceof PsiClass)) {
        return sorter;
      }
    }

    return sorter.weighAfter("negativeStats", new LookupElementWeigher("statics") {
      @NotNull
      @Override
      public Comparable weigh(@NotNull LookupElement element) {
        final Object o = element.getObject();
        if (!(o instanceof PsiMember)) return 0;

        if (((PsiMember)o).hasModifierProperty(PsiModifier.STATIC)) {
          if (o instanceof PsiMethod) return -5;
          if (o instanceof PsiField) return -4;
        }

        if (o instanceof PsiClass) return -3;

        //instance method or field
        return -5;
      }
    });
  }

  private static class PreferDefaultTypeWeigher extends LookupElementWeigher {
    private final PsiTypeParameter myTypeParameter;
    private final ExpectedTypeInfo[] myExpectedTypes;
    private final CompletionParameters myParameters;
    private final CompletionLocation myLocation;

    public PreferDefaultTypeWeigher(ExpectedTypeInfo[] expectedTypes, CompletionParameters parameters) {
      super("defaultType");
      myExpectedTypes = expectedTypes;
      myParameters = parameters;

      final Pair<PsiClass,Integer> pair = JavaSmartCompletionContributor.getTypeParameterInfo(parameters.getPosition());
      myTypeParameter = pair == null ? null : pair.first.getTypeParameters()[pair.second.intValue()];
      myLocation = new CompletionLocation(myParameters);
    }

    @NotNull
    @Override
    public Comparable weigh(@NotNull LookupElement item) {
      final Object object = item.getObject();

      if (object instanceof PsiClass) {
        if (myTypeParameter != null && object.equals(PsiUtil.resolveClassInType(TypeConversionUtil.typeParameterErasure(myTypeParameter)))) {
          return MyResult.exactlyExpected;
        }
      }

      if (myExpectedTypes == null) return MyResult.normal;

      PsiType itemType = JavaCompletionUtil.getLookupElementType(item);
      if (itemType == null || !itemType.isValid()) return MyResult.normal;

      if (object instanceof PsiClass) {
        for (final ExpectedTypeInfo info : myExpectedTypes) {
          if (TypeConversionUtil.erasure(info.getType().getDeepComponentType()).equals(TypeConversionUtil.erasure(itemType))) {
            return AbstractExpectedTypeSkipper.skips(item, myLocation) ? MyResult.expectedNoSelect : MyResult.exactlyExpected;
          }
        }
      }

      for (final ExpectedTypeInfo expectedInfo : myExpectedTypes) {
        final PsiType defaultType = expectedInfo.getDefaultType();
        final PsiType expectedType = expectedInfo.getType();
        if (!expectedType.isValid()) {
          return MyResult.normal;
        }

        if (defaultType != expectedType) {
          if (defaultType.equals(itemType)) {
            return MyResult.exactlyDefault;
          }

          if (defaultType.isAssignableFrom(itemType)) {
            return MyResult.ofDefaultType;
          }
        }
        if (PsiType.VOID.equals(itemType) && PsiType.VOID.equals(expectedType)) {
          return MyResult.exactlyExpected;
        }
      }

      return MyResult.normal;
    }

    private enum MyResult {
      expectedNoSelect,
      exactlyDefault,
      ofDefaultType,
      exactlyExpected,
      normal,
    }

  }

  private static class RecursionWeigher extends LookupElementWeigher {
    private final ElementFilter myFilter;
    private final PsiElement myPosition;
    private final PsiReferenceExpression myReference;
    private final PsiMethodCallExpression myExpression;
    private final PsiMethod myPositionMethod;
    private final ExpectedTypeInfo[] myExpectedInfos;
    private final PsiExpression myQualifier;
    private final boolean myDelegate;

    public RecursionWeigher(ElementFilter filter,
                            PsiElement position,
                            @NotNull PsiReferenceExpression reference,
                            PsiMethodCallExpression expression,
                            PsiMethod positionMethod, ExpectedTypeInfo[] expectedInfos) {
      super("recursion");
      myFilter = filter;
      myPosition = position;
      myReference = reference;
      myExpression = expression;
      myPositionMethod = positionMethod;
      myExpectedInfos = expectedInfos;
      myQualifier = myReference.getQualifierExpression();
      myDelegate = myQualifier != null && !(myQualifier instanceof PsiThisExpression);
    }

    enum Result {
      delegation,
      normal,
      passingObjectToItself,
      recursive,
    }

    @NotNull
    @Override
    public Result weigh(@NotNull LookupElement element) {
      final Object object = element.getObject();
      if (!(object instanceof PsiMethod || object instanceof PsiVariable || object instanceof PsiExpression)) return Result.normal;

      if (myFilter != null && !myFilter.isAcceptable(object, myPosition)) {
        return Result.recursive;
      }

      if (isPassingObjectToItself(object)) {
        return Result.passingObjectToItself;
      }

      if (myExpression != null && myPositionMethod != null) {
        if (myExpectedInfos != null) {
          final PsiType itemType = JavaCompletionUtil.getLookupElementType(element);
          if (itemType != null) {
            for (final ExpectedTypeInfo expectedInfo : myExpectedInfos) {
              if (myPositionMethod.equals(expectedInfo.getCalledMethod()) && expectedInfo.getType().isAssignableFrom(itemType)) {
                return myDelegate ? Result.delegation : Result.recursive;
              }
            }
          }
        }
        return Result.normal;
      }

      if (object instanceof PsiMethod && myPositionMethod != null) {
        final PsiMethod method = (PsiMethod)object;
        if (PsiTreeUtil.isAncestor(myReference, myPosition, false) &&
            Comparing.equal(method.getName(), myPositionMethod.getName())) {
          if (!myDelegate && findDeepestSuper(method).equals(findDeepestSuper(myPositionMethod))) {
            return Result.recursive;
          }
          return Result.delegation;
        }
      }

      return Result.normal;
    }

    private boolean isPassingObjectToItself(Object object) {
      if (object instanceof PsiThisExpression) {
        return !myDelegate || myQualifier instanceof PsiSuperExpression;
      }
      return myQualifier instanceof PsiReferenceExpression &&
             object.equals(((PsiReferenceExpression)myQualifier).advancedResolve(true).getElement());
    }

    @NotNull
    private static PsiMethod findDeepestSuper(@NotNull final PsiMethod method) {
      final PsiMethod first = DeepestSuperMethodsSearch.search(method).findFirst();
      return first == null ? method : first;
    }
  }
}
