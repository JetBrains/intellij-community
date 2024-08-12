// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.offlineViewer;

import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil;
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.actions.RunInspectionAction;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.offline.OfflineProblemDescriptor;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefModule;
import com.intellij.codeInspection.ui.InspectionToolPresentation;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.ProblemGroup;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ArrayUtil;
import com.intellij.util.DocumentUtil;
import com.intellij.util.PairProcessor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Dmitry Batkovich
 */
public final class OfflineDescriptorResolveResult {
  private static final Logger LOG = Logger.getInstance(OfflineDescriptorResolveResult.class);
  private final RefEntity myResolvedEntity;
  private final CommonProblemDescriptor myResolvedDescriptor;
  private volatile boolean myExcluded;

  private OfflineDescriptorResolveResult(@Nullable RefEntity resolvedEntity, @Nullable CommonProblemDescriptor resolvedDescriptor) {
    myResolvedEntity = resolvedEntity;
    myResolvedDescriptor = resolvedDescriptor;
  }

  @Nullable
  RefEntity getResolvedEntity() {
    return myResolvedEntity;
  }

  @Nullable
  CommonProblemDescriptor getResolvedDescriptor() {
    return myResolvedDescriptor;
  }

  public boolean isExcluded() {
    return myExcluded;
  }

  public void setExcluded(boolean excluded) {
    myExcluded = excluded;
  }

  static @NotNull OfflineDescriptorResolveResult resolve(@NotNull OfflineProblemDescriptor descriptor,
                                                         @NotNull InspectionToolWrapper<?,?> wrapper,
                                                         @NotNull InspectionToolPresentation presentation) {
    RefEntity element = descriptor.getRefElement(presentation.getContext().getRefManager());
    CommonProblemDescriptor resolvedDescriptor =
      ReadAction.compute(() -> createDescriptor(element, descriptor, wrapper, presentation));
    return new OfflineDescriptorResolveResult(element, resolvedDescriptor);
  }


  private static @Nullable CommonProblemDescriptor createDescriptor(@Nullable RefEntity element,
                                                                    @NotNull OfflineProblemDescriptor offlineDescriptor,
                                                                    @NotNull InspectionToolWrapper<?,?> toolWrapper,
                                                                    @NotNull InspectionToolPresentation presentation) {
    Project project = presentation.getContext().getProject();
    if (toolWrapper instanceof GlobalInspectionToolWrapper) {
      LocalInspectionToolWrapper localTool = ((GlobalInspectionToolWrapper)toolWrapper).getSharedLocalInspectionToolWrapper();
      if (localTool != null) {
        CommonProblemDescriptor descriptor = createDescriptor(element, offlineDescriptor, localTool, presentation);
        if (descriptor != null) {
          return descriptor;
        }
      }
      return createRerunGlobalToolDescriptor((GlobalInspectionToolWrapper)toolWrapper, element, offlineDescriptor, project);
    }
    if (Registry.is("offline.inspections.results.verify") &&
        toolWrapper instanceof LocalInspectionToolWrapper && !(toolWrapper.getTool() instanceof UnfairLocalInspectionTool)) {
      if (element instanceof RefElement) {
        PsiElement psiElement = ((RefElement)element).getPsiElement();
        if (psiElement != null) {
          return ProgressManager.getInstance().runProcess(
            () -> runLocalTool(psiElement,
                               offlineDescriptor,
                               (LocalInspectionToolWrapper)toolWrapper,
                               presentation.getContext()), new DaemonProgressIndicator());
        }
        return null;
      }
    }

    CommonProblemDescriptor descriptor = createProblemDescriptorFromOfflineDescriptor(element,
                                                                                      offlineDescriptor,
                                                                                      QuickFix.EMPTY_ARRAY,
                                                                                      project);
    QuickFix<?>[] quickFixes = getFixes(descriptor, element, presentation, offlineDescriptor.getHints());
    if (quickFixes != null) {
      descriptor = createProblemDescriptorFromOfflineDescriptor(element,
                                                                offlineDescriptor,
                                                                quickFixes,
                                                                project);
    }
    return descriptor;
  }

