/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.highlighting;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiReference;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface DomElementAnnotationHolder extends Iterable<DomElementProblemDescriptor>{

  @NotNull
  DomElementProblemDescriptor createProblem(DomElement domElement, @Nullable String message);

  @NotNull
  DomElementProblemDescriptor createProblem(DomElement domElement, DomCollectionChildDescription childDescription, @Nullable String message);

  @NotNull
  DomElementProblemDescriptor createProblem(DomElement domElement, HighlightSeverity highlightType, String message);

  @NotNull
  DomElementResolveProblemDescriptor createResolveProblem(@NotNull GenericDomValue element, @NotNull PsiReference reference);

  int getSize();
}
