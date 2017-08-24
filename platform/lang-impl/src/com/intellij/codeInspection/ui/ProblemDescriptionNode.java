/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.xml.util.XmlStringUtil;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntSupplier;

import static com.intellij.codeInspection.ProblemDescriptorUtil.TRIM_AT_TREE_END;

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
    this(element, descriptor, presentation, true, null);
  }

  protected ProblemDescriptionNode(@Nullable RefEntity element,
                                   CommonProblemDescriptor descriptor,
                                   @NotNull InspectionToolPresentation presentation,
                                   boolean doInit,
                                   @Nullable IntSupplier lineNumberCounter) {
    super(descriptor, presentation);
    myElement = element;
    myDescriptor = descriptor;
    final InspectionProfileImpl profile = presentation.getContext().getCurrentProfile();
    String shortName = presentation.getToolWrapper().getShortName();
    myLevel = descriptor instanceof ProblemDescriptor
              ? profile.getErrorLevel(HighlightDisplayKey.find(shortName), ((ProblemDescriptor)descriptor).getStartElement())
              : profile.getTools(shortName, presentation.getContext().getProject()).getLevel();
    if (doInit) {
      init(presentation.getContext().getProject());
    }
    myLineNumber = myDescriptor instanceof ProblemDescriptor ? ((ProblemDescriptor)myDescriptor).getLineNumber() : (lineNumberCounter == null ? -1 : lineNumberCounter.getAsInt());
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

  @Nullable
  public RefEntity getElement() {
    return myElement;
  }

  @Nullable
  public CommonProblemDescriptor getDescriptor() {
    return myDescriptor;
  }

  @Override
  protected void visitProblemSeverities(TObjectIntHashMap<HighlightDisplayLevel> counter) {
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
  protected void dropCache(Project project) {
    if (!isQuickFixAppliedFromView()) {
      super.dropCache(project);
    }
  }

  @NotNull
  @Override
  protected String calculatePresentableName() {
    CommonProblemDescriptor descriptor = getDescriptor();
    if (descriptor == null) return "";
    PsiElement element = descriptor instanceof ProblemDescriptor ? ((ProblemDescriptor)descriptor).getPsiElement() : null;

    return XmlStringUtil.stripHtml(ProblemDescriptorUtil.renderDescriptionMessage(descriptor, element, TRIM_AT_TREE_END));
  }

  @Override
  public boolean isQuickFixAppliedFromView() {
    return (myDescriptor != null && getPresentation().isProblemResolved(myDescriptor)) && !isAlreadySuppressedFromView();
  }

  @Nullable
  @Override
  public String getTailText() {
    if (isQuickFixAppliedFromView()) {
      return "";
    }
    else {
      final String text = super.getTailText();
      return text == null ? "" : text;
    }
  }
}
