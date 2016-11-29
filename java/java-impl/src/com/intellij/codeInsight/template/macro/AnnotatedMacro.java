/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMember;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedMembersSearch;
import com.intellij.util.Query;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Maxim.Mossienko
 */
public class AnnotatedMacro extends Macro {

  @Override
  @NonNls
  public String getName() {
    return "annotated";
  }

  @Override
  public String getPresentableName() {
    return "annotated(\"annotation qname\")";
  }

  @Nullable
  private static Query<PsiMember> findAnnotated(ExpressionContext context, Expression[] params) {
    if (params == null || params.length == 0) return null;
    PsiManager instance = PsiManager.getInstance(context.getProject());

    final String paramResult = params[0].calculateResult(context).toString();
    if (paramResult == null) return null;
    final GlobalSearchScope scope = GlobalSearchScope.allScope(context.getProject());
    final PsiClass myBaseClass = JavaPsiFacade.getInstance(instance.getProject()).findClass(paramResult,  scope);

    if (myBaseClass != null) {
      return AnnotatedMembersSearch.search(myBaseClass, scope);
    }
    return null;
  }

  @Override
  public Result calculateResult(@NotNull Expression[] expressions, ExpressionContext expressionContext) {
    final Query<PsiMember> psiMembers = findAnnotated(expressionContext, expressions);

    if (psiMembers != null) {
      final PsiMember member = psiMembers.findFirst();

      if (member != null) {
        return new TextResult(member instanceof PsiClass ? ((PsiClass)member).getQualifiedName():member.getName());
      }
    }
    return null;
  }

  @Override
  public Result calculateQuickResult(@NotNull Expression[] expressions, ExpressionContext expressionContext) {
    return calculateResult(expressions, expressionContext);
  }

  @Override
  public LookupElement[] calculateLookupItems(@NotNull Expression[] params, ExpressionContext context) {
    final Query<PsiMember> query = findAnnotated(context, params);

    if (query != null) {
      Set<LookupElement> set = new LinkedHashSet<>();
      final String secondParamValue = params.length > 1 ? params[1].calculateResult(context).toString() : null;
      final boolean isShortName = secondParamValue != null && !Boolean.valueOf(secondParamValue);
      final Project project = context.getProject();
      final PsiClass findInClass = secondParamValue != null
                                   ? JavaPsiFacade.getInstance(project).findClass(secondParamValue, GlobalSearchScope.allScope(project))
                                   : null;

      for (PsiMember object : query.findAll()) {
        if (findInClass != null && !object.getContainingClass().equals(findInClass)) continue;
        boolean isClazz = object instanceof PsiClass;
        final String name = isShortName || !isClazz ? object.getName() : ((PsiClass) object).getQualifiedName();
        set.add(LookupElementBuilder.create(name));
      }

      return set.toArray(new LookupElement[set.size()]);
    }
    return LookupElement.EMPTY_ARRAY;
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }

}