  private static @NotNull CommonProblemDescriptor createProblemDescriptorFromOfflineDescriptor(@Nullable RefEntity element,
                                                                                               @NotNull OfflineProblemDescriptor offlineDescriptor,
                                                                                               @NotNull QuickFix<?> @NotNull [] fixes,
                                                                                               @NotNull Project project) {
    InspectionManager inspectionManager = InspectionManager.getInstance(project);
    if (element instanceof RefElement refElement) {
      if(refElement.getPsiElement() instanceof PsiFile) {
        PsiElement targetElement = findTargetElementFromOfflineDescriptor((PsiFile)refElement.getPsiElement(), offlineDescriptor, project);
        if(targetElement != null) {
          return inspectionManager.createProblemDescriptor(targetElement, offlineDescriptor.getDescription(), false,
                                                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false);
        }
      }
      return new ProblemDescriptorBackedByRefElement(refElement, offlineDescriptor, fixes);
    }
    else if (element instanceof RefModule) {
      return inspectionManager.createProblemDescriptor(offlineDescriptor.getDescription(), ((RefModule)element).getModule(), fixes);
    }
    else {
      return inspectionManager.createProblemDescriptor(offlineDescriptor.getDescription(), 
                                                       ContainerUtil.filter(fixes, f -> !(f instanceof LocalQuickFix)).toArray(QuickFix.EMPTY_ARRAY));
    }
  }

  private static @Nullable PsiElement findTargetElementFromOfflineDescriptor(@NotNull PsiFile file, @NotNull OfflineProblemDescriptor descriptor,
                                                                             @NotNull Project project) {
    if(descriptor.getLine() - 1 <= 0 && descriptor.getOffset() <= 0)
      return null;
    Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if(document == null)
      return null;
    int lineStartOffset = document.getLineStartOffset(descriptor.getLine() - 1);
    if(!DocumentUtil.isValidOffset(lineStartOffset, document) || !DocumentUtil.isValidOffset(lineStartOffset + descriptor.getOffset(), document))
      return null;
    return file.findElementAt(lineStartOffset + descriptor.getOffset());
  }

  private static ProblemDescriptor runLocalTool(@NotNull PsiElement psiElement,
                                                @NotNull OfflineProblemDescriptor offlineProblemDescriptor,
                                                @NotNull LocalInspectionToolWrapper toolWrapper,
                                                @NotNull GlobalInspectionContextImpl context) {
    PsiFile containingFile = psiElement.getContainingFile();
    LocalInspectionTool localTool = toolWrapper.getTool();
    TextRange textRange = psiElement.getTextRange();
    LOG.assertTrue(textRange != null,
                   "text range must be not null here; " +
                   "isValid = " + psiElement.isValid() + ", " +
                   "isPhysical = " + psiElement.isPhysical() + ", " +
                   "containingFile = " + containingFile.getName() + ", " +
                   "inspection = " + toolWrapper.getShortName());
    PsiElement[] elementsInRange = getElementsIntersectingRange(containingFile, textRange.getStartOffset(), textRange.getEndOffset());
    Collection<PsiFile> injectedFiles = new HashSet<>();
    InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(context.getProject());
    for (PsiElement element : elementsInRange) {
      List<Pair<PsiElement, TextRange>> injectedPsiFiles = injectedLanguageManager.getInjectedPsiFiles(element);
      if (injectedPsiFiles != null) {
        for (Pair<PsiElement, TextRange> pair : injectedPsiFiles) {
          injectedFiles.add(pair.getFirst().getContainingFile());
        }
      }
    }
    Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> map =
      InspectionEngine.inspectEx(Collections.singletonList(toolWrapper), containingFile, textRange, containingFile.getTextRange(), true,
                                 false, true, new DaemonProgressIndicator(), PairProcessor.alwaysTrue());
    List<ProblemDescriptor> list = new ArrayList<>();
    map.values().forEach(problemsList -> list.addAll(problemsList));
    for (PsiFile injectedFile : injectedFiles) {
      Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> injectedMap =
        InspectionEngine.inspectEx(Collections.singletonList(toolWrapper), injectedFile, injectedFile.getTextRange(),
                                   injectedFile.getTextRange(), true,
                                   false, true, new DaemonProgressIndicator(), PairProcessor.alwaysTrue());
      list.addAll(ContainerUtil.flatten(injectedMap.values()));
    }

    int idx = offlineProblemDescriptor.getProblemIndex();
    int curIdx = 0;
    for (ProblemDescriptor descriptor : list) {
      PsiNamedElement member = BatchModeDescriptorsUtil.getContainerElement(descriptor.getPsiElement(), localTool, context);
      PsiElement element = psiElement instanceof LightElement ? psiElement.getNavigationElement() : psiElement;
      if (psiElement instanceof PsiFile || element.equals(member)) {
        if (curIdx == idx) {
          return descriptor;
        }
        curIdx++;
      }
    }

    return null;
  }

