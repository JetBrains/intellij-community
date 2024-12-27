// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.*;
import com.intellij.java.JavaBundle;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiElementProcessorAdapter;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class DescendantClassesEnumMacro extends Macro {
  @Override
  public String getName() {
    return "descendantClassesEnum";
  }

  @Override
  public String getPresentableName() {
    return JavaBundle.message("macro.descendant.classes.enum");
  }

  @Override
  public Result calculateResult(Expression @NotNull [] params, ExpressionContext context) {
    final List<PsiClass> classes = findDescendants(context, params);
    if (classes == null || classes.isEmpty()) return null;
    Result[] results = calculateResults(classes);

    return results[0];
  }

  private static Result[] calculateResults(final List<? extends PsiClass> classes) {
    Result[] results = new Result[classes.size()];
    int i = 0;

    for (final PsiClass aClass : classes) {
      results[i++] = new JavaPsiElementResult(aClass);
    }
    return results;
  }

  private static @Nullable List<PsiClass> findDescendants(ExpressionContext context, Expression[] params) {
    if (params == null || params.length == 0) return null;
    PsiManager instance = PsiManager.getInstance(context.getProject());

    Result result = params[0].calculateResult(context);
    if (result == null) return null;
    
    final String paramResult = result.toString();
    if (paramResult == null) return null;

    final boolean isAllowAbstract = isAllowAbstract(context, params);
    final PsiClass myBaseClass =
      JavaPsiFacade.getInstance(instance.getProject()).findClass(paramResult, GlobalSearchScope.allScope(context.getProject()));

    if (myBaseClass != null) {
      final List<PsiClass> classes = new ArrayList<>();

      ClassInheritorsSearch.search(myBaseClass).forEach(new PsiElementProcessorAdapter<>(new PsiElementProcessor<>() {
        @Override
        public boolean execute(@NotNull PsiClass element) {
          if (isAllowAbstract || !isAbstractOrInterface(element)) {
            classes.add(element);
          }
          return true;
        }
      }));

      return classes;
    }

    return null;
  }

  @Override
  public Result calculateQuickResult(Expression @NotNull [] params, ExpressionContext context) {
    final List<PsiClass> classes = findDescendants(context, params);
    if (classes == null || classes.isEmpty()) return null;
    Result[] results = calculateResults(classes);

    return results[0];
  }

  @Override
  public LookupElement[] calculateLookupItems(Expression @NotNull [] params, ExpressionContext context) {
    final List<PsiClass> classes = findDescendants(context, params);
    if (classes == null || classes.isEmpty()) return null;

    Set<LookupElement> set = new LinkedHashSet<>();
    boolean isShortName = params.length > 1 && !Boolean.parseBoolean(params[1].calculateResult(context).toString());

    for (PsiClass object : classes) {
      final String name = isShortName ? object.getName() : object.getQualifiedName();
      if (name != null && !name.isEmpty()) {
        set.add(LookupElementBuilder.create(name));
      }
    }

    return set.toArray(LookupElement.EMPTY_ARRAY);
  }

  private static boolean isAbstractOrInterface(final PsiClass psiClass) {
    final PsiModifierList modifierList = psiClass.getModifierList();

    return psiClass.isInterface() || (modifierList != null && modifierList.hasModifierProperty(PsiModifier.ABSTRACT));
  }

  private static boolean isAllowAbstract(final ExpressionContext context, final Expression[] params) {
      return params.length > 2 ? Boolean.parseBoolean(params[2].calculateResult(context).toString()) : true;
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }

}