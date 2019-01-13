// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.ProblemHighlightFilter;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.InspectionProfileWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.profile.ProfileChangeAdapter;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ObjectIntMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class WholeFileLocalInspectionsPassFactory implements TextEditorHighlightingPassFactory, ProjectComponent {
  private final Set<PsiFile> mySkipWholeInspectionsCache = ContainerUtil.createWeakSet(); // guarded by mySkipWholeInspectionsCache
  private final ObjectIntMap<PsiFile> myPsiModificationCount = ContainerUtil.createWeakKeyIntValueMap(); // guarded by myPsiModificationCount
  private final Project myProject;

  public WholeFileLocalInspectionsPassFactory(Project project, TextEditorHighlightingPassRegistrar highlightingPassRegistrar) {
    myProject = project;
    // can run in the same time with LIP, but should start after it, since I believe whole-file inspections would run longer
    highlightingPassRegistrar.registerTextEditorHighlightingPass(this, null, new int[]{Pass.LOCAL_INSPECTIONS}, true, Pass.WHOLE_FILE_LOCAL_INSPECTIONS);
  }

  @Override
  public void projectOpened() {
    ProjectInspectionProfileManager profileManager = ProjectInspectionProfileManager.getInstance(myProject);
    profileManager.addProfileChangeListener(new ProfileChangeAdapter() {
      @Override
      public void profileChanged(InspectionProfile profile) {
        clearCaches();
      }

      @Override
      public void profileActivated(InspectionProfile oldProfile, @Nullable InspectionProfile profile) {
        clearCaches();
      }
    }, myProject);
    Disposer.register(myProject, this::clearCaches);
  }

  private void clearCaches() {
    synchronized (mySkipWholeInspectionsCache) {
      mySkipWholeInspectionsCache.clear();
    }
    synchronized (myPsiModificationCount) {
      myPsiModificationCount.clear();
    }
  }

  @Override
  @Nullable
  public TextEditorHighlightingPass createHighlightingPass(@NotNull final PsiFile file, @NotNull final Editor editor) {
    long actualCount = PsiManager.getInstance(myProject).getModificationTracker().getModificationCount();
    synchronized (myPsiModificationCount) {
      if (myPsiModificationCount.get(file) == (int)actualCount) {
        return null; //optimization
      }
    }

    if (!ProblemHighlightFilter.shouldHighlightFile(file)) {
      return null;
     }

    synchronized (mySkipWholeInspectionsCache) {
      if (mySkipWholeInspectionsCache.contains(file)) {
        return null;
      }
    }
    ProperTextRange visibleRange = VisibleHighlightingPassFactory.calculateVisibleRange(editor);
    return new LocalInspectionsPass(file, editor.getDocument(), 0, file.getTextLength(), visibleRange, true,
                                    new DefaultHighlightInfoProcessor()) {
      @NotNull
      @Override
      List<LocalInspectionToolWrapper> getInspectionTools(@NotNull InspectionProfileWrapper profile) {
        List<LocalInspectionToolWrapper> tools = super.getInspectionTools(profile);
        List<LocalInspectionToolWrapper> result = ContainerUtil.filter(tools, LocalInspectionToolWrapper::runForWholeFile);
        if (result.isEmpty()) {
          synchronized (mySkipWholeInspectionsCache) {
            mySkipWholeInspectionsCache.add(file);
          }
        }
        return result;
      }

      @Override
      protected String getPresentableName() {
        return DaemonBundle.message("pass.whole.inspections");
      }

      @NotNull
      @Override
      Set<PsiFile> inspectInjectedPsi(@NotNull List<? extends PsiElement> elements,
                              boolean onTheFly,
                              @NotNull ProgressIndicator indicator,
                              @NotNull InspectionManager iManager,
                              boolean inVisibleRange,
                              @NotNull List<? extends LocalInspectionToolWrapper> wrappers, @NotNull Set<? extends PsiFile> alreadyVisitedInjected) {
        // already inspected in LIP
        return Collections.emptySet();
      }

      @Override
      protected void applyInformationWithProgress() {
        super.applyInformationWithProgress();
        long modificationCount = PsiManager.getInstance(myProject).getModificationTracker().getModificationCount();
        synchronized (myPsiModificationCount) {
          myPsiModificationCount.put(file, (int)modificationCount);
        }
      }
    };
  }
}
