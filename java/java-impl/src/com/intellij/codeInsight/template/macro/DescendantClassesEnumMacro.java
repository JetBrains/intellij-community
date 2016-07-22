/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.*;
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

public class DescendantClassesEnumMacro extends Macro {
  @Override
  public String getName() {
    return "descendantClassesEnum";
  }

  @Override
  public String getPresentableName() {
    return CodeInsightBundle.message("macro.descendant.classes.enum");
  }

  @Override
  public Result calculateResult(@NotNull Expression[] params, ExpressionContext context) {
    final List<PsiClass> classes = findDescendants(context, params);
    if (classes == null || classes.size() == 0) return null;
    Result[] results = calculateResults(classes);

    return results[0];
  }

  private static Result[] calculateResults(final List<PsiClass> classes) {
    Result[] results = new Result[classes.size()];
    int i = 0;

    for (final PsiClass aClass : classes) {
      results[i++] = new JavaPsiElementResult(aClass);
    }
    return results;
  }

  @Nullable
  private static List<PsiClass> findDescendants(ExpressionContext context, Expression[] params) {
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

      ClassInheritorsSearch.search(myBaseClass).forEach(new PsiElementProcessorAdapter<>(new PsiElementProcessor<PsiClass>() {
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
  public Result calculateQuickResult(@NotNull Expression[] params, ExpressionContext context) {
    final List<PsiClass> classes = findDescendants(context, params);
    if (classes == null || classes.size() == 0) return null;
    Result[] results = calculateResults(classes);

    return results[0];
  }

  @Override
  public LookupElement[] calculateLookupItems(@NotNull Expression[] params, ExpressionContext context) {
    final List<PsiClass> classes = findDescendants(context, params);
    if (classes == null || classes.size() == 0) return null;

    Set<LookupElement> set = new LinkedHashSet<>();
    boolean isShortName = params.length > 1 && !Boolean.valueOf(params[1].calculateResult(context).toString());

    for (PsiClass object : classes) {
      final String name = isShortName ? object.getName() : object.getQualifiedName();
      if (name != null && name.length() > 0) {
        set.add(LookupElementBuilder.create(name));
      }
    }

    return set.toArray(new LookupElement[set.size()]);
  }

  private static boolean isAbstractOrInterface(final PsiClass psiClass) {
    final PsiModifierList modifierList = psiClass.getModifierList();

    return psiClass.isInterface() || (modifierList != null && modifierList.hasModifierProperty(PsiModifier.ABSTRACT));
  }

  private static boolean isAllowAbstract(final ExpressionContext context, final Expression[] params) {
      return params.length > 2 ? Boolean.valueOf(params[2].calculateResult(context).toString()) : true;
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }

}