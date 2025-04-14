// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.TypeAnnotationProvider;
import org.jetbrains.annotations.NotNull;

/**
 * A container that reports external type annotations. External type annotations are described in annotation.xml files
 * with additional {@code typePath} attribute. The attribute contains several components starting with '/' and separated with '/'
 * (no ending '/' is allowed). The allowed components are the following:
 * <ul>
 *   <li>{@code number} - one-based type argument index (1-255)
 *   <li>{@code *} (asterisk) - bound of a wildcard type
 *   <li>{@code []} (square brackets) - array element (also works for varargs)
 *   <li>{@code .} (dot) - enclosing type of inner type
 * </ul>
 * E.g., for type {@code Entry<? extends K, ? extends V>[]} the typePath {@code /[]/2/*} points to {@code V}
 */
public final class ExternalTypeAnnotationContainer implements TypeAnnotationContainer {
  private final @NotNull String myTypePath;
  private final @NotNull PsiModifierListOwner myOwner;

  private ExternalTypeAnnotationContainer(@NotNull String typePath, @NotNull PsiModifierListOwner owner) {
    myTypePath = typePath;
    myOwner = owner;
  }
  
  @Override
  public @NotNull TypeAnnotationContainer forArrayElement() {
    return new ExternalTypeAnnotationContainer(myTypePath + "/[]", myOwner);
  }

  @Override
  public @NotNull TypeAnnotationContainer forEnclosingClass() {
    return new ExternalTypeAnnotationContainer(myTypePath + "/.", myOwner);
  }

  @Override
  public @NotNull TypeAnnotationContainer forBound() {
    return new ExternalTypeAnnotationContainer(myTypePath + "/*", myOwner);
  }

  @Override
  public @NotNull TypeAnnotationContainer forTypeArgument(int index) {
    return new ExternalTypeAnnotationContainer(myTypePath + "/" + (index + 1), myOwner);
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
  public void appendImmediateText(@NotNull StringBuilder sb) {
    throw new UnsupportedOperationException();
  }

  public static @NotNull TypeAnnotationContainer create(@NotNull PsiModifierListOwner owner) {
    return new ExternalTypeAnnotationContainer("", owner);
  }
}
