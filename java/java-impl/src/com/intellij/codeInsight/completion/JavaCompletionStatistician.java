/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.statistics.JavaStatisticsManager;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nullable;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;

/**
 * @author peter
 */
public class JavaCompletionStatistician extends CompletionStatistician{
  private static final ElementPattern<PsiElement> SUPER_CALL = psiElement().afterLeaf(psiElement().withText(".").afterLeaf(PsiKeyword.SUPER));

  @Override
  public StatisticsInfo serialize(final LookupElement element, final CompletionLocation location) {
    Object o = element.getObject();

    if (o instanceof PsiLocalVariable || o instanceof PsiParameter || 
        o instanceof PsiThisExpression || o instanceof PsiKeyword || 
        element.getUserData(JavaCompletionUtil.SUPER_METHOD_PARAMETERS) != null ||
        FunctionalExpressionCompletionProvider.isFunExprItem(element)) {
      return StatisticsInfo.EMPTY;
    }

    if (!(o instanceof PsiMember)) {
      return null;
    }

    PsiElement position = location.getCompletionParameters().getPosition();
    if (SUPER_CALL.accepts(position) || ReferenceExpressionCompletionContributor.IN_SWITCH_LABEL.accepts(position)) {
      return StatisticsInfo.EMPTY;
    }

    ExpectedTypeInfo firstInfo = getExpectedTypeInfo(location);
    if (firstInfo != null && isInEnumAnnotationParameter(position, firstInfo)) {
      return StatisticsInfo.EMPTY;
    }
    
    if (o instanceof PsiClass) {
      return getClassInfo((PsiClass)o, position, firstInfo);
    }
    return getFieldOrMethodInfo((PsiMember)o, element, firstInfo);
  }

  private static boolean isInEnumAnnotationParameter(PsiElement position, ExpectedTypeInfo firstInfo) {
    if (PsiTreeUtil.getParentOfType(position, PsiNameValuePair.class) == null) return false;
    
    PsiClass expectedClass = PsiUtil.resolveClassInType(firstInfo.getType());
    return expectedClass != null && expectedClass.isEnum();
  }

  @Nullable
  private static ExpectedTypeInfo getExpectedTypeInfo(CompletionLocation location) {
    ExpectedTypeInfo[] infos = JavaCompletionUtil.EXPECTED_TYPES.getValue(location);
    return infos != null && infos.length > 0 ? infos[0] : null;
  }

  private static StatisticsInfo getClassInfo(PsiClass psiClass, PsiElement position, @Nullable ExpectedTypeInfo firstInfo) {
    if (PreferByKindWeigher.isInMethodTypeArg(position)) {
      return StatisticsInfo.EMPTY;
    }

    PsiType expectedType = firstInfo != null ? firstInfo.getDefaultType() : null;
    String context =
      JavaClassNameCompletionContributor.AFTER_NEW.accepts(position) ? JavaStatisticsManager.getAfterNewKey(expectedType) :
      PreferByKindWeigher.isExceptionPosition(position) ? "exception" :
      "";
    return new StatisticsInfo(context, JavaStatisticsManager.getMemberUseKey2(psiClass));
  }

  @Nullable
  private static StatisticsInfo getFieldOrMethodInfo(PsiMember member, LookupElement item, @Nullable ExpectedTypeInfo firstInfo) {
    PsiClass containingClass = member.getContainingClass();
    if (containingClass == null) return null;

    String expectedName = firstInfo instanceof ExpectedTypeInfoImpl ? ((ExpectedTypeInfoImpl)firstInfo).getExpectedName() : null;
    PsiType qualifierType = JavaCompletionUtil.getQualifierType(item);
    String contextPrefix = (qualifierType == null ? "" : JavaStatisticsManager.getMemberUseKey1(qualifierType) + "###") +
                           (expectedName == null ? "" : "expectedName=" + expectedName + "###");

    if (member instanceof PsiMethod) {
      String memberValue = JavaStatisticsManager.getMemberUseKey2(RecursionWeigher.findDeepestSuper((PsiMethod)member));
      return new StatisticsInfo(contextPrefix, memberValue);
    }

    return new StatisticsInfo(contextPrefix, JavaStatisticsManager.getMemberUseKey2(member));
  }
}
