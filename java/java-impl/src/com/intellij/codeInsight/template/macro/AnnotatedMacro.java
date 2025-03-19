// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
public final class AnnotatedMacro extends Macro {

  @Override
  public @NonNls String getName() {
    return "annotated";
  }

  @Override
  public String getPresentableName() {
    return "annotated(\"annotation qname\")";
  }

  private static @Nullable Query<PsiMember> findAnnotated(ExpressionContext context, Expression[] params) {
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
  public Result calculateResult(Expression @NotNull [] expressions, ExpressionContext expressionContext) {
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
  public Result calculateQuickResult(Expression @NotNull [] expressions, ExpressionContext expressionContext) {
    return calculateResult(expressions, expressionContext);
  }

  @Override
  public LookupElement[] calculateLookupItems(Expression @NotNull [] params, ExpressionContext context) {
    final Query<PsiMember> query = findAnnotated(context, params);

    if (query != null) {
      Set<LookupElement> set = new LinkedHashSet<>();
      final String secondParamValue = params.length > 1 ? params[1].calculateResult(context).toString() : null;
      final boolean isShortName = secondParamValue != null && !Boolean.parseBoolean(secondParamValue);
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

      return set.toArray(LookupElement.EMPTY_ARRAY);
    }
    return LookupElement.EMPTY_ARRAY;
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }

}