  private static PsiElement @NotNull [] getElementsIntersectingRange(@NotNull PsiFile file, int startOffset, int endOffset) {
    FileViewProvider viewProvider = file.getViewProvider();
    Set<PsiElement> result = new LinkedHashSet<>();
    for (Language language : viewProvider.getLanguages()) {
      PsiFile psiRoot = viewProvider.getPsi(language);
      if (HighlightingLevelManager.getInstance(file.getProject()).shouldInspect(psiRoot)) {
        result.addAll(CollectHighlightsUtil.getElementsInRange(psiRoot, startOffset, endOffset, true));
      }
    }
    return PsiUtilCore.toPsiElementArray(result);
  }

  private static @NotNull QuickFix<?> @Nullable [] getFixes(@NotNull CommonProblemDescriptor descriptor,
                                                            @Nullable RefEntity entity,
                                                            @NotNull InspectionToolPresentation presentation,
                                                            @Nullable List<String> hints) {
    List<QuickFix<?>> fixes = new ArrayList<>(hints == null ? 1 : hints.size());
    if (hints == null) {
      addFix(descriptor, entity, fixes, null, presentation);
    }
    else {
      for (String hint : hints) {
        addFix(descriptor, entity, fixes, hint, presentation);
      }
    }
    return fixes.isEmpty() ? null : fixes.toArray(QuickFix.EMPTY_ARRAY);
  }

  private static void addFix(@NotNull CommonProblemDescriptor descriptor,
                             @Nullable RefEntity entity,
                             @NotNull List<? super QuickFix<?>> fixes,
                             @Nullable String hint,
                             @NotNull InspectionToolPresentation presentation) {
    ContainerUtil.addAllNotNull(fixes, presentation.findQuickFixes(descriptor, entity, hint));
  }

  private static @NotNull CommonProblemDescriptor createRerunGlobalToolDescriptor(@NotNull GlobalInspectionToolWrapper wrapper,
                                                                                  @Nullable RefEntity entity,
                                                                                  @NotNull OfflineProblemDescriptor offlineDescriptor,
                                                                                  @NotNull Project project) {
    QuickFix<?> rerunFix = new QuickFix<>() {
      @Override
      public @Nls @NotNull String getFamilyName() {
        return InspectionsBundle.message("rerun.inspection.family.name", wrapper.getDisplayName());
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
      List<QuickFix> fixes = ContainerUtil.mapNotNull(hints, hint -> wrapper.getTool().getQuickFix(hint));
      return InspectionManager.getInstance(project).createProblemDescriptor(offlineDescriptor.getDescription(), ((RefModule)entity).getModule(), ArrayUtil.append(fixes.toArray(QuickFix.EMPTY_ARRAY), rerunFix));
    }
    return InspectionManager.getInstance(project).createProblemDescriptor(offlineDescriptor.getDescription(), rerunFix);
  }

  private static final class ProblemDescriptorBackedByRefElement implements ProblemDescriptor {
    private final RefElement myElement;
    private final OfflineProblemDescriptor myOfflineProblemDescriptor;
    private final QuickFix<?>[] myFixes;

    private ProblemDescriptorBackedByRefElement(@NotNull RefElement element,
                                                @NotNull OfflineProblemDescriptor descriptor,
                                                @NotNull QuickFix<?> @NotNull [] fixes) {
      myElement = element;
      myOfflineProblemDescriptor = descriptor;
      myFixes = fixes;
    }

    @Override
    public PsiElement getPsiElement() {
      return myElement.getPsiElement();
    }

    @Override
    public PsiElement getStartElement() {
      return getPsiElement();
    }

    @Override
    public PsiElement getEndElement() {
      return getPsiElement();
    }

    @Override
    public TextRange getTextRangeInElement() {
      return null;
    }

    @Override
    public int getLineNumber() {
      return 0;
    }

    @Override
    public @NotNull ProblemHighlightType getHighlightType() {
      return ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
    }

    @Override
    public boolean isAfterEndOfLine() {
      return false;
    }

    @Override
    public void setTextAttributes(TextAttributesKey key) {

    }

    @Override
    public @Nullable ProblemGroup getProblemGroup() {
      return null;
    }

    @Override
    public void setProblemGroup(@Nullable ProblemGroup problemGroup) {

    }

    @Override
    public boolean showTooltip() {
      return false;
    }

    @Override
    public @NotNull String getDescriptionTemplate() {
      return myOfflineProblemDescriptor.getDescription();
    }

    @Override
    public @NotNull QuickFix @Nullable [] getFixes() {
      return myFixes;
    }
  }
}
