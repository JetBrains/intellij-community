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
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.application.options.colors.ScopeAttributesUtil;
import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.JavaHighlightInfoTypes;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.colors.TextAttributesScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.packageDependencies.DependencyValidationManagerImpl;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class HighlightNamesUtil {
  private static final Logger LOG = Logger.getInstance("#" + HighlightNamesUtil.class.getName());

  @Nullable
  static HighlightInfo highlightMethodName(@NotNull PsiMethod method,
                                           @NotNull PsiElement elementToHighlight,
                                           final boolean isDeclaration,
                                           @NotNull TextAttributesScheme colorsScheme) {
    return highlightMethodName(method, elementToHighlight, elementToHighlight.getTextRange(), colorsScheme, isDeclaration);
  }

  /**
   * @param methodOrClass method to highlight; class is passed instead of implicit constructor
   */
  @Nullable
  static HighlightInfo highlightMethodName(@NotNull PsiMember methodOrClass,
                                           @NotNull PsiElement elementToHighlight,
                                           @NotNull TextRange range,
                                           @NotNull TextAttributesScheme colorsScheme,
                                           final boolean isDeclaration) {
    boolean isInherited = false;

    if (!isDeclaration) {
      if (isCalledOnThis(elementToHighlight)) {
        final PsiClass containingClass = methodOrClass instanceof PsiMethod ? methodOrClass.getContainingClass() : null;
        PsiClass enclosingClass = containingClass == null ? null : PsiTreeUtil.getParentOfType(elementToHighlight, PsiClass.class);
        while (enclosingClass != null) {
          isInherited = enclosingClass.isInheritor(containingClass, true);
          if (isInherited) break;
          enclosingClass = PsiTreeUtil.getParentOfType(enclosingClass, PsiClass.class, true);
        }
      }
    }

    LOG.assertTrue(methodOrClass instanceof PsiMethod || !isDeclaration);
    HighlightInfoType type = methodOrClass instanceof PsiMethod ? getMethodNameHighlightType((PsiMethod)methodOrClass, isDeclaration, isInherited)
                                                                : JavaHighlightInfoTypes.CONSTRUCTOR_CALL;
    if (type != null) {
      TextAttributes attributes = mergeWithScopeAttributes(methodOrClass, type, colorsScheme);
      HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(type).range(range);
      if (attributes != null) {
        builder.textAttributes(attributes);
      }
      return builder.createUnconditionally();
    }
    return null;
  }

  private static boolean isCalledOnThis(@NotNull PsiElement elementToHighlight) {
    PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(elementToHighlight, PsiMethodCallExpression.class);
    if (methodCallExpression != null) {
      PsiElement qualifier = methodCallExpression.getMethodExpression().getQualifier();
      if (qualifier == null || qualifier instanceof PsiThisExpression) {
        return true;
      }
    }
    return false;
  }

  private static TextAttributes mergeWithScopeAttributes(@Nullable PsiElement element,
                                                         @NotNull HighlightInfoType type,
                                                         @NotNull TextAttributesScheme colorsScheme) {
    TextAttributes regularAttributes = HighlightInfo.getAttributesByType(element, type, colorsScheme);
    if (element == null) return regularAttributes;
    TextAttributes scopeAttributes = getScopeAttributes(element, colorsScheme);
    return TextAttributes.merge(scopeAttributes, regularAttributes);
  }

  @NotNull
  static HighlightInfo highlightClassName(@Nullable PsiClass aClass, @NotNull PsiElement elementToHighlight, @NotNull TextAttributesScheme colorsScheme) {
    HighlightInfoType type = getClassNameHighlightType(aClass, elementToHighlight);
    TextAttributes attributes = mergeWithScopeAttributes(aClass, type, colorsScheme);
    TextRange range = elementToHighlight.getTextRange();
    if (elementToHighlight instanceof PsiJavaCodeReferenceElement) {
      final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)elementToHighlight;
      PsiReferenceParameterList parameterList = referenceElement.getParameterList();
      if (parameterList != null) {
        final TextRange paramListRange = parameterList.getTextRange();
        if (paramListRange.getEndOffset() > paramListRange.getStartOffset()) {
          range = new TextRange(range.getStartOffset(), paramListRange.getStartOffset());
        }
      }
    }

    // This will highlight @ sign in annotation as well.
    final PsiElement parent = elementToHighlight.getParent();
    if (parent instanceof PsiAnnotation) {
      final PsiAnnotation psiAnnotation = (PsiAnnotation)parent;
      range = new TextRange(psiAnnotation.getTextRange().getStartOffset(), range.getEndOffset());
    }

    HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(type).range(range);
    if (attributes != null) {
      builder.textAttributes(attributes);
    }
    return builder.createUnconditionally();
  }

  @Nullable
  static HighlightInfo highlightVariableName(@NotNull PsiVariable variable,
                                             @NotNull PsiElement elementToHighlight,
                                             @NotNull TextAttributesScheme colorsScheme) {
    HighlightInfoType varType = getVariableNameHighlightType(variable);
    if (varType == null) {
      return null;
    }
    if (variable instanceof PsiField) {
      TextAttributes attributes = mergeWithScopeAttributes(variable, varType, colorsScheme);
      HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(varType).range(elementToHighlight.getTextRange());
      if (attributes != null) {
        builder.textAttributes(attributes);
      }
      return builder.createUnconditionally();
    }

    HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(varType).range(elementToHighlight);
    return RainbowHighlighter.isRainbowEnabled() ? builder.createUnconditionally() : builder.create();
  }

  @Nullable
  static HighlightInfo highlightClassNameInQualifier(@NotNull PsiJavaCodeReferenceElement element,
                                                     @NotNull TextAttributesScheme colorsScheme) {
    PsiElement qualifierExpression = element.getQualifier();
    if (qualifierExpression instanceof PsiJavaCodeReferenceElement) {
      PsiElement resolved = ((PsiJavaCodeReferenceElement)qualifierExpression).resolve();
      if (resolved instanceof PsiClass) {
        return highlightClassName((PsiClass)resolved, qualifierExpression, colorsScheme);
      }
    }
    return null;
  }

  private static HighlightInfoType getMethodNameHighlightType(@NotNull PsiMethod method, boolean isDeclaration, boolean isInheritedMethod) {
    if (method.isConstructor()) {
      return isDeclaration ? JavaHighlightInfoTypes.CONSTRUCTOR_DECLARATION : JavaHighlightInfoTypes.CONSTRUCTOR_CALL;
    }
    if (isDeclaration) return JavaHighlightInfoTypes.METHOD_DECLARATION;
    if (method.hasModifierProperty(PsiModifier.STATIC)) {
      return JavaHighlightInfoTypes.STATIC_METHOD;
    }
    if (isInheritedMethod) return JavaHighlightInfoTypes.INHERITED_METHOD;
    if(method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return JavaHighlightInfoTypes.ABSTRACT_METHOD;
    }
    return JavaHighlightInfoTypes.METHOD_CALL;
  }

  @Nullable
  private static HighlightInfoType getVariableNameHighlightType(@NotNull PsiVariable var) {
    if (var instanceof PsiLocalVariable
        || var instanceof PsiParameter && ((PsiParameter)var).getDeclarationScope() instanceof PsiForeachStatement) {
      return JavaHighlightInfoTypes.LOCAL_VARIABLE;
    }
    if (var instanceof PsiField) {
      return var.hasModifierProperty(PsiModifier.STATIC) 
             ? var.hasModifierProperty(PsiModifier.FINAL) ? JavaHighlightInfoTypes.STATIC_FINAL_FIELD : JavaHighlightInfoTypes.STATIC_FIELD
             : var.hasModifierProperty(PsiModifier.FINAL) ? JavaHighlightInfoTypes.INSTANCE_FINAL_FIELD : JavaHighlightInfoTypes.INSTANCE_FIELD;
    }
    if (var instanceof PsiParameter) {
      return ((PsiParameter)var).getDeclarationScope() instanceof PsiLambdaExpression ? JavaHighlightInfoTypes.LAMBDA_PARAMETER
                                                                                      : JavaHighlightInfoTypes.PARAMETER;
    }
    return null;
  }

  @NotNull
  private static HighlightInfoType getClassNameHighlightType(@Nullable PsiClass aClass, @Nullable PsiElement element) {
    if (element instanceof PsiJavaCodeReferenceElement && element.getParent() instanceof PsiAnonymousClass) {
      return JavaHighlightInfoTypes.ANONYMOUS_CLASS_NAME;
    }
    if (aClass != null) {
      if (aClass.isAnnotationType()) return JavaHighlightInfoTypes.ANNOTATION_NAME;
      if (aClass.isInterface()) return JavaHighlightInfoTypes.INTERFACE_NAME;
      if (aClass.isEnum()) return JavaHighlightInfoTypes.ENUM_NAME;
      if (aClass instanceof PsiTypeParameter) return JavaHighlightInfoTypes.TYPE_PARAMETER_NAME;
      final PsiModifierList modList = aClass.getModifierList();
      if (modList != null && modList.hasModifierProperty(PsiModifier.ABSTRACT)) return JavaHighlightInfoTypes.ABSTRACT_CLASS_NAME;
    }
    // use class by default
    return JavaHighlightInfoTypes.CLASS_NAME;
  }

  @Nullable
  static HighlightInfo highlightReassignedVariable(@NotNull PsiVariable variable, @NotNull PsiElement elementToHighlight) {
    if (variable instanceof PsiLocalVariable) {
      return HighlightInfo.newHighlightInfo(JavaHighlightInfoTypes.REASSIGNED_LOCAL_VARIABLE).range(elementToHighlight).create();
    }
    if (variable instanceof PsiParameter) {
      return HighlightInfo.newHighlightInfo(JavaHighlightInfoTypes.REASSIGNED_PARAMETER).range(elementToHighlight).create();
    }
    return null;
  }

  private static TextAttributes getScopeAttributes(@NotNull PsiElement element, @NotNull TextAttributesScheme colorsScheme) {
    PsiFile file = element.getContainingFile();
    if (file == null) return null;
    TextAttributes result = null;
    DependencyValidationManagerImpl validationManager = (DependencyValidationManagerImpl)DependencyValidationManager.getInstance(file.getProject());
    List<Pair<NamedScope,NamedScopesHolder>> scopes = validationManager.getScopeBasedHighlightingCachedScopes();
    for (Pair<NamedScope, NamedScopesHolder> scope : scopes) {
      final NamedScope namedScope = scope.getFirst();
      final TextAttributesKey scopeKey = ScopeAttributesUtil.getScopeTextAttributeKey(namedScope.getName());
      final TextAttributes attributes = colorsScheme.getAttributes(scopeKey);
      if (attributes == null || attributes.isEmpty()) {
        continue;
      }
      final PackageSet packageSet = namedScope.getValue();
      if (packageSet != null && packageSet.contains(file, scope.getSecond())) {
        result = TextAttributes.merge(attributes, result);
      }
    }
    return result;
  }

  @NotNull
  public static TextRange getMethodDeclarationTextRange(@NotNull PsiMethod method) {
    if (method instanceof SyntheticElement) return TextRange.EMPTY_RANGE;
    int start = stripAnnotationsFromModifierList(method.getModifierList());
    final TextRange throwsRange = method.getThrowsList().getTextRange();
    LOG.assertTrue(throwsRange != null, method);
    int end = throwsRange.getEndOffset();
    return new TextRange(start, end);
  }

  @NotNull
  public static TextRange getFieldDeclarationTextRange(@NotNull PsiField field) {
    int start = stripAnnotationsFromModifierList(field.getModifierList());
    int end = field.getNameIdentifier().getTextRange().getEndOffset();
    return new TextRange(start, end);
  }

  @NotNull
  public static TextRange getClassDeclarationTextRange(@NotNull PsiClass aClass) {
    if (aClass instanceof PsiEnumConstantInitializer) {
      return ((PsiEnumConstantInitializer)aClass).getEnumConstant().getNameIdentifier().getTextRange();
    }
    final PsiElement psiElement = aClass instanceof PsiAnonymousClass
                                  ? ((PsiAnonymousClass)aClass).getBaseClassReference()
                                  : aClass.getModifierList() == null ? aClass.getNameIdentifier() : aClass.getModifierList();
    if(psiElement == null) return new TextRange(aClass.getTextRange().getStartOffset(), aClass.getTextRange().getStartOffset());
    int start = stripAnnotationsFromModifierList(psiElement);
    PsiElement endElement = aClass instanceof PsiAnonymousClass ?
                            ((PsiAnonymousClass)aClass).getBaseClassReference() :
                            aClass.getImplementsList();
    if (endElement == null) endElement = aClass.getNameIdentifier();
    TextRange endTextRange = endElement == null ? null : endElement.getTextRange();
    int end = endTextRange == null ? start : endTextRange.getEndOffset();
    return new TextRange(start, end);
  }

  private static int stripAnnotationsFromModifierList(@NotNull PsiElement element) {
    TextRange textRange = element.getTextRange();
    if (textRange == null) return 0;
    PsiAnnotation lastAnnotation = null;
    for (PsiElement child : element.getChildren()) {
      if (child instanceof PsiAnnotation) lastAnnotation = (PsiAnnotation)child;
    }
    if (lastAnnotation == null) {
      return textRange.getStartOffset();
    }
    ASTNode node = lastAnnotation.getNode();
    if (node != null) {
      do {
        node = TreeUtil.nextLeaf(node);
      }
      while (node != null && ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains(node.getElementType()));
    }
    if (node != null) return node.getTextRange().getStartOffset();
    return textRange.getStartOffset();
  }
}
