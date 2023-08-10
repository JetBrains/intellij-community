// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.javadoc;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightNamesUtil;
import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;


public class JavaDocHighlightingManagerImpl implements JavaDocHighlightingManager {

  private static final @NotNull JavaDocHighlightingManagerImpl INSTANCE = new JavaDocHighlightingManagerImpl();

  public static @NotNull JavaDocHighlightingManagerImpl getInstance() {
    return INSTANCE;
  }

  private static @NotNull TextAttributes resolveAttributes(@NotNull TextAttributesKey attributesKey) {
    return EditorColorsManager.getInstance().getGlobalScheme().getAttributes(attributesKey);
  }

  @Override
  public @NotNull TextAttributes getClassDeclarationAttributes(@NotNull PsiClass aClass) {
    if (aClass.isInterface()) return getInterfaceNameAttributes();
    if (aClass.isEnum()) return getEnumNameAttributes();
    if (aClass instanceof PsiAnonymousClass) return getAnonymousClassNameAttributes();
    if (aClass.hasModifierProperty(PsiModifier.ABSTRACT)) return getAbstractClassNameAttributes();
    return getClassNameAttributes();
  }

  @Override
  public @NotNull TextAttributes getMethodDeclarationAttributes(@NotNull PsiMethod method) {
    return method.isConstructor()
           ? getConstructorDeclarationAttributes()
           : getMethodDeclarationAttributes();
  }

  @Override
  public @NotNull TextAttributes getFieldDeclarationAttributes(@NotNull PsiField field) {
    boolean isFinal = field.hasModifierProperty(PsiModifier.FINAL);
    boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
    boolean isImported = HighlightNamesUtil.isStaticallyImported(field);
    if (isImported) {
      return isFinal ? getStaticFinalFieldImportedAttributes()
                     : getStaticFieldImportedAttributes();
    }
    else if (isStatic) {
      return isFinal ? getStaticFinalFieldAttributes()
                     : getStaticFieldAttributes();
    }
    else {
      return isFinal ? getInstanceFinalFieldAttributes()
                     : getInstanceFieldAttributes();
    }
  }

  public @NotNull TextAttributes getInterfaceNameAttributes() {
    return resolveAttributes(JavaHighlightingColors.INTERFACE_NAME_ATTRIBUTES);
  }

  public @NotNull TextAttributes getEnumNameAttributes() {
    return resolveAttributes(JavaHighlightingColors.ENUM_NAME_ATTRIBUTES);
  }

  public @NotNull TextAttributes getAnonymousClassNameAttributes() {
    return resolveAttributes(JavaHighlightingColors.ANONYMOUS_CLASS_NAME_ATTRIBUTES);
  }

  public @NotNull TextAttributes getAbstractClassNameAttributes() {
    return resolveAttributes(JavaHighlightingColors.ABSTRACT_CLASS_NAME_ATTRIBUTES);
  }

  @Override
  public @NotNull TextAttributes getClassNameAttributes() {
    return resolveAttributes(JavaHighlightingColors.CLASS_NAME_ATTRIBUTES);
  }

  @Override
  public @NotNull TextAttributes getKeywordAttributes() {
    return resolveAttributes(JavaHighlightingColors.KEYWORD);
  }

  @Override
  public @NotNull TextAttributes getCommaAttributes() {
    return resolveAttributes(JavaHighlightingColors.COMMA);
  }

  @Override
  public @NotNull TextAttributes getParameterAttributes() {
    return resolveAttributes(JavaHighlightingColors.PARAMETER_ATTRIBUTES);
  }

  @Override
  public @NotNull TextAttributes getTypeParameterNameAttributes() {
    return resolveAttributes(JavaHighlightingColors.TYPE_PARAMETER_NAME_ATTRIBUTES);
  }

  public @NotNull TextAttributes getStaticFinalFieldImportedAttributes() {
    return resolveAttributes(JavaHighlightingColors.STATIC_FINAL_FIELD_IMPORTED_ATTRIBUTES);
  }

  public @NotNull TextAttributes getStaticFieldImportedAttributes() {
    return resolveAttributes(JavaHighlightingColors.STATIC_FIELD_IMPORTED_ATTRIBUTES);
  }

  public @NotNull TextAttributes getStaticFinalFieldAttributes() {
    return resolveAttributes(JavaHighlightingColors.STATIC_FINAL_FIELD_ATTRIBUTES);
  }

  public @NotNull TextAttributes getStaticFieldAttributes() {
    return resolveAttributes(JavaHighlightingColors.STATIC_FIELD_ATTRIBUTES);
  }

  public @NotNull TextAttributes getInstanceFinalFieldAttributes() {
    return resolveAttributes(JavaHighlightingColors.INSTANCE_FINAL_FIELD_ATTRIBUTES);
  }

  public @NotNull TextAttributes getInstanceFieldAttributes() {
    return resolveAttributes(JavaHighlightingColors.INSTANCE_FIELD_ATTRIBUTES);
  }

  @Override
  public @NotNull TextAttributes getOperationSignAttributes() {
    return resolveAttributes(JavaHighlightingColors.OPERATION_SIGN);
  }

  @Override
  public @NotNull TextAttributes getLocalVariableAttributes() {
    return resolveAttributes(JavaHighlightingColors.LOCAL_VARIABLE_ATTRIBUTES);
  }

  public @NotNull TextAttributes getConstructorDeclarationAttributes() {
    return resolveAttributes(JavaHighlightingColors.CONSTRUCTOR_DECLARATION_ATTRIBUTES);
  }

  public @NotNull TextAttributes getMethodDeclarationAttributes() {
    return resolveAttributes(JavaHighlightingColors.METHOD_DECLARATION_ATTRIBUTES);
  }

  @Override
  public @NotNull TextAttributes getParenthesesAttributes() {
    return resolveAttributes(JavaHighlightingColors.PARENTHESES);
  }

  @Override
  public @NotNull TextAttributes getDotAttributes() {
    return resolveAttributes(JavaHighlightingColors.DOT);
  }

  @Override
  public @NotNull TextAttributes getBracketsAttributes() {
    return resolveAttributes(JavaHighlightingColors.BRACKETS);
  }

  @Override
  public @NotNull TextAttributes getMethodCallAttributes() {
    return resolveAttributes(JavaHighlightingColors.METHOD_CALL_ATTRIBUTES);
  }
}
