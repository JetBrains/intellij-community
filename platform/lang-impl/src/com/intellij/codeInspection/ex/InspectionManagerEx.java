// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Author: max
 */

package com.intellij.codeInspection.ex;

import com.intellij.analysis.problemsView.toolWindow.ProblemsView;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.TabbedPaneContentUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class InspectionManagerEx extends InspectionManagerBase {
  private final NotNullLazyValue<ContentManager> myContentManager;
  private final Set<GlobalInspectionContextImpl> myRunningContexts = new HashSet<>();

  public InspectionManagerEx(final Project project) {
    super(project);
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      myContentManager = NotNullLazyValue.createValue(() -> {
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        toolWindowManager.registerToolWindow(ProblemsView.ID, true, ToolWindowAnchor.BOTTOM, project);
        return ContentFactory.getInstance().createContentManager(new TabbedPaneContentUI(), true, project);
      });
    }
    else {
      myContentManager = NotNullLazyValue.createValue(() -> getProblemsViewContentManager(project));
    }
  }

  protected @NotNull ContentManager getProblemsViewContentManager(@NotNull Project project) {
    ToolWindow toolWindow = Objects.requireNonNull(ProblemsView.getToolWindow(project));
    ContentManager contentManager = toolWindow.getContentManager();
    ContentManagerWatcher.watchContentManager(toolWindow, contentManager);
    return contentManager;
  }

  public @NotNull ProblemDescriptor createProblemDescriptor(final @NotNull PsiElement psiElement,
                                                            final @NotNull @InspectionMessage String descriptionTemplate,
                                                            final @NotNull ProblemHighlightType highlightType,
                                                            final @Nullable HintAction hintAction,
                                                            boolean onTheFly,
                                                            @NotNull LocalQuickFix @Nullable ... fixes) {
    return new ProblemDescriptorImpl(psiElement, psiElement, descriptionTemplate, fixes, highlightType, false, null, hintAction, onTheFly);
  }

  @Override
  public @NotNull GlobalInspectionContextImpl createNewGlobalContext(boolean reuse) {
    return createNewGlobalContext();
  }

  @Override
  public @NotNull GlobalInspectionContextImpl createNewGlobalContext() {
    final GlobalInspectionContextImpl inspectionContext = new GlobalInspectionContextImpl(getProject(), myContentManager);
    myRunningContexts.add(inspectionContext);
    return inspectionContext;
  }

  void closeRunningContext(@NotNull GlobalInspectionContextImpl globalInspectionContext){
    myRunningContexts.remove(globalInspectionContext);
  }

  public @NotNull Set<GlobalInspectionContextImpl> getRunningContexts() {
    return myRunningContexts;
  }

  @TestOnly
  public @NotNull NotNullLazyValue<ContentManager> getContentManager() {
    return myContentManager;
  }
}
