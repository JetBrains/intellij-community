// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * A container that reports external type annotations. External type annotations are described in annotation.xml files
 * with additional {@code typePath} attribute. The attribute syntax is the following:
 * <ul>
 *   <li>{@code digit;} - zero-based type argument
 *   <li>{@code *} (asterisk) - bound of a wildcard type
 *   <li>{@code [} (left bracket) - array element
 *   <li>{@code .} (dot) - enclosing type of inner type
 * </ul>
 * E.g., for type {@code Consumer<? super T>} the typePath {@code 0;*} points to {@code T}
 */
public final class ExternalTypeAnnotationContainer implements TypeAnnotationContainer {
  @NotNull private final String myTypePath;
  @NotNull private final PsiModifierListOwner myOwner;

  private ExternalTypeAnnotationContainer(@NotNull String typePath, @NotNull PsiModifierListOwner owner) {
    myTypePath = typePath;
    myOwner = owner;
  }
  
  @Override
  public @NotNull TypeAnnotationContainer forArrayElement() {
    return new ExternalTypeAnnotationContainer(myTypePath + "[", myOwner);
  }

  @Override
  public @NotNull TypeAnnotationContainer forEnclosingClass() {
    return new ExternalTypeAnnotationContainer(myTypePath + ".", myOwner);
  }

  @Override
  public @NotNull TypeAnnotationContainer forBound() {
    return new ExternalTypeAnnotationContainer(myTypePath + "*", myOwner);
  }

  @Override
  public @NotNull TypeAnnotationContainer forTypeArgument(int index) {
    return new ExternalTypeAnnotationContainer(myTypePath + index + ";", myOwner);
  }

  @Override
  public @NotNull TypeAnnotationProvider getProvider(PsiElement parent) {
    // We don't expect any top-level type annotations: they will be stored as element (method/field/parameter) annotations,
    // so let's spare some memory and avoid creating a provider
    if (myTypePath.isEmpty()) return TypeAnnotationProvider.EMPTY;
    return new TypeAnnotationProvider() {
      @Override
      public @NotNull PsiAnnotation @NotNull [] getAnnotations() {
        return ExternalAnnotationsManager.getInstance(myOwner.getProject()).findExternalTypeAnnotations(myOwner, myTypePath);
      }
    };
  }

  @Override
  public @NotNull PsiType applyTo(@NotNull PsiType type, @NotNull PsiElement context) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull PsiJavaCodeReferenceElement annotateReference(@NotNull PsiJavaCodeReferenceElement reference,
                                                                @NotNull PsiElement context) {
    throw new UnsupportedOperationException();
  }
  
  public static @NotNull TypeAnnotationContainer create(@NotNull PsiModifierListOwner owner) {
    return new ExternalTypeAnnotationContainer("", owner);
  }
}
