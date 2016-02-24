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
import com.intellij.openapi.util.Computable;
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
  OfflineProblemDescriptorNode(@NotNull OfflineProblemDescriptor descriptor,
                               @NotNull LocalInspectionToolWrapper toolWrapper,
                               @NotNull InspectionToolPresentation presentation) {
    super(descriptor, toolWrapper, presentation);
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

  @Override
  @Nullable
  public RefEntity getElement() {
    if (userObject instanceof CommonProblemDescriptor) {
      return myElement;
    }
    if (userObject == null) {
      return null;
    }
    myElement = ((OfflineProblemDescriptor)userObject).getRefElement(myPresentation.getContext().getRefManager());
    return myElement;
  }

  @Override
  @Nullable
  public CommonProblemDescriptor getDescriptor() {
    if (userObject == null) return null;
    if (userObject instanceof CommonProblemDescriptor) {
      return (CommonProblemDescriptor)userObject;
    }

    final InspectionManager inspectionManager = InspectionManager.getInstance(myPresentation.getContext().getProject());
    final OfflineProblemDescriptor offlineProblemDescriptor = (OfflineProblemDescriptor)userObject;
    final RefEntity element = getElement();
    if (myToolWrapper instanceof LocalInspectionToolWrapper) {
      if (element instanceof RefElement) {
        final PsiElement psiElement = ((RefElement)element).getElement();
        if (psiElement != null) {
          ProblemDescriptor descriptor = ProgressManager.getInstance().runProcess(new Computable<ProblemDescriptor>() {
            @Override
            public ProblemDescriptor compute() {
              return runLocalTool(psiElement, inspectionManager, offlineProblemDescriptor);
            }
          }, new DaemonProgressIndicator());
          if (descriptor != null) return descriptor;
        }
      }
      setUserObject(null);
      return null;
    }
    final List<String> hints = offlineProblemDescriptor.getHints();
    if (element instanceof RefElement) {
      final PsiElement psiElement = ((RefElement)element).getElement();
      if (psiElement == null) return null;
      ProblemDescriptor descriptor = inspectionManager.createProblemDescriptor(psiElement, offlineProblemDescriptor.getDescription(),
                                                                               (LocalQuickFix)null,
                                                                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false);
      final LocalQuickFix[] quickFixes = getFixes(descriptor, hints);
      if (quickFixes != null) {
        descriptor = inspectionManager.createProblemDescriptor(psiElement, offlineProblemDescriptor.getDescription(), false, quickFixes,
                                                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
      }
      setUserObject(descriptor);
      return descriptor;
    }
    CommonProblemDescriptor descriptor =
      inspectionManager.createProblemDescriptor(offlineProblemDescriptor.getDescription(), (QuickFix)null);
    final QuickFix[] quickFixes = getFixes(descriptor, hints);
    if (quickFixes != null) {
      descriptor = inspectionManager.createProblemDescriptor(offlineProblemDescriptor.getDescription(), quickFixes);
    }
    setUserObject(descriptor);
    return descriptor;
  }

  private ProblemDescriptor runLocalTool(@NotNull PsiElement psiElement,
                                         @NotNull InspectionManager inspectionManager,
                                         @NotNull OfflineProblemDescriptor offlineProblemDescriptor) {
    PsiFile containingFile = psiElement.getContainingFile();
    final ProblemsHolder holder = new ProblemsHolder(inspectionManager, containingFile, false);
    final LocalInspectionTool localTool = ((LocalInspectionToolWrapper)myToolWrapper).getTool();
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
            setUserObject(descriptor);
            return descriptor;
          }
          curIdx++;
        }
      }
    }

    return null;
  }

  @Nullable
  private LocalQuickFix[] getFixes(@NotNull CommonProblemDescriptor descriptor, List<String> hints) {
    final List<LocalQuickFix> fixes = new ArrayList<LocalQuickFix>(hints == null ? 1 : hints.size());
    if (hints == null) {
      addFix(descriptor, fixes, null);
    }
    else {
      for (String hint : hints) {
        addFix(descriptor, fixes, hint);
      }
    }
    return fixes.isEmpty() ? null : fixes.toArray(new LocalQuickFix[fixes.size()]);
  }

  private void addFix(@NotNull CommonProblemDescriptor descriptor, final List<LocalQuickFix> fixes, String hint) {
    final IntentionAction intentionAction = myPresentation.findQuickFixes(descriptor, hint);
    if (intentionAction instanceof QuickFixWrapper) {
      fixes.add(((QuickFixWrapper)intentionAction).getFix());
    }
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public FileStatus getNodeStatus() {
    return FileStatus.NOT_CHANGED;
  }

  public String toString() {
    if (userObject instanceof OfflineProblemDescriptor) {
      return ((OfflineProblemDescriptor)userObject).getDescription();
    }
    return super.toString();
  }
}
