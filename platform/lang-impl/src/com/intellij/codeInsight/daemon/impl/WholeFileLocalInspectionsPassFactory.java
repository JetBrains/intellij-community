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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ex.InspectionProfileWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.profile.Profile;
import com.intellij.profile.ProfileChangeAdapter;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author cdr
*/
public class WholeFileLocalInspectionsPassFactory extends AbstractProjectComponent implements TextEditorHighlightingPassFactory {
  private final Map<PsiFile, Boolean> myFileTools = ContainerUtil.createConcurrentWeakMap();
  public InspectionProjectProfileManager myProfileManager;

  public WholeFileLocalInspectionsPassFactory(Project project, TextEditorHighlightingPassRegistrar highlightingPassRegistrar,
                                              final InspectionProjectProfileManager profileManager) {
    super(project);
    // can run in the same time with LIP, but should start after it, since I believe whole-file inspections would run longer
    highlightingPassRegistrar.registerTextEditorHighlightingPass(this, null, new int[]{Pass.LOCAL_INSPECTIONS}, true, Pass.WHOLE_FILE_LOCAL_INSPECTIONS);
    myProfileManager = profileManager;
  }

  @Override
  @NonNls
  @NotNull
  public String getComponentName() {
    return "WholeFileLocalInspectionsPassFactory";
  }

  @Override
  public void projectOpened() {
    final ProfileChangeAdapter myProfilesListener = new ProfileChangeAdapter() {
      @Override
      public void profileChanged(Profile profile) {
        myFileTools.clear();
      }

      @Override
      public void profileActivated(Profile oldProfile, Profile profile) {
        myFileTools.clear();
      }
    };
    myProfileManager.addProfilesListener(myProfilesListener, myProject);
    Disposer.register(myProject, new Disposable() {
      @Override
      public void dispose() {
        myFileTools.clear();
      }
    });
  }

  @Override
  @Nullable
  public TextEditorHighlightingPass createHighlightingPass(@NotNull final PsiFile file, @NotNull final Editor editor) {
    TextRange textRange = FileStatusMap.getDirtyTextRange(editor, Pass.LOCAL_INSPECTIONS);
    if (textRange == null ||
        !InspectionProjectProfileManager.getInstance(file.getProject()).isProfileLoaded() ||
        myFileTools.containsKey(file) && !myFileTools.get(file)) {
      return null;
    }

    return new LocalInspectionsPass(file, editor.getDocument(), 0, file.getTextLength(), LocalInspectionsPass.EMPTY_PRIORITY_RANGE, true,
                                    new DefaultHighlightInfoProcessor()) {
      @NotNull
      @Override
      List<LocalInspectionToolWrapper> getInspectionTools(@NotNull InspectionProfileWrapper profile) {
        List<LocalInspectionToolWrapper> tools = super.getInspectionTools(profile);
        List<LocalInspectionToolWrapper> result = new ArrayList<LocalInspectionToolWrapper>(tools.size());
        for (LocalInspectionToolWrapper tool : tools) {
          if (tool.runForWholeFile()) result.add(tool);
        }
        myFileTools.put(file, !result.isEmpty());
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
    };
  }

}
