// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting.errors;

import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiAnnotationMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Java compilation error connected to annotation attribute value
 * @param <Psi> type of annotation attribute value
 */
public abstract class JavaAnnotationValueErrorKind<Psi extends PsiAnnotationMemberValue> 
  extends Parameterized<Psi, JavaAnnotationValueErrorKind.@NotNull AnnotationValueErrorContext> {
  JavaAnnotationValueErrorKind(@NotNull String key) {
    super(key);
  }

  public @NotNull JavaCompilationError<Psi, AnnotationValueErrorContext> create(@NotNull Psi value,
                                                                                @NotNull PsiAnnotationMethod method,
                                                                                @NotNull PsiType expectedType) {
    boolean fromDefaultValue = PsiTreeUtil.isAncestor(method.getDefaultValue(), value, false);
    return super.create(value, new AnnotationValueErrorContext(method, expectedType, fromDefaultValue));
  }

  public record AnnotationValueErrorContext(@NotNull PsiAnnotationMethod method, 
                                            @NotNull PsiType expectedType, 
                                            boolean fromDefaultValue) {
    public @NotNull String typeText() {
      return PsiTypesUtil.removeExternalAnnotations(expectedType()).getInternalCanonicalText();
    }
  } 
}
