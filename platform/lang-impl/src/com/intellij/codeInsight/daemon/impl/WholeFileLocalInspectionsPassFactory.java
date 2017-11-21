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
import com.intellij.openapi.components.AbstractProjectComponent;
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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * @author cdr
*/
public class WholeFileLocalInspectionsPassFactory extends AbstractProjectComponent implements TextEditorHighlightingPassFactory {
  private final Set<PsiFile> mySkipWholeInspectionsCache = ContainerUtil.createWeakSet(); // guarded by mySkipWholeInspectionsCache
  private final ObjectIntMap<PsiFile> myPsiModificationCount = ContainerUtil.createWeakKeyIntValueMap(); // guarded by myPsiModificationCount

  public WholeFileLocalInspectionsPassFactory(Project project, TextEditorHighlightingPassRegistrar highlightingPassRegistrar) {
    super(project);
    // can run in the same time with LIP, but should start after it, since I believe whole-file inspections would run longer
    highlightingPassRegistrar.registerTextEditorHighlightingPass(this, null, new int[]{Pass.LOCAL_INSPECTIONS}, true, Pass.WHOLE_FILE_LOCAL_INSPECTIONS);
  }

  @Override
  @NonNls
  @NotNull
  public String getComponentName() {
    return "WholeFileLocalInspectionsPassFactory";
  }

  @Override
  public void projectOpened() {
    ProjectInspectionProfileManager profileManager = ProjectInspectionProfileManager.getInstance(myProject);
    profileManager.addProfileChangeListener(new ProfileChangeAdapter() {
      @Override
      public void profileChanged(InspectionProfile profile) {
        clearSkipCache();
      }

      @Override
      public void profileActivated(InspectionProfile oldProfile, @Nullable InspectionProfile profile) {
        clearSkipCache();
      }
    }, myProject);
    Disposer.register(myProject, this::clearSkipCache);
  }

  private void clearSkipCache() {
    synchronized (mySkipWholeInspectionsCache) {
      mySkipWholeInspectionsCache.clear();
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

      @Override
      void inspectInjectedPsi(@NotNull List<PsiElement> elements,
                              boolean onTheFly,
                              @NotNull ProgressIndicator indicator,
                              @NotNull InspectionManager iManager,
                              boolean inVisibleRange,
                              @NotNull List<LocalInspectionToolWrapper> wrappers) {
        // already inspected in LIP
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
