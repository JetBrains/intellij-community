/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInspection.offlineViewer;

import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil;
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.actions.RunInspectionAction;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.offline.OfflineProblemDescriptor;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefModule;
import com.intellij.codeInspection.ui.InspectionToolPresentation;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Dmitry Batkovich
 */
class OfflineDescriptorResolveResult {
  private static final Logger LOG = Logger.getInstance(OfflineDescriptorResolveResult.class);
  private final RefEntity myResolvedEntity;
  private final CommonProblemDescriptor myResolvedDescriptor;
  private volatile boolean myExcluded;

  public OfflineDescriptorResolveResult(RefEntity resolvedEntity, CommonProblemDescriptor resolvedDescriptor) {
    myResolvedEntity = resolvedEntity;
    myResolvedDescriptor = resolvedDescriptor;
  }

  @Nullable
  public RefEntity getResolvedEntity() {
    return myResolvedEntity;
  }

  @Nullable
  public CommonProblemDescriptor getResolvedDescriptor() {
    return myResolvedDescriptor;
  }

  public boolean isExcluded() {
    return myExcluded;
  }

  public void setExcluded(boolean excluded) {
    myExcluded = excluded;
  }

  @NotNull
  static OfflineDescriptorResolveResult resolve(@NotNull OfflineProblemDescriptor descriptor,
                                                @NotNull InspectionToolWrapper wrapper,
                                                @NotNull InspectionToolPresentation presentation) {
    final RefEntity element = descriptor.getRefElement(presentation.getContext().getRefManager());
    final CommonProblemDescriptor resolvedDescriptor =
      ReadAction.compute(() -> createDescriptor(element, descriptor, wrapper, presentation));
    return new OfflineDescriptorResolveResult(element, resolvedDescriptor);
  }


