// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectType;
import com.intellij.openapi.project.ProjectTypeService;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PairProcessor;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

final class DoNotShowInspectionIntentionMenuContributor implements IntentionMenuContributor {
  private static final Logger LOG = Logger.getInstance(DoNotShowInspectionIntentionMenuContributor.class);

  @Override
  public void collectActions(@NotNull Editor hostEditor,
                             @NotNull PsiFile hostFile,
                             @NotNull ShowIntentionsPass.IntentionsInfo intentions,
                             int passIdToShowIntentionsFor,
                             int offset) {
    Project project = hostFile.getProject();
    PsiElement psiElement = hostFile.findElementAt(offset);
    if (HighlightingLevelManager.getInstance(project).shouldInspect(hostFile)) {
      PsiElement intentionElement = psiElement;
      int intentionOffset = offset;
      if (psiElement instanceof PsiWhiteSpace && offset == psiElement.getTextRange().getStartOffset() && offset > 0) {
        PsiElement prev = hostFile.findElementAt(offset - 1);
        if (prev != null && prev.isValid()) {
          intentionElement = prev;
          intentionOffset = offset - 1;
        }
      }
      if (intentionElement != null &&
          (intentionElement.getManager().isInProject(intentionElement) || ScratchUtil.isScratch(hostFile.getVirtualFile()))) {
        collectIntentionsFromDoNotShowLeveledInspections(project, hostFile, intentionElement, intentionOffset, intentions);
      }
    }
  }

  /**
   * Can be invoked in EDT, each inspection should be fast
   */
  private static void collectIntentionsFromDoNotShowLeveledInspections(@NotNull Project project,
                                                                       @NotNull PsiFile hostFile,
                                                                       @NotNull PsiElement psiElement,
                                                                       int offset,
                                                                       @NotNull ShowIntentionsPass.IntentionsInfo outIntentions) {
    if (!psiElement.isPhysical() && !PlatformUtils.isFleetBackend()) {
      VirtualFile virtualFile = hostFile.getVirtualFile();
      String text = hostFile.getText();
      LOG.error("not physical: '" + psiElement.getText() + "' @" + offset + " " +psiElement.getTextRange() +
                " elem:" + psiElement + " (" + psiElement.getClass().getName() + ")" +
                " in:" + psiElement.getContainingFile() + " host:" + hostFile + "(" + hostFile.getClass().getName() + ")",
                new Attachment(virtualFile != null ? virtualFile.getPresentableUrl() : "null", text != null ? text : "null"));
    }
    if (DumbService.isDumb(project) || LightEdit.owns(project)) {
      return;
    }

    Collection<ProjectType> projectTypes = ProjectTypeService.getProjectTypes(project);

    List<LocalInspectionToolWrapper> intentionTools = new ArrayList<>();
    InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
    for (InspectionToolWrapper<?,?> toolWrapper : profile.getInspectionTools(hostFile)) {
      if (!toolWrapper.isApplicable(projectTypes)) continue;

      if (toolWrapper instanceof GlobalInspectionToolWrapper) {
        toolWrapper = ((GlobalInspectionToolWrapper)toolWrapper).getSharedLocalInspectionToolWrapper();
      }
      if (toolWrapper instanceof LocalInspectionToolWrapper && !((LocalInspectionToolWrapper)toolWrapper).isUnfair()) {
        HighlightDisplayKey key = HighlightDisplayKey.find(toolWrapper.getShortName());
        if (profile.isToolEnabled(key, hostFile) &&
            HighlightDisplayLevel.DO_NOT_SHOW.equals(profile.getErrorLevel(key, hostFile))) {
          intentionTools.add((LocalInspectionToolWrapper)toolWrapper);
        }
      }
    }

    if (intentionTools.isEmpty()) {
      return;
    }

    List<PsiElement> elements = PsiTreeUtil.collectParents(psiElement, PsiElement.class, true, e -> e instanceof PsiDirectory);
    PsiElement elementToTheLeft = psiElement.getContainingFile().findElementAt(offset - 1);
    @Unmodifiable List<PsiElement> toInspect;
    if (elementToTheLeft != psiElement && elementToTheLeft != null) {
      List<PsiElement> parentsOnTheLeft =
        PsiTreeUtil.collectParents(elementToTheLeft, PsiElement.class, true, e -> e instanceof PsiDirectory || elements.contains(e));
      toInspect = ContainerUtil.concat(elements, parentsOnTheLeft);
    }
    else {
      toInspect = elements;
    }

    Map<@NonNls String, @Nls(capitalization = Nls.Capitalization.Sentence) String> displayNames =
      ContainerUtil.map2Map(intentionTools, wrapper -> Pair.create(wrapper.getShortName(), wrapper.getDisplayName()));

    // indicator can be null when run from EDT
    ProgressIndicator progress = ObjectUtils.notNull(ProgressIndicatorProvider.getGlobalProgressIndicator(), new DaemonProgressIndicator());
    Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> map =
      InspectionEngine.inspectElements(intentionTools, hostFile, hostFile.getTextRange(), true, true, progress, toInspect, PairProcessor.alwaysTrue());

    for (Map.Entry<LocalInspectionToolWrapper, List<ProblemDescriptor>> entry : map.entrySet()) {
      List<ProblemDescriptor> descriptors = entry.getValue();
      String shortName = entry.getKey().getShortName();
      for (ProblemDescriptor problemDescriptor : descriptors) {
        if (problemDescriptor instanceof ProblemDescriptorBase) {
          TextRange range = ((ProblemDescriptorBase)problemDescriptor).getTextRange();
          if (range != null && range.containsOffset(offset)) {
            QuickFix[] fixes = problemDescriptor.getFixes();
            for (int k = 0; k < fixes.length; k++) {
              IntentionAction intentionAction = QuickFixWrapper.wrap(problemDescriptor, k);
              HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
              String displayName = displayNames.get(shortName);
              HighlightInfo.IntentionActionDescriptor actionDescriptor =
                new HighlightInfo.IntentionActionDescriptor(intentionAction, null, displayName, null,
                                                            key, null, HighlightSeverity.INFORMATION);
              (problemDescriptor.getHighlightType() == ProblemHighlightType.ERROR
               ? outIntentions.errorFixesToShow
               : outIntentions.intentionsToShow).add(actionDescriptor);
            }
          }
        }
      }
    }
  }
}
