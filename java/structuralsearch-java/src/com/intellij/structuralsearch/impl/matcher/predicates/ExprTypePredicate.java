// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.structuralsearch.MatchUtil;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Maxim.Mossienko
 */
public class ExprTypePredicate extends MatchPredicate {
  private final RegExpPredicate myDelegate;
  private final boolean myWithinHierarchy;
  private final boolean needsTypeParameters;
  private final boolean needsFullyQualified;
  private final boolean needsArrayType;
  private final boolean needsAnnotations;
  private final boolean myCaseSensitive;
  private final List<String> myTypes;

  public ExprTypePredicate(@NotNull String type, String baseName, boolean withinHierarchy, boolean caseSensitiveMatch, boolean target, boolean regex) {
    myDelegate = regex ? new RegExpPredicate(type, caseSensitiveMatch, baseName, false, target) : null;
    myWithinHierarchy = withinHierarchy;
    needsTypeParameters = type.indexOf('<') >= 0;
    needsFullyQualified = type.indexOf('.') >= 0;
    needsArrayType = type.indexOf('[') >= 0;
    needsAnnotations = type.indexOf('@') >= 0;
    myCaseSensitive = caseSensitiveMatch;
    myTypes = regex ? null : split(type);
  }

  private static List<String> split(String types) {
    List<String> result = new ArrayList<>();
    int pos = 0;
    while (true) {
      int index = types.indexOf('|', pos);
      if (index == -1) break;
      String token = MatchUtil.normalizeWhiteSpace(types.substring(pos, index));
      if (!token.isEmpty()) {
        result.add(token);
      }
      pos = index + 1;
    }
    if (pos < types.length()) {
      result.add(MatchUtil.normalizeWhiteSpace(types.substring(pos)));
    }
    return result;
  }

  @Override
  public boolean match(@NotNull PsiElement match, int start, int end, @NotNull MatchContext context) {
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

  protected PsiType evalType(@NotNull PsiExpression match, @NotNull MatchContext context) {
    if (match instanceof PsiFunctionalExpression) {
      final PsiFunctionalExpression functionalExpression = (PsiFunctionalExpression)match;
      return functionalExpression.getFunctionalInterfaceType();
    }
    else if (match instanceof PsiReferenceExpression) {
      final PsiElement parent = match.getParent();
      if (parent instanceof PsiMethodCallExpression) {
        return ((PsiMethodCallExpression)parent).getType();
      }
    }
    return match.getType();
  }

  private boolean doMatchWithTheType(@NotNull PsiType type, @NotNull MatchContext context, @NotNull PsiElement matchedNode, @Nullable Set<? super PsiType> visited) {
    final List<String> permutations = getTextPermutations(type);
    for (String permutation : permutations) {
      if (myDelegate == null ? doMatch(permutation) : myDelegate.doMatch(permutation, context, matchedNode)) {
        return true;
      }
    }
    if (myWithinHierarchy) {
      if (visited == null) {
        visited = new HashSet<>();
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

  private boolean doMatch(String text) {
    return ContainerUtil.exists(myTypes, type -> myCaseSensitive ? type.equals(text) : type.equalsIgnoreCase(text));
  }

  private List<String> getTextPermutations(PsiType type) {
    final String annotationString = getAnnotationString(type);
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
        final String typeString = annotationString + typeText;
        if (needsTypeParameters) {
          result.add(typeString); // unqualified with type parameters if present
        }
        addWithoutTypeParameters(typeString, dimensions, result);
        if (typeText.indexOf(".") >= 0) { // inner class qualified with outer class (we currently support one level)
          typeText.setLength(0);
          buildText(aClass, substitutor, false, typeText);
          typeText.append(dimensions);
          final String outerClassQualified = annotationString + typeText;
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
    final String fullyQualified = annotationString + type.getCanonicalText() + dimensions;
    if (needsTypeParameters) {
      result.add(fullyQualified); // fully qualified if possible with type parameters if present
    }
    addWithoutTypeParameters(fullyQualified, dimensions, result);
    return result;
  }

  @NotNull
  private String getAnnotationString(PsiType type) {
    if (!needsAnnotations) {
      return "";
    }
    final PsiAnnotation[] annotations = type.getAnnotations();
    if (annotations.length <= 0) {
      return "";
    }
    StringBuilder result = new StringBuilder();
    for (PsiAnnotation annotation : annotations) {
      final PsiJavaCodeReferenceElement nameReferenceElement = annotation.getNameReferenceElement();
      if (nameReferenceElement != null) {
        final PsiElement referenceNameElement = nameReferenceElement.getReferenceNameElement();
        if (referenceNameElement != null) {
          result.append('@').append(referenceNameElement.getText())
            .append(annotation.getParameterList().getText())
            .append(' ');
        }
      }
    }
    return result.toString();
  }

  private static void addWithoutTypeParameters(String typeText, String suffix, List<? super String> result) {
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
