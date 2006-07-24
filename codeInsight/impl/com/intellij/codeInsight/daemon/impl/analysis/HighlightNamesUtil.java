/**
 * @author cdr
 */
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.application.options.colors.ColorAndFontOptions;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import org.jetbrains.annotations.Nullable;

public class HighlightNamesUtil {
  private HighlightNamesUtil() {}

  @Nullable
  public static HighlightInfo highlightMethodName(PsiMethod method, PsiElement elementToHighlight, boolean isDeclaration) {
    HighlightInfoType type = getMethodNameHighlightType(method, isDeclaration);
    if (type != null && elementToHighlight != null) {
      TextAttributes attributes = mergeWithScopeAttributes(method, type);
      return HighlightInfo.createHighlightInfo(type, elementToHighlight.getTextRange(), null, attributes);
    }
    return null;
  }

  private static TextAttributes mergeWithScopeAttributes(final PsiElement element, final HighlightInfoType type) {
    TextAttributes regularAttributes = HighlightInfo.getAttributesByType(type);
    if (element == null) return regularAttributes;
    TextAttributes scopeAttributes = getScopeAttributes(element);
    return TextAttributes.merge(scopeAttributes, regularAttributes);
  }

  @Nullable
  public static HighlightInfo highlightClassName(PsiClass aClass, PsiElement elementToHighlight) {
    HighlightInfoType type = getClassNameHighlightType(aClass);
    if (type != null && elementToHighlight != null) {
      TextAttributes attributes = mergeWithScopeAttributes(aClass, type);
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

      return HighlightInfo.createHighlightInfo(type, range, null, attributes);
    }
    return null;
  }

  @Nullable
  public static HighlightInfo highlightVariable(PsiVariable variable, PsiElement elementToHighlight) {
    HighlightInfoType varType = getVariableNameHighlightType(variable);
    if (varType != null) {
      if (variable instanceof PsiField) {
        TextAttributes attributes = mergeWithScopeAttributes(variable, varType);
        return HighlightInfo.createHighlightInfo(varType, elementToHighlight.getTextRange(), null, attributes);
      }
      return HighlightInfo.createHighlightInfo(varType, elementToHighlight, null);
    }
    return null;
  }

  @Nullable
  public static HighlightInfo highlightClassNameInQualifier(PsiJavaCodeReferenceElement element) {
    PsiExpression qualifierExpression = null;
    if (element instanceof PsiReferenceExpression) {
      qualifierExpression = ((PsiReferenceExpression)element).getQualifierExpression();
    }
    if (qualifierExpression instanceof PsiJavaCodeReferenceElement) {
      PsiElement resolved = ((PsiJavaCodeReferenceElement)qualifierExpression).resolve();
      if (resolved instanceof PsiClass) {
        return highlightClassName((PsiClass)resolved, qualifierExpression);
      }
    }
    return null;
  }

  private static HighlightInfoType getMethodNameHighlightType(PsiMethod method, boolean isDeclaration) {
    if (method.isConstructor()) {
      return isDeclaration ? HighlightInfoType.CONSTRUCTOR_DECLARATION : HighlightInfoType.CONSTRUCTOR_CALL;
    }
    if (isDeclaration) return HighlightInfoType.METHOD_DECLARATION;
    if (method.hasModifierProperty(PsiModifier.STATIC)) {
      return HighlightInfoType.STATIC_METHOD;
    }
    return HighlightInfoType.METHOD_CALL;
  }

  @Nullable
  private static HighlightInfoType getVariableNameHighlightType(PsiVariable var) {
    if (var instanceof PsiLocalVariable
        || var instanceof PsiParameter && ((PsiParameter)var).getDeclarationScope() instanceof PsiForeachStatement) {
      return HighlightInfoType.LOCAL_VARIABLE;
    }
    else if (var instanceof PsiField) {
      return var.hasModifierProperty(PsiModifier.STATIC)
             ? HighlightInfoType.STATIC_FIELD
             : HighlightInfoType.INSTANCE_FIELD;
    }
    else if (var instanceof PsiParameter) {
      return HighlightInfoType.PARAMETER;
    }
    else { //?
      return null;
    }
  }

  private static HighlightInfoType getClassNameHighlightType(PsiClass aClass) {
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
    else if (variable instanceof PsiParameter) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.REASSIGNED_PARAMETER, elementToHighlight, null);
    }
    else {
      return null;
    }
  }

  private static TextAttributes getScopeAttributes(PsiElement element) {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    NamedScopeManager namedScopeManager = NamedScopeManager.getInstance(element.getProject());
    NamedScope[] scopes = namedScopeManager.getScopes();
    PsiFile file = element.getContainingFile();
    if (file == null) return null;
    for (NamedScope namedScope : scopes) {
      PackageSet packageSet = namedScope.getValue();
      String name = namedScope.getName();
      if (packageSet != null && packageSet.contains(file, namedScopeManager)) {
        TextAttributesKey scopeKey = ColorAndFontOptions.getScopeTextAttributeKey(name);
        TextAttributes attributes = scheme.getAttributes(scopeKey);
        if (attributes != null) return attributes;
      }
    }
    return null;
  }
}