// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.reference.RefDirectory;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class RefElementNode extends SuppressableInspectionTreeNode {
  private final Icon myIcon;
  private final @Nullable RefEntity myRefEntity;

  public RefElementNode(@Nullable RefEntity refEntity,
                        @NotNull InspectionToolPresentation presentation,
                        @NotNull InspectionTreeNode parent) {
    super(presentation, parent);
    myRefEntity = refEntity;
    Icon icon = refEntity == null ? null : refEntity.getIcon(false);
    myIcon = icon == null ? null : IconUtil.deepRetrieveIconNow(icon);
  }

  @Override
  public final boolean isAlreadySuppressedFromView() {
    return getElement() != null && getPresentation().isSuppressed(getElement());
  }

  @Override
  public @Nullable RefEntity getElement() {
    return myRefEntity;
  }

  @Override
  public @Nullable Icon getIcon(boolean expanded) {
    return myIcon;
  }

  @Override
  protected String calculatePresentableName() {
    final RefEntity element = getElement();
    if (element == null) {
      return AnalysisBundle.message("inspection.reference.invalid");
    }
    return element.getRefManager().getRefinedElement(element).getName();
  }

  @Override
  protected boolean calculateIsValid() {
    final RefEntity refEntity = getElement();
    return refEntity != null && refEntity.isValid();
  }

  @Override
  public boolean isExcluded() {
    RefEntity element = getElement();
    if (isLeaf() && element != null) {
      return getPresentation().isExcluded(element);
    }
    return super.isExcluded();
  }

  @Override
  public void excludeElement() {
    RefEntity element = getElement();
    if (isLeaf() && element != null) {
      getPresentation().exclude(element);
    }
    super.excludeElement();
  }

  @Override
  public void amnestyElement() {
    RefEntity element = getElement();
    if (isLeaf() && element != null) {
      getPresentation().amnesty(element);
    }
    super.amnestyElement();
  }

  @Override
  public RefEntity getContainingFileLocalEntity() {
    final RefEntity element = getElement();
    return element instanceof RefElement && !(element instanceof RefDirectory)
           ? element
           : super.getContainingFileLocalEntity();
  }

  @Override
  public boolean isQuickFixAppliedFromView() {
    return isLeaf() && getPresentation().isProblemResolved(getElement());
  }

  @Override
  public @Nullable String getTailText() {
    if (getPresentation().isDummy()) {
      return "";
    }
    final String customizedText = super.getTailText();
    if (customizedText != null) {
      return customizedText;
    }
    return isLeaf() ? "" : null;
  }

  @Override
  public @NotNull Pair<PsiElement, CommonProblemDescriptor> getSuppressContent() {
    RefEntity refElement = getElement();
    PsiElement element = refElement instanceof RefElement ? ((RefElement)refElement).getPsiElement() : null;
    return Pair.create(element, null);
  }
}
