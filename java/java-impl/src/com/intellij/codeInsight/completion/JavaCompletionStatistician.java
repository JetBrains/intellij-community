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
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.statistics.JavaStatisticsManager;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;

/**
 * @author peter
 */
public class JavaCompletionStatistician extends CompletionStatistician{
  private static final ElementPattern<PsiElement> SUPER_CALL = psiElement().afterLeaf(psiElement().withText(".").afterLeaf(PsiKeyword.SUPER));

  @Override
  public StatisticsInfo serialize(final LookupElement element, final CompletionLocation location) {
    Object o = element.getObject();

    if (o instanceof PsiLocalVariable || o instanceof PsiParameter || o instanceof PsiThisExpression || o instanceof PsiKeyword) {
      return StatisticsInfo.EMPTY;
    }

    PsiElement position = location.getCompletionParameters().getPosition();
    if (SUPER_CALL.accepts(position) || ReferenceExpressionCompletionContributor.IN_SWITCH_LABEL.accepts(position)) {
      return StatisticsInfo.EMPTY;
    }

    LookupItem item = element.as(LookupItem.CLASS_CONDITION_KEY);
    if (item == null) return null;

    PsiType qualifierType = JavaCompletionUtil.getQualifierType(item);

    if (o instanceof PsiMember) {
      final ExpectedTypeInfo[] infos = JavaCompletionUtil.EXPECTED_TYPES.getValue(location);
      final ExpectedTypeInfo firstInfo = infos != null && infos.length > 0 ? infos[0] : null;
      String key2 = JavaStatisticsManager.getMemberUseKey2((PsiMember)o);
      if (o instanceof PsiClass) {
        PsiType expectedType = firstInfo != null ? firstInfo.getDefaultType() : null;
        return new StatisticsInfo(JavaStatisticsManager.getAfterNewKey(expectedType), key2);
      }

      PsiClass containingClass = ((PsiMember)o).getContainingClass();
      if (containingClass != null) {
        String expectedName = firstInfo instanceof ExpectedTypeInfoImpl ? ((ExpectedTypeInfoImpl)firstInfo).getExpectedName() : null;
        String contextPrefix = expectedName == null ? "" : "expectedName=" + expectedName + "###";
        String context = contextPrefix + JavaStatisticsManager.getMemberUseKey2(containingClass);

        if (o instanceof PsiMethod) {
          String memberValue = JavaStatisticsManager.getMemberUseKey2(RecursionWeigher.findDeepestSuper((PsiMethod)o));

          List<StatisticsInfo> superMethodInfos = ContainerUtil.newArrayList(new StatisticsInfo(contextPrefix + context, memberValue));
          for (PsiClass superClass : InheritanceUtil.getSuperClasses(containingClass)) {
            superMethodInfos.add(new StatisticsInfo(contextPrefix + JavaStatisticsManager.getMemberUseKey2(superClass), memberValue));
          }
          return StatisticsInfo.createComposite(superMethodInfos);
        }

        return new StatisticsInfo(context, key2);
      }
    }

    if (qualifierType != null) return StatisticsInfo.EMPTY;

    return null;
  }

}
