/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.codeInspection.ui;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemDescriptorUtil;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.WeakStringInterner;
import com.intellij.xml.util.XmlStringUtil;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntSupplier;

/**
 * @author max
 */
public class ProblemDescriptionNode extends SuppressableInspectionTreeNode {
  private final CommonProblemDescriptor myDescriptor;
  private final HighlightDisplayLevel myLevel;
  protected final int myLineNumber;
  protected final RefEntity myElement;

  public ProblemDescriptionNode(RefEntity element,
                                CommonProblemDescriptor descriptor,
                                @NotNull InspectionToolPresentation presentation) {
    this(element, descriptor, presentation, null);
  }

  protected ProblemDescriptionNode(@Nullable RefEntity element,
                                   CommonProblemDescriptor descriptor,
                                   @NotNull InspectionToolPresentation presentation,
                                   @Nullable IntSupplier lineNumberCounter) {
    super(descriptor, presentation);
    myElement = element;
    myDescriptor = descriptor;
    final InspectionProfileImpl profile = presentation.getContext().getCurrentProfile();
    String shortName = presentation.getToolWrapper().getShortName();
    myLevel = descriptor instanceof ProblemDescriptor
              ? profile.getErrorLevel(HighlightDisplayKey.find(shortName), ((ProblemDescriptor)descriptor).getStartElement())
              : profile.getTools(shortName, presentation.getContext().getProject()).getLevel();
    myLineNumber = myDescriptor instanceof ProblemDescriptor
                   ? ((ProblemDescriptor)myDescriptor).getLineNumber()
                   : lineNumberCounter == null ? -1 : lineNumberCounter.getAsInt();
  }

  @Nullable
  public String getToolTipText() {
    if (!isValid()) return null;
    CommonProblemDescriptor descriptor = getDescriptor();
    if (descriptor == null) return null;
    PsiElement element = descriptor instanceof ProblemDescriptor ? ((ProblemDescriptor)descriptor).getPsiElement() : null;
    return ProblemDescriptorUtil.renderDescriptionMessage(descriptor, element, false);
  }

  @Override
  public final boolean isAlreadySuppressedFromView() {
    return myDescriptor != null && getPresentation().isSuppressed(myDescriptor);
  }

  public int getLineNumber() {
    return myLineNumber;
  }

  @Override
  public boolean canSuppress() {
    return super.canSuppress() && !isQuickFixAppliedFromView();
  }

  @NotNull
  public InspectionToolWrapper getToolWrapper() {
    return getPresentation().getToolWrapper();
  }

  @Override
  @Nullable
  public RefEntity getElement() {
    return myElement;
  }

  @Override
  @Nullable
  public CommonProblemDescriptor getDescriptor() {
    return myDescriptor;
  }

  @Override
  public void excludeElement() {
    CommonProblemDescriptor descriptor = getDescriptor();
    if (descriptor != null) {
      getPresentation().exclude(descriptor);
    }
  }

  @Override
  public void amnestyElement() {
    CommonProblemDescriptor descriptor = getDescriptor();
    if (descriptor != null) {
      getPresentation().amnesty(descriptor);
    }
  }

  @Override
  protected void visitProblemSeverities(@NotNull TObjectIntHashMap<HighlightDisplayLevel> counter) {
    if (isValid() && !isExcluded() && !isQuickFixAppliedFromView() && !isAlreadySuppressedFromView()) {
      counter.put(myLevel, counter.get(myLevel) + 1);
    }
  }

  @Override
  protected boolean calculateIsValid() {
    if (myDescriptor == null) return false;
    if (myElement == null || !myElement.isValid()) return false;
    if (myDescriptor instanceof ProblemDescriptor) {
      final PsiElement psiElement = ((ProblemDescriptor)myDescriptor).getPsiElement();
      return psiElement != null && psiElement.isValid();
    }
    return true;
  }

  @Override
  public boolean isExcluded() {
    return getPresentation().isExcluded(getDescriptor());
  }

  private static final WeakStringInterner NAME_INTERNER = new WeakStringInterner();

  @NotNull
  @Override
  protected String calculatePresentableName() {
    CommonProblemDescriptor descriptor = getDescriptor();
    if (descriptor == null) return "";
    PsiElement element = descriptor instanceof ProblemDescriptor ? ((ProblemDescriptor)descriptor).getPsiElement() : null;

    String name = XmlStringUtil.stripHtml(ProblemDescriptorUtil.renderDescriptionMessage(descriptor, element,
                                                                                         ProblemDescriptorUtil.TRIM_AT_TREE_END));
    return NAME_INTERNER.intern(name);
  }

  @Override
  public boolean isQuickFixAppliedFromView() {
    return myDescriptor != null && getPresentation().isProblemResolved(myDescriptor) && !isAlreadySuppressedFromView();
  }

  @Nullable
  @Override
  public String getTailText() {
    final String text = super.getTailText();
    return text == null ? "" : text;
  }
}
