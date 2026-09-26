// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceCodeFragment;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class PsiJavaCodeReferenceCodeFragmentImpl extends PsiCodeFragmentImpl implements PsiJavaCodeReferenceCodeFragment {
  private static final Logger LOG = Logger.getInstance(PsiJavaCodeReferenceCodeFragmentImpl.class);
  private final boolean myIsClassesAccepted;

  public PsiJavaCodeReferenceCodeFragmentImpl(@NotNull Project project,
                                              boolean isPhysical,
                                              @NonNls @NotNull String name,
                                              @NotNull CharSequence text,
                                              boolean isClassesAccepted,
                                              @Nullable PsiElement context) {
    super(project, JavaElementType.REFERENCE_TEXT, isPhysical, name, text, context);
    myIsClassesAccepted = isClassesAccepted;
  }

  public PsiJavaCodeReferenceCodeFragmentImpl(Project project,
                                              boolean isPhysical,
                                              String name,
                                              CharSequence text,
                                              boolean isClassesAccepted, @Nullable String packageName) {
    super(project, JavaElementType.REFERENCE_TEXT, isPhysical, name, text, packageName);
    myIsClassesAccepted = isClassesAccepted;
  }

  @Override
  public PsiJavaCodeReferenceElement getReferenceElement() {
    final CompositeElement treeElement = calcTreeElement();
    LOG.assertTrue (treeElement.getFirstChildNode().getElementType() == JavaElementType.JAVA_CODE_REFERENCE);
    return (PsiJavaCodeReferenceElement)SourceTreeToPsiMap.treeElementToPsi(treeElement.getFirstChildNode());
  }

  @Override
  public boolean isClassesAccepted() {
    return myIsClassesAccepted;
  }
}
