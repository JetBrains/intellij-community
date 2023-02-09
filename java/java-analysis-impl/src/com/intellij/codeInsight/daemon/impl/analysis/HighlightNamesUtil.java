// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.application.options.colors.ScopeAttributesUtil;
import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.JavaHighlightInfoTypes;
import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.lang.ASTNode;
import com.intellij.lang.java.JavaLanguage;
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
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class HighlightNamesUtil {
  private static final Logger LOG = Logger.getInstance(HighlightNamesUtil.class);

  @Nullable
  static HighlightInfo highlightMethodName(@NotNull PsiMember methodOrClass,
                                           @NotNull PsiElement elementToHighlight,
                                           boolean isDeclaration,
                                           @NotNull TextAttributesScheme colorsScheme) {
    boolean isInherited = false;
    boolean isStaticallyImported = false;

    if (!isDeclaration) {
      isStaticallyImported = isStaticallyImported(elementToHighlight);
      if (isCalledOnThis(elementToHighlight)) {
        PsiClass containingClass = methodOrClass instanceof PsiMethod ? methodOrClass.getContainingClass() : null;
        PsiClass enclosingClass = containingClass == null ? null : PsiTreeUtil.getParentOfType(elementToHighlight, PsiClass.class);
        while (enclosingClass != null) {
          isInherited = enclosingClass.isInheritor(containingClass, true);
          if (isInherited) break;
          enclosingClass = PsiTreeUtil.getParentOfType(enclosingClass, PsiClass.class, true);
        }
      }
    }

    LOG.assertTrue(methodOrClass instanceof PsiMethod || !isDeclaration);
    HighlightInfoType type = methodOrClass instanceof PsiMethod ? getMethodNameHighlightType((PsiMethod)methodOrClass, isDeclaration, isInherited, isStaticallyImported)
                                                                : JavaHighlightInfoTypes.CONSTRUCTOR_CALL;
    if (type != null) {
      TextAttributes attributes = mergeWithScopeAttributes(methodOrClass, type, colorsScheme);
      if (!isDeclaration) {
        attributes = mergeWithVisibilityAttributes(methodOrClass, attributes, colorsScheme);
      }
      HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(type).range(elementToHighlight.getTextRange());
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
      return qualifier == null || qualifier instanceof PsiThisExpression;
    }
    return false;
  }

  public static boolean isStaticallyImported(@NotNull PsiElement elementToHighlight) {
    PsiReferenceExpression referenceExpression = PsiTreeUtil.getParentOfType(elementToHighlight, PsiReferenceExpression.class);
    if (referenceExpression != null) {
      JavaResolveResult result = referenceExpression.advancedResolve(false);
      return result.getCurrentFileResolveScope() instanceof PsiImportStaticStatement;
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
  
  private static TextAttributes mergeWithVisibilityAttributes(@NotNull PsiModifierListOwner listOwner, TextAttributes basedAttributes, @NotNull TextAttributesScheme colorsScheme) {
    TextAttributesKey attributesKey = null;
    if (listOwner.hasModifierProperty(PsiModifier.PUBLIC)) {
      attributesKey = JavaHighlightingColors.PUBLIC_REFERENCE_ATTRIBUTES;
    }
    else if (listOwner.hasModifierProperty(PsiModifier.PROTECTED)) {
      attributesKey = JavaHighlightingColors.PROTECTED_REFERENCE_ATTRIBUTES;
    }
    else if (listOwner.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
      attributesKey = JavaHighlightingColors.PACKAGE_PRIVATE_REFERENCE_ATTRIBUTES;
    }
    else if (listOwner.hasModifierProperty(PsiModifier.PRIVATE)) {
      attributesKey = JavaHighlightingColors.PRIVATE_REFERENCE_ATTRIBUTES;
    }
    if (attributesKey != null) {
      TextAttributes visibilityAttributes = colorsScheme.getAttributes(attributesKey);
      if (visibilityAttributes != null && !visibilityAttributes.isEmpty()) return TextAttributes.merge(basedAttributes, visibilityAttributes);
    }
    return basedAttributes;
  }

  @NotNull
  static HighlightInfo highlightClassName(@Nullable PsiClass aClass, @NotNull PsiElement elementToHighlight, @NotNull TextAttributesScheme colorsScheme) {
    TextRange range = elementToHighlight.getTextRange();
    if (elementToHighlight instanceof PsiJavaCodeReferenceElement referenceElement) {
      PsiElement identifier = referenceElement.getReferenceNameElement();
      if (identifier != null) {
        range = identifier.getTextRange();
      }
    }

    // This will highlight @ sign in annotation as well.
    PsiElement parent = elementToHighlight.getParent();
    if (parent instanceof PsiAnnotation psiAnnotation) {
      range = new TextRange(psiAnnotation.getTextRange().getStartOffset(), range.getEndOffset());
    }

    HighlightInfoType type = getClassNameHighlightType(aClass, elementToHighlight);
    TextAttributes attributes = mergeWithScopeAttributes(aClass, type, colorsScheme);
    if (aClass != null && elementToHighlight instanceof PsiJavaCodeReferenceElement) {
      attributes = mergeWithVisibilityAttributes(aClass, attributes, colorsScheme);
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
    HighlightInfoType varType = getVariableNameHighlightType(variable, elementToHighlight);
    if (varType == null) {
      return null;
    }
    if (variable instanceof PsiField) {
      TextAttributes attributes = mergeWithScopeAttributes(variable, varType, colorsScheme);
      if (elementToHighlight.getParent() instanceof PsiReferenceExpression) {
        attributes = mergeWithVisibilityAttributes(variable, attributes, colorsScheme);
      }
      HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(varType).range(elementToHighlight);
      if (attributes != null) {
        builder.textAttributes(attributes);
      }
      return builder.createUnconditionally();
    }

    HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(varType).range(elementToHighlight);
    return RainbowHighlighter.isRainbowEnabledWithInheritance(colorsScheme, JavaLanguage.INSTANCE)
           ? builder.createUnconditionally()
           : builder.create();
  }

  private static HighlightInfoType getMethodNameHighlightType(@NotNull PsiMethod method,
                                                              boolean isDeclaration,
                                                              boolean isInheritedMethod,
                                                              boolean isStaticallyImported) {
    if (method.isConstructor()) {
      return isDeclaration ? JavaHighlightInfoTypes.CONSTRUCTOR_DECLARATION : JavaHighlightInfoTypes.CONSTRUCTOR_CALL;
    }
    if (isDeclaration) return JavaHighlightInfoTypes.METHOD_DECLARATION;
    if (method.hasModifierProperty(PsiModifier.STATIC)) {
      return isStaticallyImported ? JavaHighlightInfoTypes.STATIC_METHOD_CALL_IMPORTED : JavaHighlightInfoTypes.STATIC_METHOD;
    }
    if (isInheritedMethod) return JavaHighlightInfoTypes.INHERITED_METHOD;
    if(method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return JavaHighlightInfoTypes.ABSTRACT_METHOD;
    }
    return JavaHighlightInfoTypes.METHOD_CALL;
  }

  @Nullable
  private static HighlightInfoType getVariableNameHighlightType(@NotNull PsiVariable var, @NotNull PsiElement elementToHighlight) {
    if (var instanceof PsiLocalVariable
        || var instanceof PsiParameter && ((PsiParameter)var).getDeclarationScope() instanceof PsiForeachStatement) {
      return JavaHighlightInfoTypes.LOCAL_VARIABLE;
    }
    if (var instanceof PsiField) {
      if (var.hasModifierProperty(PsiModifier.STATIC)) {
        boolean staticallyImported = isStaticallyImported(elementToHighlight);
        if (var.hasModifierProperty(PsiModifier.FINAL)) {
          return staticallyImported ? JavaHighlightInfoTypes.STATIC_FINAL_FIELD_IMPORTED : JavaHighlightInfoTypes.STATIC_FINAL_FIELD;
        }
        else {
          return staticallyImported ? JavaHighlightInfoTypes.STATIC_FIELD_IMPORTED : JavaHighlightInfoTypes.STATIC_FIELD;
        }
      }
      else {
        return var.hasModifierProperty(PsiModifier.FINAL) ? JavaHighlightInfoTypes.INSTANCE_FINAL_FIELD : JavaHighlightInfoTypes.INSTANCE_FIELD;
      }
    }
    if (var instanceof PsiParameter) {
      return ((PsiParameter)var).getDeclarationScope() instanceof PsiLambdaExpression ? JavaHighlightInfoTypes.LAMBDA_PARAMETER
                                                                                      : JavaHighlightInfoTypes.PARAMETER;
    }
    return null;
  }

  @NotNull
  private static HighlightInfoType getClassNameHighlightType(@Nullable PsiClass aClass, @NotNull PsiElement element) {
    if (element instanceof PsiJavaCodeReferenceElement && element.getParent() instanceof PsiAnonymousClass) {
      return JavaHighlightInfoTypes.ANONYMOUS_CLASS_NAME;
    }
    if (aClass != null) {
      if (aClass.isAnnotationType()) return JavaHighlightInfoTypes.ANNOTATION_NAME;
      if (aClass.isInterface()) return JavaHighlightInfoTypes.INTERFACE_NAME;
      if (aClass.isEnum()) return JavaHighlightInfoTypes.ENUM_NAME;
      if (aClass instanceof PsiTypeParameter) return JavaHighlightInfoTypes.TYPE_PARAMETER_NAME;
      PsiModifierList modList = aClass.getModifierList();
      if (modList != null && modList.hasModifierProperty(PsiModifier.ABSTRACT)) return JavaHighlightInfoTypes.ABSTRACT_CLASS_NAME;
    }
    // use class by default
    return JavaHighlightInfoTypes.CLASS_NAME;
  }

  private static TextAttributes getScopeAttributes(@NotNull PsiElement element, @NotNull TextAttributesScheme colorsScheme) {
    PsiFile file = element.getContainingFile();
    if (file == null) return null;
    TextAttributes result = null;
    DependencyValidationManagerImpl validationManager = (DependencyValidationManagerImpl)DependencyValidationManager.getInstance(file.getProject());
    List<Pair<NamedScope,NamedScopesHolder>> scopes = validationManager.getScopeBasedHighlightingCachedScopes();
    for (Pair<NamedScope, NamedScopesHolder> scope : scopes) {
      NamedScope namedScope = scope.getFirst();
      TextAttributesKey scopeKey = ScopeAttributesUtil.getScopeTextAttributeKey(namedScope.getScopeId());
      TextAttributes attributes = colorsScheme.getAttributes(scopeKey);
      if (attributes == null || attributes.isEmpty()) {
        continue;
      }
      PackageSet packageSet = namedScope.getValue();
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
    TextRange throwsRange = method.getThrowsList().getTextRange();
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
    PsiElement psiElement = aClass instanceof PsiAnonymousClass
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
    for (PsiElement child = element.getLastChild(); child != null; child = child.getPrevSibling()) {
      if (child instanceof PsiAnnotation) {
        lastAnnotation = (PsiAnnotation)child;
        break;
      }
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

  static @NotNull HighlightInfo highlightPackage(@NotNull PsiElement resolved, @NotNull PsiJavaCodeReferenceElement elementToHighlight, @NotNull TextAttributesScheme scheme) {
    PsiElement referenceNameElement = elementToHighlight.getReferenceNameElement();
    TextRange range;
    if (referenceNameElement == null) {
      range = elementToHighlight.getTextRange();
    }
    else {
      PsiElement nextSibling = PsiTreeUtil.nextLeaf(referenceNameElement);
      if (nextSibling != null && nextSibling.getTextRange().isEmpty()) {
        // empty PsiReferenceParameterList
        nextSibling = PsiTreeUtil.nextLeaf(nextSibling);
      }
      if (PsiUtil.isJavaToken(nextSibling, JavaTokenType.DOT)) {
        range = new TextRange(referenceNameElement.getTextRange().getStartOffset(), nextSibling.getTextRange().getEndOffset());
      }
      else {
        range = referenceNameElement.getTextRange();
      }
    }
    HighlightInfoType type = JavaHighlightInfoTypes.CLASS_NAME;
    TextAttributes attributes = mergeWithScopeAttributes(resolved, type, scheme);
    HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(type).range(range);
    if (attributes != null) {
      builder.textAttributes(attributes);
    }
    return builder.createUnconditionally();
  }
}
