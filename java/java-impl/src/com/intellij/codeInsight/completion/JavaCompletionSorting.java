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
import com.intellij.codeInsight.ExpectedTypeInfoImpl;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementWeigher;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.searches.DeepestSuperMethodsSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public class JavaCompletionSorting {
  private JavaCompletionSorting() {
  }

  public static CompletionResultSet addJavaSorting(final CompletionParameters parameters, CompletionResultSet result) {
    String prefix = result.getPrefixMatcher().getPrefix();
    final PsiElement position = parameters.getPosition();
    final ExpectedTypeInfo[] expectedTypes = PsiJavaPatterns.psiElement().beforeLeaf(PsiJavaPatterns.psiElement().withText(".")).accepts(position) ? ExpectedTypeInfo.EMPTY_ARRAY : JavaSmartCompletionContributor.getExpectedTypes(parameters);
    final CompletionType type = parameters.getCompletionType();
    final boolean smart = type == CompletionType.SMART;
    final boolean afterNew = JavaSmartCompletionContributor.AFTER_NEW.accepts(position);

    List<LookupElementWeigher> afterNegativeStats = new ArrayList<LookupElementWeigher>();
    ContainerUtil.addIfNotNull(afterNegativeStats, smart ? new PreferDefaultTypeWeigher(expectedTypes, parameters) : preferStatics(position));
    afterNegativeStats.add(new PreferLocalVariablesLiteralsAndAnnoMethodsWeigher(type));
    ContainerUtil.addIfNotNull(afterNegativeStats, recursion(parameters, expectedTypes));
    if (!smart && !afterNew) {
      afterNegativeStats.add(new PreferExpected(false, expectedTypes));
    }
    afterNegativeStats.add(new PreferSimilarlyEnding(expectedTypes, prefix));

    List<LookupElementWeigher> afterProximity = new ArrayList<LookupElementWeigher>();
    afterProximity.add(new PreferContainingSameWords(expectedTypes));
    if (smart) {
      afterProximity.add(new PreferFieldsAndGetters());
    }
    afterProximity.add(new PreferShorter(expectedTypes, prefix));

    CompletionSorter sorter = CompletionSorter.defaultSorter(parameters);
    if (!smart && afterNew) {
      sorter = sorter.weighBefore("liftShorter", new PreferExpected(true, expectedTypes));
    }
    sorter = sorter.weighAfter("negativeStats", afterNegativeStats.toArray(new LookupElementWeigher[afterNegativeStats.size()]));
    sorter = sorter.weighAfter("prefix", new PreferNonGeneric(), new PreferAccessible(position), new PreferSimple(), new PreferEnumConstants(parameters));
    sorter = sorter.weighAfter("proximity", afterProximity.toArray(new LookupElementWeigher[afterProximity.size()]));
    return result.withRelevanceSorter(sorter);
  }

  @Nullable
  private static LookupElementWeigher recursion(CompletionParameters parameters, final ExpectedTypeInfo[] expectedInfos) {

    final PsiElement position = parameters.getPosition();
    final PsiMethod positionMethod = PsiTreeUtil.getParentOfType(position, PsiMethod.class, false);
    final ElementFilter filter = JavaCompletionUtil.recursionFilter(position);
    final PsiMethodCallExpression expression = PsiTreeUtil.getParentOfType(position, PsiMethodCallExpression.class, true, PsiClass.class);
    final PsiReferenceExpression reference = expression != null ? expression.getMethodExpression() : PsiTreeUtil.getParentOfType(position, PsiReferenceExpression.class);
    if (reference == null) return null;

    return new RecursionWeigher(filter, position, reference, expression, positionMethod, expectedInfos);
  }

  @Nullable
  private static LookupElementWeigher preferStatics(PsiElement position) {
    if (PsiTreeUtil.getParentOfType(position, PsiDocComment.class) != null) {
      return null;
    }
    if (position.getParent() instanceof PsiReferenceExpression) {
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)position.getParent();
      final PsiElement qualifier = refExpr.getQualifier();
      if (qualifier == null) {
        return null;
      }
      if (!(qualifier instanceof PsiJavaCodeReferenceElement) || !(((PsiJavaCodeReferenceElement)qualifier).resolve() instanceof PsiClass)) {
        return null;
      }
    }

    return new LookupElementWeigher("statics") {
      @NotNull
      @Override
      public Comparable weigh(@NotNull LookupElement element) {
        final Object o = element.getObject();
        if (o instanceof PsiKeyword) return -3;
        if (!(o instanceof PsiMember)) return 0;

        if (((PsiMember)o).hasModifierProperty(PsiModifier.STATIC)) {
          if (o instanceof PsiMethod) return -5;
          if (o instanceof PsiField) return -4;
        }

        if (o instanceof PsiClass) return -3;

        //instance method or field
        return -5;
      }
    };
  }

  private static ExpectedTypeMatching getExpectedTypeMatching(LookupElement item, ExpectedTypeInfo[] expectedInfos) {
    PsiType itemType = JavaCompletionUtil.getLookupElementType(item);
    if (itemType == null) return ExpectedTypeMatching.normal;

    for (final ExpectedTypeInfo expectedInfo : expectedInfos) {
      final PsiType defaultType = expectedInfo.getDefaultType();
      final PsiType expectedType = expectedInfo.getType();
      if (!expectedType.isValid()) {
        return ExpectedTypeMatching.normal;
      }

      if (defaultType != expectedType && defaultType.isAssignableFrom(itemType)) {
        return ExpectedTypeMatching.ofDefaultType;
      }
      if (expectedType.isAssignableFrom(itemType)) {
        return ExpectedTypeMatching.expected;
      }
    }

    return ExpectedTypeMatching.normal;
  }

  @Nullable
  private static String getLookupObjectName(Object o) {
    if (o instanceof PsiVariable) {
      final PsiVariable variable = (PsiVariable)o;
      JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(variable.getProject());
      VariableKind variableKind = codeStyleManager.getVariableKind(variable);
      return codeStyleManager.variableNameToPropertyName(variable.getName(), variableKind);
    }
    if (o instanceof PsiMethod) {
      return ((PsiMethod)o).getName();
    }
    return null;
  }

  private static int getNameEndMatchingDegree(final String name, ExpectedTypeInfo[] expectedInfos, String prefix) {
    int res = 0;
    if (name != null && expectedInfos != null) {
      if (prefix.equals(name)) {
        res = Integer.MAX_VALUE;
      } else {
        final List<String> words = NameUtil.nameToWordsLowerCase(name);
        final List<String> wordsNoDigits = NameUtil.nameToWordsLowerCase(truncDigits(name));
        int max1 = calcMatch(words, 0, expectedInfos);
        max1 = calcMatch(wordsNoDigits, max1, expectedInfos);
        res = max1;
      }
    }

    return res;
  }

  private static String truncDigits(String name){
    int count = name.length() - 1;
    while (count >= 0) {
      char c = name.charAt(count);
      if (!Character.isDigit(c)) break;
      count--;
    }
    return name.substring(0, count + 1);
  }

  private static int calcMatch(final List<String> words, int max, ExpectedTypeInfo[] myExpectedInfos) {
    for (ExpectedTypeInfo myExpectedInfo : myExpectedInfos) {
      String expectedName = ((ExpectedTypeInfoImpl)myExpectedInfo).expectedName;
      if (expectedName == null) continue;
      max = calcMatch(expectedName, words, max);
      max = calcMatch(truncDigits(expectedName), words, max);
    }
    return max;
  }

  private static int calcMatch(final String expectedName, final List<String> words, int max) {
    if (expectedName == null) return max;

    String[] expectedWords = NameUtil.nameToWords(expectedName);
    int limit = Math.min(words.size(), expectedWords.length);
    for (int i = 0; i < limit; i++) {
      String word = words.get(words.size() - i - 1);
      String expectedWord = expectedWords[expectedWords.length - i - 1];
      if (word.equalsIgnoreCase(expectedWord)) {
        max = Math.max(max, i + 1);
      }
      else {
        break;
      }
    }
    return max;
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

    private enum Result {
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

  private enum ExpectedTypeMatching {
    ofDefaultType,
    expected,
    normal,
  }

  private static class PreferAccessible extends LookupElementWeigher {
    private final PsiElement myPosition;

    public PreferAccessible(PsiElement position) {
      super("accessible");
      myPosition = position;
    }

    private enum MyEnum {
      NORMAL,
      DEPRECATED,
      INACCESSIBLE,
    }

    @NotNull
    @Override
    public Comparable weigh(@NotNull LookupElement element) {
      final Object object = element.getObject();
      if (object instanceof PsiDocCommentOwner) {
        final PsiDocCommentOwner member = (PsiDocCommentOwner)object;
        if (!member.isValid()) {
          return MyEnum.NORMAL;
        }

        if (!JavaPsiFacade.getInstance(member.getProject()).getResolveHelper().isAccessible(member, myPosition, null)) return MyEnum.INACCESSIBLE;
        if (member.isDeprecated()) return MyEnum.DEPRECATED;
      }
      return MyEnum.NORMAL;
    }
  }

  private static class PreferNonGeneric extends LookupElementWeigher {
    public PreferNonGeneric() {
      super("nonGeneric");
    }

    @NotNull
    @Override
    public Comparable weigh(@NotNull LookupElement element) {
      final Object object = element.getObject();
      if (object instanceof PsiMethod) {
        PsiType type = ((PsiMethod)object).getReturnType();
        final JavaMethodCallElement callItem = element.as(JavaMethodCallElement.CLASS_CONDITION_KEY);
        if (callItem != null) {
          type = callItem.getSubstitutor().substitute(type);
        }

        if (type instanceof PsiClassType && ((PsiClassType) type).resolve() instanceof PsiTypeParameter) return 1;
      }

      return 0;
    }
  }

  private static class PreferSimple extends LookupElementWeigher {
    public PreferSimple() {
      super("simple");
    }

    @NotNull
    @Override
    public Comparable weigh(@NotNull LookupElement element) {
      final PsiTypeLookupItem lookupItem = element.as(PsiTypeLookupItem.CLASS_CONDITION_KEY);
      if (lookupItem != null) {
        return lookupItem.getBracketsCount();
      }
      if (element.as(CastingLookupElementDecorator.CLASS_CONDITION_KEY) != null) {
        return 239;
      }
      return 0;
    }
  }

  private static class PreferEnumConstants extends LookupElementWeigher {
    private final CompletionParameters myParameters;

    public PreferEnumConstants(CompletionParameters parameters) {
      super("constants");
      myParameters = parameters;
    }

    @NotNull
    @Override
    public Comparable weigh(@NotNull LookupElement element) {
      if (element.getObject() instanceof PsiEnumConstant) return -2;

      if (!(myParameters.getOriginalFile() instanceof PsiJavaFile)) return -1;

      if (PsiKeyword.TRUE.equals(element.getLookupString()) || PsiKeyword.FALSE.equals(element.getLookupString())) {
        boolean inReturn = PsiTreeUtil.getParentOfType(myParameters.getPosition(), PsiReturnStatement.class, false, PsiMember.class) != null;
        return inReturn ? -2 : 0;
      }

      return -1;
    }
  }

  private static class PreferExpected extends LookupElementWeigher {
    private final boolean myAcceptClasses;
    private final ExpectedTypeInfo[] myExpectedTypes;

    public PreferExpected(boolean acceptClasses, ExpectedTypeInfo[] expectedTypes) {
      super("expectedType");
      myAcceptClasses = acceptClasses;
      myExpectedTypes = expectedTypes;
    }

    @NotNull
    @Override
    public Comparable weigh(@NotNull LookupElement item) {
      return item.getObject() instanceof PsiClass && !myAcceptClasses
             ? ExpectedTypeMatching.normal : getExpectedTypeMatching(item, myExpectedTypes);
    }
  }

  private static class PreferSimilarlyEnding extends LookupElementWeigher {
    private final ExpectedTypeInfo[] myExpectedTypes;
    private final String myPrefix;

    public PreferSimilarlyEnding(ExpectedTypeInfo[] expectedTypes, String prefix) {
      super("nameEnd");
      myExpectedTypes = expectedTypes;
      myPrefix = prefix;
    }

    @NotNull
    @Override
    public Comparable weigh(@NotNull LookupElement element) {
      final String name = getLookupObjectName(element.getObject());
      return -getNameEndMatchingDegree(name, myExpectedTypes, myPrefix);
    }
  }

  private static class PreferContainingSameWords extends LookupElementWeigher {
    private final ExpectedTypeInfo[] myExpectedTypes;

    public PreferContainingSameWords(ExpectedTypeInfo[] expectedTypes) {
      super("sameWords");
      myExpectedTypes = expectedTypes;
    }

    @NotNull
    @Override
    public Comparable weigh(@NotNull LookupElement element) {
      final Object object = element.getObject();

      final String name = getLookupObjectName(object);
      if (name != null) {
        int max = 0;
        final List<String> wordsNoDigits = NameUtil.nameToWordsLowerCase(truncDigits(name));
        for (ExpectedTypeInfo myExpectedInfo : myExpectedTypes) {
          String expectedName = ((ExpectedTypeInfoImpl)myExpectedInfo).expectedName;
          if (expectedName != null) {
            final THashSet<String> set = new THashSet<String>(NameUtil.nameToWordsLowerCase(truncDigits(expectedName)));
            set.retainAll(wordsNoDigits);
            max = Math.max(max, set.size());
          }
        }
        return -max;
      }
      return 0;
    }
  }

  private static class PreferFieldsAndGetters extends LookupElementWeigher {
    public PreferFieldsAndGetters() {
      super("fieldsAndGetters");
    }

    @NotNull
    @Override
    public Comparable weigh(@NotNull LookupElement element) {
      final Object object = element.getObject();
      if (object instanceof PsiField) return -2;
      if (object instanceof PsiMethod && PropertyUtil.isSimplePropertyGetter((PsiMethod)object)) return -1;
      return 0;
    }
  }

  private static class PreferShorter extends LookupElementWeigher {
    private final ExpectedTypeInfo[] myExpectedTypes;
    private final String myPrefix;

    public PreferShorter(ExpectedTypeInfo[] expectedTypes, String prefix) {
      super("shorter");
      myExpectedTypes = expectedTypes;
      myPrefix = prefix;
    }

    @NotNull
    @Override
    public Comparable weigh(@NotNull LookupElement element) {
      final Object object = element.getObject();
      final String name = getLookupObjectName(object);

      if (name != null && getNameEndMatchingDegree(name, myExpectedTypes, myPrefix) != 0) {
        return NameUtil.nameToWords(name).length - 1000;
      }
      return 0;
    }
  }
}
