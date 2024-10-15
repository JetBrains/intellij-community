// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedMembersSearch;
import com.intellij.util.Query;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

public final class AnnotationTypeMacro extends Macro {

  @Override
  public @NonNls String getName() {
    return "annotation";
  }

  @Override
  public String getPresentableName() {
    return "annotation(\"base annotation qname\", \"include base annotation\")";
  }

  private static @Nullable Query<PsiMember> findAnnotated(ExpressionContext context, PsiClass myBaseClass) {
    final GlobalSearchScope scope = context.getPsiElementAtStartOffset().getResolveScope();

    if (myBaseClass != null) {
      return AnnotatedMembersSearch.search(myBaseClass, scope).filtering(member -> member instanceof PsiClass annotation && annotation.isAnnotationType());
    }
    return null;
  }

  @Override
  public Result calculateResult(Expression @NotNull [] expressions, ExpressionContext expressionContext) {
    final PsiClass myBaseClass = getBaseClass(expressions, expressionContext);
    if (myBaseClass == null) return null;
    final boolean includeBaseClass =
      expressions.length > 1 && Boolean.parseBoolean(expressions[1].calculateResult(expressionContext).toString());

    if (includeBaseClass) {
      return new TextResult(myBaseClass.getQualifiedName());
    }

    final Query<PsiMember> psiMembers = findAnnotated(expressionContext, myBaseClass);

    if (psiMembers != null) {
      final PsiMember member = psiMembers.findFirst();

      if (member != null) {
        return new TextResult(member instanceof PsiClass ? ((PsiClass)member).getQualifiedName() : member.getName());
      }
    }
    return null;
  }

  private static @Nullable PsiClass getBaseClass(Expression[] expressions, ExpressionContext expressionContext) {
    if (expressions == null || expressions.length == 0) return null;
    final String paramResult = expressions[0].calculateResult(expressionContext).toString();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(expressionContext.getProject());
    return JavaPsiFacade.getInstance(expressionContext.getProject()).findClass(paramResult, scope);
  }

  @Override
  public Result calculateQuickResult(Expression @NotNull [] expressions, ExpressionContext expressionContext) {
    return calculateResult(expressions, expressionContext);
  }

  @Override
  public LookupElement[] calculateLookupItems(Expression @NotNull [] params, ExpressionContext context) {
    final PsiClass myBaseClass = getBaseClass(params, context);
    if (myBaseClass == null) return LookupElement.EMPTY_ARRAY;

    final boolean includeBaseClass =
      params.length > 1 && Boolean.parseBoolean(params[1].calculateResult(context).toString());

    final Query<PsiMember> query = findAnnotated(context, myBaseClass);

    Set<LookupElement> set = new LinkedHashSet<>();
    if (includeBaseClass) {
      LookupElementBuilder lookupElement = createLookupElement(myBaseClass);
      if (lookupElement != null) {
        set.add(lookupElement);
      }
    }

    if (query != null) {

      for (PsiMember object : query.findAll()) {
        LookupElementBuilder lookupElement = createLookupElement(object);
        if (lookupElement == null) continue;
        set.add(lookupElement);
      }

      return set.toArray(LookupElement.EMPTY_ARRAY);
    }
    return LookupElement.EMPTY_ARRAY;
  }

  private static @Nullable LookupElementBuilder createLookupElement(PsiMember object) {
    if (!(object instanceof PsiClass psiClass)) return null;
    final String name = object.getName();
    String qualifiedName = psiClass.getQualifiedName();
    if (name == null || qualifiedName == null) return null;
    final String packageName = StringUtil.substringBefore(qualifiedName, "." + name);
    return LookupElementBuilder.create(qualifiedName)
      .withPresentableText(name)
      .withPsiElement(psiClass)
      .withLookupString(qualifiedName)
      .withIcon(object.getIcon(0))
      .withTypeText(packageName);
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }
}
