/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.util.SmartList;
import gnu.trove.THashSet;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Maxim.Mossienko
 */
public class ExprTypePredicate extends MatchPredicate {
  private final RegExpPredicate delegate;
  private final boolean withinHierarchy;
  private final boolean needsTypeParameters;
  private final boolean needsFullyQualified;
  private final boolean needsArrayType;

  public ExprTypePredicate(String type, String baseName, boolean _withinHierarchy, boolean caseSensitiveMatch, boolean target) {
    delegate = new RegExpPredicate(type, caseSensitiveMatch, baseName, false, target);
    withinHierarchy = _withinHierarchy;
    needsTypeParameters = type.indexOf('<') >= 0;
    needsFullyQualified = type.indexOf('.') >= 0;
    needsArrayType = type.indexOf('[') >= 0;
  }

  @Override
  public boolean match(PsiElement match, int start, int end, MatchContext context) {
    if (match instanceof PsiIdentifier) {
      // since we pickup tokens
      match = match.getParent();
    }
    else if (match instanceof PsiExpressionStatement) {
      match = ((PsiExpressionStatement)match).getExpression();
    }

    if (!(match instanceof PsiExpression)) {
      return false;
    }
    final PsiType type = evalType((PsiExpression)match, context);
    return type != null && doMatchWithTheType(type, context, match, null);
  }

  protected PsiType evalType(PsiExpression match, MatchContext context) {
    if (match instanceof PsiReferenceExpression) {
      final PsiElement parent = match.getParent();
      if (parent instanceof PsiMethodCallExpression) {
        return ((PsiMethodCallExpression)parent).getType();
      }
    }
    return match.getType();
  }

  private boolean doMatchWithTheType(final PsiType type, MatchContext context, PsiElement matchedNode, Set<PsiType> visited) {
    final List<String> permutations = getTextPermutations(type);
    for (String permutation : permutations) {
      if (delegate.doMatch(permutation, context, matchedNode)) {
        return true;
      }
    }
    if (withinHierarchy) {
      if (visited == null) {
        visited = new THashSet<>();
        visited.add(type);
      }
      for (PsiType superType : type.getSuperTypes()) {
        if (visited.add(superType) && doMatchWithTheType(superType, context, matchedNode, visited)) {
          return true;
        }
      }
    }
    return false;
  }

  private List<String> getTextPermutations(PsiType type) {
    final String dimensions;
    if (type instanceof PsiArrayType) {
      if (!needsArrayType) return Collections.emptyList();
      dimensions = StringUtil.repeat("[]", type.getArrayDimensions());
      type = type.getDeepComponentType();
    }
    else {
      dimensions = "";
    }
    final List<String> result = new SmartList<>();
    if (type instanceof PsiClassType) {
      final PsiClassType classType = (PsiClassType)type;
      final PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
      final PsiClass aClass = resolveResult.getElement();
      final StringBuilder typeText = new StringBuilder();
      if (aClass != null) {
        final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
        buildText(aClass, substitutor, true, typeText);
        typeText.append(dimensions);
        final String unqualified = typeText.toString();
        if (needsTypeParameters) {
          result.add(unqualified); // unqualified with type parameters if present
        }
        addWithoutTypeParameters(unqualified, dimensions, result);
        if (typeText.indexOf(".") >= 0) {
          typeText.setLength(0);
          buildText(aClass, substitutor, false, typeText);
          typeText.append(dimensions);
          final String outerClassQualified = typeText.toString();
          if (needsTypeParameters) {
            result.add(outerClassQualified); // inner class without enclosing class qualifier with type parameters if present
          }
          addWithoutTypeParameters(outerClassQualified, dimensions, result);
        }
        if (!needsFullyQualified) {
          return result;
        }
      }
    }
    final String fullyQualified = type.getCanonicalText() + dimensions;
    if (needsTypeParameters) {
      result.add(fullyQualified); // fully qualified if possible with type parameters if present
    }
    addWithoutTypeParameters(fullyQualified, dimensions, result);
    return result;
  }

  private static void addWithoutTypeParameters(String typeText, String suffix, List<String> result) {
    final int lt = typeText.indexOf("<");
    if (lt >= 0) {
      result.add(typeText.substring(0, lt) + suffix);
    }
    else if (result.isEmpty() || !result.get(result.size() - 1).equals(typeText)) {
      result.add(typeText);
    }
  }

  private static void buildText(PsiClass aClass, PsiSubstitutor substitutor, boolean qualifyInnerClasses, StringBuilder text) {
    if (aClass instanceof PsiAnonymousClass) {
      final PsiClassType baseClassType = ((PsiAnonymousClass)aClass).getBaseClassType();
      final PsiClassType.ClassResolveResult baseResolveResult = baseClassType.resolveGenerics();
      final PsiClass baseClass = baseResolveResult.getElement();
      if (baseClass != null) {
        buildText(baseClass, substitutor == null ? null : baseResolveResult.getSubstitutor(), qualifyInnerClasses, text);
      }
      else {
        text.append(baseClassType.getCanonicalText());
      }
      return;
    }
    if (qualifyInnerClasses) {
      final PsiElement parent = aClass.getParent();
      if (parent instanceof PsiClass && !(parent instanceof PsiAnonymousClass)) {
        buildText((PsiClass)parent, substitutor, true, text);
        text.append('.');
      }
    }
    text.append(aClass.getName());
    if (substitutor != null) {
      final PsiTypeParameter[] parameters = aClass.getTypeParameters();
      if (parameters.length > 0) {
        final int pos = text.length();
        text.append('<');
        final Map<PsiTypeParameter, PsiType> substitutionMap = substitutor.getSubstitutionMap();
        for (int i = 0, length = parameters.length; i < length; i++) {
          final PsiTypeParameter parameter = parameters[i];
          final PsiType parameterType = substitutionMap.get(parameter);
          if (parameterType == null) {
            text.setLength(pos);
            return;
          }
          if (i > 0) text.append(',');
          if (parameterType instanceof PsiClassType) {
            final PsiClassType classType = (PsiClassType)parameterType;
            final PsiClassType.ClassResolveResult result = classType.resolveGenerics();
            final PsiClass aClass1 = result.getElement();
            if (aClass1 != null) {
              buildText(aClass1, result.getSubstitutor(), qualifyInnerClasses, text);
              continue;
            }
          }
          text.append(parameterType.getPresentableText());
        }
        text.append('>');
      }
    }
  }
}
