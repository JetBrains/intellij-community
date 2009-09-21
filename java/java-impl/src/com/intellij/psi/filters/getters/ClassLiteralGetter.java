package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ClassLiteralGetter {
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.filters.getters.ClassLiteralGetter");

  private ClassLiteralGetter() {
  }

  public static LookupElement[] getClassLiterals(PsiElement context, CompletionContext completionContext, final PrefixMatcher matcher, ContextGetter myBaseGetter) {
    final Condition<String> shortNameCondition = new Condition<String>() {
      public boolean value(String s) {
        return matcher.prefixMatches(s);
      }
    };

    final List<LookupElement> result = new ArrayList<LookupElement>();
    for (final Object element : myBaseGetter.get(context, completionContext)) {
      if (element instanceof PsiClassType) {
        PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)element).resolveGenerics();
        PsiClass psiClass = resolveResult.getElement();
        if (psiClass != null && CommonClassNames.JAVA_LANG_CLASS.equals(psiClass.getQualifiedName())) {
          final PsiTypeParameter[] typeParameters = psiClass.getTypeParameters();
          if (typeParameters.length == 1) {
            PsiType substitution = resolveResult.getSubstitutor().substitute(typeParameters[0]);
            boolean addInheritors = false;
            if (substitution instanceof PsiWildcardType) {
              final PsiWildcardType wildcardType = (PsiWildcardType)substitution;
              substitution = wildcardType.getBound();
              addInheritors = wildcardType.isExtends();
            }

            final PsiClass aClass = PsiUtil.resolveClassInType(substitution);
            if (aClass == null) continue;

            createLookupElement(substitution, result, context);
            if (addInheritors && substitution != null && !CommonClassNames.JAVA_LANG_OBJECT.equals(substitution.getCanonicalText())) {
              for (final PsiType type : CodeInsightUtil.addSubtypes(substitution, context, true, shortNameCondition)) {
                createLookupElement(type, result, context);
              }
            }

          }
        }
      }
    }

    return result.toArray(new LookupElement[result.size()]);
  }

  private static void createLookupElement(@Nullable final PsiType type, final List<LookupElement> list, PsiElement context) {
    if (type instanceof PsiClassType && !((PsiClassType)type).hasParameters() && !(((PsiClassType) type).resolve() instanceof PsiTypeParameter)) {
      try {
        list.add(AutoCompletionPolicy.NEVER_AUTOCOMPLETE.applyPolicy(new ClassLiteralLookupElement((PsiClassType)type, context)));
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }
}
