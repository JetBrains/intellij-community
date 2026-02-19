// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemDescriptorUtil;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Pair;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.Interner;
import com.intellij.xml.util.XmlStringUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntSupplier;

public class ProblemDescriptionNode extends SuppressableInspectionTreeNode {
  private final CommonProblemDescriptor myDescriptor;
  private final HighlightDisplayLevel myLevel;
  protected final int myLineNumber;
  protected final RefEntity myElement;
  private @Nullable String myMessage = null;

  public ProblemDescriptionNode(RefEntity element,
                                @NotNull CommonProblemDescriptor descriptor,
                                @NotNull InspectionToolPresentation presentation,
                                @NotNull InspectionTreeNode parent) {
    this(element, descriptor, presentation, null, parent);
  }

  protected ProblemDescriptionNode(@Nullable RefEntity element,
                                   CommonProblemDescriptor descriptor,
                                   @NotNull InspectionToolPresentation presentation,
                                   @Nullable IntSupplier lineNumberCounter,
                                   @NotNull InspectionTreeNode parent) {
    super(presentation, parent);
    myElement = element;
    myDescriptor = descriptor;
    myLevel = ObjectUtils.notNull(calculatePreciseLevel(element, descriptor, presentation), () -> {
      String shortName = presentation.getToolWrapper().getShortName();
      final InspectionProfileImpl profile = presentation.getContext().getCurrentProfile();
      return profile.getTools(shortName, presentation.getContext().getProject()).getLevel();
    });
    myLineNumber = myDescriptor instanceof ProblemDescriptor
                   ? ((ProblemDescriptor)myDescriptor).getLineNumber()
                   : lineNumberCounter == null ? -1 : lineNumberCounter.getAsInt();
  }

  private static HighlightDisplayLevel calculatePreciseLevel(@Nullable RefEntity element,
                                                             @Nullable CommonProblemDescriptor descriptor,
                                                             @NotNull InspectionToolPresentation presentation) {
    if (element == null) return null;
    final InspectionProfileImpl profile = presentation.getContext().getCurrentProfile();
    String shortName = presentation.getToolWrapper().getShortName();
    if (descriptor instanceof ProblemDescriptor) {
      InspectionProfileManager inspectionProfileManager = profile.getProfileManager();
      RefElement refElement = (RefElement)element;
      SeverityRegistrar severityRegistrar = inspectionProfileManager.getSeverityRegistrar();
      HighlightSeverity severity = presentation.getSeverity(refElement);
      if (severity == null) return null;
      HighlightInfoType highlightInfoType = ProblemDescriptorUtil.highlightTypeFromDescriptor((ProblemDescriptor)descriptor, severity, severityRegistrar);
      HighlightSeverity highlightSeverity = highlightInfoType.getSeverity(refElement.getPsiElement());
      return HighlightDisplayLevel.find(highlightSeverity);
    }
    else {
      return profile.getTools(shortName, presentation.getContext().getProject()).getLevel();
    }
  }

  public boolean needCalculateTooltip() {
    return myMessage == null;
  }

  public @Nullable String getToolTipText() {
    if (!isValid()) return null;
    if (myMessage != null) return myMessage;
    CommonProblemDescriptor descriptor = getDescriptor();
    if (descriptor == null) return null;

    PsiElement element = descriptor instanceof ProblemDescriptor ? ((ProblemDescriptor)descriptor).getPsiElement() : null;
    String message = ProblemDescriptorUtil.renderDescriptor(descriptor, element, ProblemDescriptorUtil.NONE).getTooltip();
    myMessage = XmlStringUtil.isWrappedInHtml(message) ? message : XmlStringUtil.wrapInHtml(XmlStringUtil.escapeString(message));
    return myMessage;
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

  public @NotNull InspectionToolWrapper<?, ?> getToolWrapper() {
    return getPresentation().getToolWrapper();
  }

  @Override
  public @Nullable RefEntity getElement() {
    return myElement;
  }

  public @Nullable CommonProblemDescriptor getDescriptor() {
    return myDescriptor;
  }

  @Override
  public void excludeElement() {
    CommonProblemDescriptor descriptor = getDescriptor();
    if (descriptor != null) {
      getPresentation().exclude(descriptor);
    }
    dropProblemCountCaches();
  }

  @Override
  public void amnestyElement() {
    CommonProblemDescriptor descriptor = getDescriptor();
    if (descriptor != null) {
      getPresentation().amnesty(descriptor);
    }
    dropProblemCountCaches();
  }

  @Override
  protected void visitProblemSeverities(@NotNull Object2IntMap<HighlightDisplayLevel> counter) {
    if (isValid() && !isExcluded() && !isQuickFixAppliedFromView() && !isAlreadySuppressedFromView()) {
      counter.put(myLevel, counter.getInt(myLevel) + 1);
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
    CommonProblemDescriptor descriptor = getDescriptor();
    return descriptor != null && getPresentation().isExcluded(descriptor);
  }

  private static final Interner<String> NAME_INTERNER = Interner.createWeakInterner();

  @Override
  protected @NotNull String calculatePresentableName() {
    CommonProblemDescriptor descriptor = getDescriptor();
    if (descriptor == null) return "";
    String name = ReadAction.compute(() -> {
      PsiElement element = descriptor instanceof ProblemDescriptor ? ((ProblemDescriptor)descriptor).getPsiElement() : null;
      return ProblemDescriptorUtil.renderDescriptionMessage(descriptor, element, ProblemDescriptorUtil.TRIM_AT_TREE_END);
    });
    return NAME_INTERNER.intern(name);
  }

  @Override
  public boolean isQuickFixAppliedFromView() {
    return myDescriptor != null && getPresentation().isProblemResolved(myDescriptor) && !isAlreadySuppressedFromView();
  }

  @Override
  public @Nullable String getTailText() {
    final String text = super.getTailText();
    return text == null ? "" : text;
  }

  @Override
  public @NotNull Pair<PsiElement, CommonProblemDescriptor> getSuppressContent() {
    RefEntity refElement = getElement();
    CommonProblemDescriptor descriptor = getDescriptor();
    PsiElement element = descriptor instanceof ProblemDescriptor
                         ? ((ProblemDescriptor)descriptor).getPsiElement()
                         : refElement instanceof RefElement
                           ? ((RefElement)refElement).getPsiElement()
                           : null;
    return Pair.create(element, descriptor);
  }
}
