/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.slicer;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisUIOptions;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.refactoring.util.RefactoringDescriptionLocation;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

@State(
    name = "SliceManager",
    storages = {@Storage(id = "other", file = "$WORKSPACE_FILE$")}
)
public class SliceManager implements PersistentStateComponent<SliceManager.Bean> {
  private final Project myProject;
  private final ContentManager myBackContentManager;
  private final ContentManager myForthContentManager;
  private volatile boolean myCanceled;
  private final Bean myStoredSettings = new Bean();
  private static final String BACK_TOOLWINDOW_ID = "Analyze Dataflow to";
  private static final String FORTH_TOOLWINDOW_ID = "Analyze Dataflow from";

  public static class Bean {
    //public boolean includeTestSources = true; // to show in dialog
    public AnalysisUIOptions analysisUIOptions = new AnalysisUIOptions();
  }

  public static SliceManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, SliceManager.class);
  }

  public SliceManager(@NotNull Project project, @NotNull ToolWindowManager toolWindowManager, final PsiManager psiManager) {
    myProject = project;
    ToolWindow toolWindow = toolWindowManager.registerToolWindow(BACK_TOOLWINDOW_ID, true, ToolWindowAnchor.BOTTOM, project);
    myBackContentManager = toolWindow.getContentManager();
    new ContentManagerWatcher(toolWindow, myBackContentManager);

    ToolWindow ftoolWindow = toolWindowManager.registerToolWindow(FORTH_TOOLWINDOW_ID, true, ToolWindowAnchor.BOTTOM, project);
    myForthContentManager = ftoolWindow.getContentManager();
    new ContentManagerWatcher(ftoolWindow, myForthContentManager);

    psiManager.addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
      @Override
      public void beforeChildAddition(PsiTreeChangeEvent event) {
        cancel();
      }

      @Override
      public void beforeChildRemoval(PsiTreeChangeEvent event) {
        cancel();
      }

      @Override
      public void beforeChildReplacement(PsiTreeChangeEvent event) {
        cancel();
      }

      @Override
      public void beforeChildMovement(PsiTreeChangeEvent event) {
        cancel();
      }

      @Override
      public void beforeChildrenChange(PsiTreeChangeEvent event) {
        cancel();
      }

      @Override
      public void beforePropertyChange(PsiTreeChangeEvent event) {
        cancel();
      }
    }, project);
  }

  private void cancel() {
    myCanceled = true;
  }

  public void slice(@NotNull PsiElement element, boolean dataFlowToThis) {
    doSlice(element, dataFlowToThis);
  }

  private void doSlice(@NotNull PsiElement element, boolean dataFlowToThis) {
    Module module = ModuleUtil.findModuleForPsiElement(element);
    AnalysisUIOptions analysisUIOptions = new AnalysisUIOptions();
    analysisUIOptions.save(myStoredSettings.analysisUIOptions);
    AnalysisScope analysisScope = new AnalysisScope(element.getContainingFile());
    String name = module == null ? null : module.getName();
    String dialogTitle = getElementDescription((dataFlowToThis ? BACK_TOOLWINDOW_ID : FORTH_TOOLWINDOW_ID) + " ", element, null);

    dialogTitle = Pattern.compile("<[^<>]*>").matcher(dialogTitle).replaceAll("");
    
    BaseAnalysisActionDialog dialog = new BaseAnalysisActionDialog(dialogTitle, "Analyze scope", myProject, analysisScope, name, true, analysisUIOptions, element);
    dialog.show();
    if (!dialog.isOK()) return;

    AnalysisScope scope = dialog.getScope(analysisUIOptions, analysisScope, myProject, module);
    myStoredSettings.analysisUIOptions.save(analysisUIOptions);

    SliceRootNode rootNode = new SliceRootNode(myProject, new DuplicateMap(), scope, createRootUsage(element, scope), dataFlowToThis);
    createToolWindow(dataFlowToThis, rootNode, false, getElementDescription(null, element, null));
  }

  public void createToolWindow(final boolean dataFlowToThis, final SliceRootNode rootNode, boolean splitByLeafExpressions, String displayName) {
    final SliceToolwindowSettings sliceToolwindowSettings = SliceToolwindowSettings.getInstance(myProject);
    final ContentManager contentManager = dataFlowToThis ? myBackContentManager : myForthContentManager;
    final Content[] myContent = new Content[1];
    final SlicePanel slicePanel = new SlicePanel(myProject, dataFlowToThis, rootNode, splitByLeafExpressions) {
      protected void close() {
        contentManager.removeContent(myContent[0], true);
      }

      public boolean isAutoScroll() {
        return sliceToolwindowSettings.isAutoScroll();
      }

      public void setAutoScroll(boolean autoScroll) {
        sliceToolwindowSettings.setAutoScroll(autoScroll);
      }

      public boolean isPreview() {
        return sliceToolwindowSettings.isPreview();
      }

      public void setPreview(boolean preview) {
        sliceToolwindowSettings.setPreview(preview);
      }
    };

    myContent[0] = contentManager.getFactory().createContent(slicePanel, displayName, true);
    contentManager.addContent(myContent[0]);
    contentManager.setSelectedContent(myContent[0]);

    ToolWindowManager.getInstance(myProject).getToolWindow(dataFlowToThis ? BACK_TOOLWINDOW_ID : FORTH_TOOLWINDOW_ID).activate(null);
  }

  public static String getElementDescription(String prefix, PsiElement element, String suffix) {
    PsiElement elementToSlice = element;
    if (element instanceof PsiReferenceExpression) elementToSlice = ((PsiReferenceExpression)element).resolve();
    if (elementToSlice == null) elementToSlice = element;
    String desc = ElementDescriptionUtil.getElementDescription(elementToSlice, RefactoringDescriptionLocation.WITHOUT_PARENT);
    return "<html>"+ (prefix == null ? "" : prefix) + StringUtil.first(desc, 100, true)+(suffix == null ? "" : suffix) + "</html>";
  }

  public static SliceUsage createRootUsage(@NotNull PsiElement element, @NotNull AnalysisScope scope) {
    return new SliceUsage(element, scope);
  }

  public void checkCanceled() throws ProcessCanceledException {
    if (myCanceled) {
      throw new ProcessCanceledException();
    }
  }

  public void runInterruptibly(Runnable runnable, Runnable onCancel, ProgressIndicator progress) throws ProcessCanceledException {
    myCanceled = false;
    try {
      progress.checkCanceled();
      ((ProgressManagerImpl)ProgressManager.getInstance()).executeProcessUnderProgress(runnable, progress);
    }
    catch (ProcessCanceledException e) {
      cancel();
      progress.cancel();
      //reschedule for later
      onCancel.run();
      throw e;
    }
  }

  public Bean getState() {
    return myStoredSettings;
  }

  public void loadState(Bean state) {
    myStoredSettings.analysisUIOptions.save(state.analysisUIOptions);
  }
}
