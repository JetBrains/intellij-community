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

/**
 * @author cdr
 */
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.application.options.colors.ColorAndFontOptions;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class HighlightNamesUtil {
  @Nullable
  public static HighlightInfo highlightMethodName(final PsiMethod method,
                                                  final PsiElement elementToHighlight,
                                                  final boolean isDeclaration,
                                                  @NotNull EditorColorsScheme colorsScheme) {
    boolean calledOnThis = isCalledOnThis(elementToHighlight);
    boolean isInheritedMethod = false;
    if (calledOnThis) {
      PsiClass enclosingClass = findEnclosingClass(elementToHighlight);
      isInheritedMethod = isMethodInHierarchy(method, enclosingClass.getSuperClass());
    }

    HighlightInfoType type = getMethodNameHighlightType(method, isDeclaration, isInheritedMethod);
    if (type != null && elementToHighlight != null) {
      TextAttributes attributes = mergeWithScopeAttributes(method, type, colorsScheme);
      HighlightInfo info = HighlightInfo.createHighlightInfo(type, elementToHighlight.getTextRange(), null, null, attributes);
      if (info != null) return info;
    }
    return null;
  }

  private static boolean isMethodInHierarchy(PsiMethod method, PsiClass psiClass) {
    if (Arrays.asList(psiClass.getAllMethods()).contains(method)) {
      return true;
    }
    boolean result = false;
    PsiClass superClass = psiClass.getSuperClass();
    if (superClass != null) {
      result = isMethodInHierarchy(method, superClass);
    }
    return result;
  }

  private static boolean isCalledOnThis(PsiElement elementToHighlight) {
    PsiElement parent = elementToHighlight.getParent();
    if (parent instanceof PsiReferenceExpression) {
      PsiReferenceExpression psiReferenceExpression = (PsiReferenceExpression)parent;
      PsiElement firstElement = psiReferenceExpression.getFirstChild();
      return firstElement instanceof PsiThisExpression || firstElement instanceof PsiReferenceParameterList;
    }
    return false;
  }

  private static PsiClass findEnclosingClass(PsiElement element) {
    PsiElement currentElement = element;
    while (!(currentElement instanceof PsiClass)) {
      currentElement = currentElement.getParent();
    }
    return (PsiClass)currentElement;
  }

  private static TextAttributes mergeWithScopeAttributes(final PsiElement element,
                                                         final HighlightInfoType type,
                                                         @NotNull EditorColorsScheme colorsScheme) {
    TextAttributes regularAttributes = HighlightInfo.getAttributesByType(element, type, colorsScheme);
    if (element == null) return regularAttributes;
    TextAttributes scopeAttributes = getScopeAttributes(element, colorsScheme);
    return TextAttributes.merge(scopeAttributes, regularAttributes);
  }

  @Nullable
  public static HighlightInfo highlightClassName(PsiClass aClass, PsiElement elementToHighlight, @NotNull EditorColorsScheme colorsScheme) {
    HighlightInfoType type = getClassNameHighlightType(aClass, elementToHighlight);
    if (elementToHighlight != null) {
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

      return HighlightInfo.createHighlightInfo(type, range, null, null, attributes);
    }
    return null;
  }

  @Nullable
  public static HighlightInfo highlightVariableName(final PsiVariable variable,
                                                    final PsiElement elementToHighlight,
                                                    @NotNull EditorColorsScheme colorsScheme) {
    HighlightInfoType varType = getVariableNameHighlightType(variable);
    if (varType != null) {
      if (variable instanceof PsiField) {
        TextAttributes attributes = mergeWithScopeAttributes(variable, varType, colorsScheme);
        return HighlightInfo.createHighlightInfo(varType, elementToHighlight.getTextRange(), null, null, attributes);
      }
      return HighlightInfo.createHighlightInfo(varType, elementToHighlight, null);
    }
    return null;
  }

  @Nullable
  public static HighlightInfo highlightClassNameInQualifier(final PsiJavaCodeReferenceElement element,
                                                            @NotNull EditorColorsScheme colorsScheme) {
    PsiExpression qualifierExpression = null;
    if (element instanceof PsiReferenceExpression) {
      qualifierExpression = ((PsiReferenceExpression)element).getQualifierExpression();
    }
    if (qualifierExpression instanceof PsiJavaCodeReferenceElement) {
      PsiElement resolved = ((PsiJavaCodeReferenceElement)qualifierExpression).resolve();
      if (resolved instanceof PsiClass) {
        return highlightClassName((PsiClass)resolved, qualifierExpression, colorsScheme);
      }
    }
    return null;
  }

  private static HighlightInfoType getMethodNameHighlightType(PsiMethod method, boolean isDeclaration, boolean isInheritedMethod) {
    if (method.isConstructor()) {
      return isDeclaration ? HighlightInfoType.CONSTRUCTOR_DECLARATION : HighlightInfoType.CONSTRUCTOR_CALL;
    }
    if (isInheritedMethod) return HighlightInfoType.INHERITED_METHOD;
    if (isDeclaration) return HighlightInfoType.METHOD_DECLARATION;
    if (method.hasModifierProperty(PsiModifier.STATIC)) {
      return HighlightInfoType.STATIC_METHOD;
    }
    if(method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return HighlightInfoType.ABSTRACT_METHOD;
    }
    return HighlightInfoType.METHOD_CALL;
  }

  @Nullable
  private static HighlightInfoType getVariableNameHighlightType(PsiVariable var) {
    if (var instanceof PsiLocalVariable
        || var instanceof PsiParameter && ((PsiParameter)var).getDeclarationScope() instanceof PsiForeachStatement) {
      return HighlightInfoType.LOCAL_VARIABLE;
    }
    if (var instanceof PsiField) {
      return var.hasModifierProperty(PsiModifier.STATIC)
             ? HighlightInfoType.STATIC_FIELD
             : HighlightInfoType.INSTANCE_FIELD;
    }
    if (var instanceof PsiParameter) {
      return HighlightInfoType.PARAMETER;
    }
    return null;
  }

  @NotNull
  private static HighlightInfoType getClassNameHighlightType(@Nullable PsiClass aClass, @Nullable PsiElement element) {
    if (element instanceof PsiJavaCodeReferenceElement && element.getParent() instanceof PsiAnonymousClass) {
      return HighlightInfoType.ANONYMOUS_CLASS_NAME;
    }
    if (aClass != null) {
      if (aClass.isAnnotationType()) return HighlightInfoType.ANNOTATION_NAME;
      if (aClass.isInterface()) return HighlightInfoType.INTERFACE_NAME;
      if (aClass instanceof PsiTypeParameter) return HighlightInfoType.TYPE_PARAMETER_NAME;
      final PsiModifierList modList = aClass.getModifierList();
      if (modList != null && modList.hasModifierProperty(PsiModifier.ABSTRACT)) return HighlightInfoType.ABSTRACT_CLASS_NAME;
    }
    // use class by default
    return HighlightInfoType.CLASS_NAME;
  }

  @Nullable
  public static HighlightInfo highlightReassignedVariable(PsiVariable variable, PsiElement elementToHighlight) {
    if (variable instanceof PsiLocalVariable) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.REASSIGNED_LOCAL_VARIABLE, elementToHighlight, null);
    }
    if (variable instanceof PsiParameter) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.REASSIGNED_PARAMETER, elementToHighlight, null);
    }
    return null;
  }

  private static TextAttributes getScopeAttributes(final PsiElement element,
                                                   final EditorColorsScheme colorsScheme) {
    PsiFile file = element.getContainingFile();
    if (file == null) return null;
    TextAttributes result = null;
    final DaemonCodeAnalyzerImpl daemonCodeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(element.getProject());
    List<Pair<NamedScope,NamedScopesHolder>> scopes = daemonCodeAnalyzer.getScopeBasedHighlightingCachedScopes();
    for (Pair<NamedScope, NamedScopesHolder> scope : scopes) {
      NamedScope namedScope = scope.getFirst();
      NamedScopesHolder scopesHolder = scope.getSecond();
      PackageSet packageSet = namedScope.getValue();
      if (packageSet != null && packageSet.contains(file, scopesHolder)) {
        TextAttributesKey scopeKey = ColorAndFontOptions.getScopeTextAttributeKey(namedScope.getName());
        TextAttributes attributes = colorsScheme.getAttributes(scopeKey);
        if (attributes == null || attributes.isEmpty()) {
          continue;
        }
        result = TextAttributes.merge(attributes, result);
      }
    }
    return result;
  }

  public static TextRange getMethodDeclarationTextRange(@NotNull PsiMethod method) {
    if (method instanceof JspHolderMethod) return TextRange.EMPTY_RANGE;
    int start = stripAnnotationsFromModifierList(method.getModifierList());
    int end = method.getThrowsList().getTextRange().getEndOffset();
    return new TextRange(start, end);
  }

  public static TextRange getFieldDeclarationTextRange(@NotNull PsiField field) {
    int start = stripAnnotationsFromModifierList(field.getModifierList());
    int end = field.getNameIdentifier().getTextRange().getEndOffset();
    return new TextRange(start, end);
  }

  public static TextRange getClassDeclarationTextRange(@NotNull PsiClass aClass) {
    if (aClass instanceof PsiEnumConstantInitializer) {
      return aClass.getLBrace().getTextRange();
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

  private static int stripAnnotationsFromModifierList(PsiElement element) {
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
