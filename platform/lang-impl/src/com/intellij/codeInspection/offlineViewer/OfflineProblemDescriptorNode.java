/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 09-Jan-2007
 */
package com.intellij.codeInspection.offlineViewer;

import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil;
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.codeInspection.offline.OfflineProblemDescriptor;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.ui.InspectionToolPresentation;
import com.intellij.codeInspection.ui.ProblemDescriptionNode;
import com.intellij.lang.Language;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class OfflineProblemDescriptorNode extends ProblemDescriptionNode {
  OfflineProblemDescriptorNode(RefEntity refEntity,
                               CommonProblemDescriptor descriptor,
                               @NotNull LocalInspectionToolWrapper toolWrapper,
                               @NotNull InspectionToolPresentation presentation,
                               @NotNull OfflineProblemDescriptor offlineDescriptor) {
    super(refEntity, descriptor, toolWrapper, presentation, false, offlineDescriptor::getLine);
    if (descriptor == null) {
      setUserObject(offlineDescriptor);
    }
    init(presentation.getContext().getProject());
  }

  static OfflineProblemDescriptorNode create(@NotNull OfflineProblemDescriptor offlineDescriptor,
                                             @NotNull LocalInspectionToolWrapper toolWrapper,
                                             @NotNull InspectionToolPresentation presentation) {
    final RefEntity refElement = createRefElement(offlineDescriptor, presentation);
    final CommonProblemDescriptor descriptor = createDescriptor(refElement, offlineDescriptor, toolWrapper, presentation);
    return new OfflineProblemDescriptorNode(refElement, descriptor, toolWrapper, presentation, offlineDescriptor);
  }

  @Override
  public FileStatus getNodeStatus() {
    return FileStatus.NOT_CHANGED;
  }

  @NotNull
  @Override
  protected String calculatePresentableName() {
    String presentableName = super.calculatePresentableName();
    return presentableName.isEmpty() && getUserObject() instanceof OfflineProblemDescriptor
           ? StringUtil.notNullize(((OfflineProblemDescriptor)getUserObject()).getDescription())
           : presentableName;
  }

  private static PsiElement[] getElementsIntersectingRange(PsiFile file, final int startOffset, final int endOffset) {
    final FileViewProvider viewProvider = file.getViewProvider();
    final Set<PsiElement> result = new LinkedHashSet<PsiElement>();
    for (Language language : viewProvider.getLanguages()) {
      final PsiFile psiRoot = viewProvider.getPsi(language);
      if (HighlightingLevelManager.getInstance(file.getProject()).shouldInspect(psiRoot)) {
        result.addAll(CollectHighlightsUtil.getElementsInRange(psiRoot, startOffset, endOffset, true));
      }
    }
    return PsiUtilCore.toPsiElementArray(result);
  }

  @Nullable
  private static RefEntity createRefElement(OfflineProblemDescriptor descriptor, InspectionToolPresentation presentation) {
    return descriptor.getRefElement(presentation.getContext().getRefManager());
  }

  @Nullable
  private static CommonProblemDescriptor createDescriptor(@Nullable RefEntity element,
                                                          @NotNull OfflineProblemDescriptor offlineDescriptor,
                                                          @NotNull LocalInspectionToolWrapper toolWrapper,
                                                          @NotNull InspectionToolPresentation presentation) {

    final InspectionManager inspectionManager = InspectionManager.getInstance(presentation.getContext().getProject());
    final OfflineProblemDescriptor offlineProblemDescriptor = offlineDescriptor;
    if (element instanceof RefElement) {
      final PsiElement psiElement = ((RefElement)element).getElement();
      if (psiElement != null) {
        ProblemDescriptor descriptor = ProgressManager.getInstance().runProcess(
          () -> runLocalTool(psiElement, inspectionManager, offlineProblemDescriptor, toolWrapper), new DaemonProgressIndicator());
        if (descriptor != null) return descriptor;
      }
      return null;
    }
    final List<String> hints = offlineProblemDescriptor.getHints();
    CommonProblemDescriptor descriptor =
      inspectionManager.createProblemDescriptor(offlineProblemDescriptor.getDescription(), (QuickFix)null);
    final QuickFix[] quickFixes = getFixes(descriptor, hints, presentation);
    if (quickFixes != null) {
      descriptor = inspectionManager.createProblemDescriptor(offlineProblemDescriptor.getDescription(), quickFixes);
    }
    return descriptor;
  }

  private static ProblemDescriptor runLocalTool(@NotNull PsiElement psiElement,
                                                @NotNull InspectionManager inspectionManager,
                                                @NotNull OfflineProblemDescriptor offlineProblemDescriptor,
                                                @NotNull LocalInspectionToolWrapper toolWrapper) {
    PsiFile containingFile = psiElement.getContainingFile();
    final ProblemsHolder holder = new ProblemsHolder(inspectionManager, containingFile, false);
    final LocalInspectionTool localTool = toolWrapper.getTool();
    final int startOffset = psiElement.getTextRange().getStartOffset();
    final int endOffset = psiElement.getTextRange().getEndOffset();
    LocalInspectionToolSession session = new LocalInspectionToolSession(containingFile, startOffset, endOffset);
    final PsiElementVisitor visitor = localTool.buildVisitor(holder, false, session);
    localTool.inspectionStarted(session, false);
    final PsiElement[] elementsInRange = getElementsIntersectingRange(containingFile, startOffset, endOffset);
    for (PsiElement element : elementsInRange) {
      element.accept(visitor);
    }
    localTool.inspectionFinished(session, holder);
    if (holder.hasResults()) {
      final List<ProblemDescriptor> list = holder.getResults();
      final int idx = offlineProblemDescriptor.getProblemIndex();
      int curIdx = 0;
      for (ProblemDescriptor descriptor : list) {
        final PsiNamedElement member = localTool.getProblemElement(descriptor.getPsiElement());
        if (psiElement instanceof PsiFile || member != null && member.equals(psiElement)) {
          if (curIdx == idx) {
            return descriptor;
          }
          curIdx++;
        }
      }
    }

    return null;
  }

  @Nullable
  private static LocalQuickFix[] getFixes(@NotNull CommonProblemDescriptor descriptor, List<String> hints, InspectionToolPresentation presentation) {
    final List<LocalQuickFix> fixes = new ArrayList<LocalQuickFix>(hints == null ? 1 : hints.size());
    if (hints == null) {
      addFix(descriptor, fixes, null, presentation);
    }
    else {
      for (String hint : hints) {
        addFix(descriptor, fixes, hint, presentation);
      }
    }
    return fixes.isEmpty() ? null : fixes.toArray(new LocalQuickFix[fixes.size()]);
  }

  private static void addFix(@NotNull CommonProblemDescriptor descriptor, final List<LocalQuickFix> fixes, String hint, InspectionToolPresentation presentation) {
    final IntentionAction intentionAction = presentation.findQuickFixes(descriptor, hint);
    if (intentionAction instanceof QuickFixWrapper) {
      fixes.add(((QuickFixWrapper)intentionAction).getFix());
    }
  }

}