  @Nullable
  private static CommonProblemDescriptor createDescriptor(@Nullable RefEntity element,
                                                          @NotNull OfflineProblemDescriptor offlineDescriptor,
                                                          @NotNull InspectionToolWrapper toolWrapper,
                                                          @NotNull InspectionToolPresentation presentation) {
    if (toolWrapper instanceof GlobalInspectionToolWrapper) {
      final LocalInspectionToolWrapper localTool = ((GlobalInspectionToolWrapper)toolWrapper).getSharedLocalInspectionToolWrapper();
      if (localTool != null) {
        final CommonProblemDescriptor descriptor = createDescriptor(element, offlineDescriptor, localTool, presentation);
        if (descriptor != null) {
          return descriptor;
        }
      }
      return createRerunGlobalToolDescriptor((GlobalInspectionToolWrapper)toolWrapper, element, offlineDescriptor);
    }
    if (!(toolWrapper instanceof LocalInspectionToolWrapper)) return null;
    final InspectionManager inspectionManager = InspectionManager.getInstance(presentation.getContext().getProject());
    final OfflineProblemDescriptor offlineProblemDescriptor = offlineDescriptor;
    if (element instanceof RefElement) {
      final PsiElement psiElement = ((RefElement)element).getElement();
      if (psiElement != null) {
        ProblemDescriptor descriptor = ProgressManager.getInstance().runProcess(
          () -> runLocalTool(psiElement,
                             offlineProblemDescriptor,
                             (LocalInspectionToolWrapper)toolWrapper,
                             inspectionManager,
                             presentation.getContext()), new DaemonProgressIndicator());
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
                                                @NotNull OfflineProblemDescriptor offlineProblemDescriptor,
                                                @NotNull LocalInspectionToolWrapper toolWrapper,
                                                @NotNull InspectionManager inspectionManager,
                                                @NotNull GlobalInspectionContextImpl context) {
    PsiFile containingFile = psiElement.getContainingFile();
    final ProblemsHolder holder = new ProblemsHolder(inspectionManager, containingFile, false);
    final LocalInspectionTool localTool = toolWrapper.getTool();
    TextRange textRange = psiElement.getTextRange();
    LOG.assertTrue(textRange != null,
                   "text range muse be not null here; " +
                   "isValid = " + psiElement.isValid() + ", " +
                   "isPhysical = " + psiElement.isPhysical() + ", " +
                   "containingFile = " + containingFile.getName() + ", " +
                   "inspection = " + toolWrapper.getShortName());
    final int startOffset = textRange.getStartOffset();
    final int endOffset = textRange.getEndOffset();
    LocalInspectionToolSession session = new LocalInspectionToolSession(containingFile, startOffset, endOffset);
    final PsiElementVisitor visitor = localTool.buildVisitor(holder, true, session);
    localTool.inspectionStarted(session, false);
    final PsiElement[] elementsInRange = getElementsIntersectingRange(containingFile, startOffset, endOffset);
    InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(context.getProject());
    for (PsiElement element : elementsInRange) {
      List<Pair<PsiElement, TextRange>> injectedPsiFiles = injectedLanguageManager.getInjectedPsiFiles(element);
      if (injectedPsiFiles != null) {
        for (Pair<PsiElement, TextRange> file : injectedPsiFiles) {
          file.getFirst().accept(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(PsiElement element) {
              element.accept(visitor);
              super.visitElement(element);
            }
          });
        }
      }
      element.accept(visitor);
    }
    localTool.inspectionFinished(session, holder);
    if (holder.hasResults()) {
      final List<ProblemDescriptor> list = holder.getResults();
      final int idx = offlineProblemDescriptor.getProblemIndex();
      int curIdx = 0;
      for (ProblemDescriptor descriptor : list) {
        final PsiNamedElement member = LocalDescriptorsUtil.getContainerElement(descriptor.getPsiElement(), localTool, context);
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

  @NotNull
  private static PsiElement[] getElementsIntersectingRange(PsiFile file, final int startOffset, final int endOffset) {
    final FileViewProvider viewProvider = file.getViewProvider();
    final Set<PsiElement> result = new LinkedHashSet<>();
    for (Language language : viewProvider.getLanguages()) {
      final PsiFile psiRoot = viewProvider.getPsi(language);
      if (HighlightingLevelManager.getInstance(file.getProject()).shouldInspect(psiRoot)) {
        result.addAll(CollectHighlightsUtil.getElementsInRange(psiRoot, startOffset, endOffset, true));
      }
    }
    return PsiUtilCore.toPsiElementArray(result);
  }

  @Nullable
  private static LocalQuickFix[] getFixes(@NotNull CommonProblemDescriptor descriptor, List<String> hints, InspectionToolPresentation presentation) {
    final List<LocalQuickFix> fixes = new ArrayList<>(hints == null ? 1 : hints.size());
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

  private static CommonProblemDescriptor createRerunGlobalToolDescriptor(@NotNull GlobalInspectionToolWrapper wrapper,
                                                                         @Nullable RefEntity entity,
                                                                         OfflineProblemDescriptor offlineDescriptor) {
    

    QuickFix rerunFix = new QuickFix() {
      @Nls
      @NotNull
      @Override
      public String getFamilyName() {
        return "Rerun \'" + wrapper.getDisplayName() + "\' inspection";
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull CommonProblemDescriptor descriptor) {
        VirtualFile file = null;
        if (entity != null && entity.isValid() && entity instanceof RefElement) {
          file = ((RefElement)entity).getPointer().getVirtualFile();
        }
        PsiFile psiFile = null;
        if (file != null) {
          psiFile = PsiManager.getInstance(project).findFile(file);
        }
        RunInspectionAction.runInspection(project, wrapper.getShortName(), file, null, psiFile);
      }

      @Override
      public boolean startInWriteAction() {
        return false;
      }
    };
    List<String> hints = offlineDescriptor.getHints();
    if (hints != null && entity instanceof RefModule) {
      List<QuickFix> fixes =
        hints.stream().map(hint -> wrapper.getTool().getQuickFix(hint)).filter(f -> f != null).collect(Collectors.toList());
      return new ModuleProblemDescriptorImpl(ArrayUtil.append(fixes.toArray(QuickFix.EMPTY_ARRAY), rerunFix), offlineDescriptor.getDescription(), ((RefModule)entity).getModule());
    }
    return new CommonProblemDescriptorImpl(new QuickFix[]{rerunFix}, offlineDescriptor.getDescription());
  }
}
