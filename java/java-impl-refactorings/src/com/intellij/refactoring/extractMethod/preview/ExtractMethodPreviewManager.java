// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod.preview;

import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.java.JavaPluginDisposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;

@Service(Service.Level.PROJECT)
public final class ExtractMethodPreviewManager {
  private final Project myProject;
  private ContentManager myContentManager;

  public ExtractMethodPreviewManager(Project project) {
    myProject = project;

    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(() -> {
      ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
      ToolWindow toolWindow = toolWindowManager.registerToolWindow(
        ToolWindowId.EXTRACT_METHOD,
        true,
        ToolWindowAnchor.BOTTOM,
        JavaPluginDisposable.getInstance(myProject)
      );
      myContentManager = toolWindow.getContentManager();
      ContentManagerWatcher.watchContentManager(toolWindow, myContentManager);
    });
  }

  public void showPreview(ExtractMethodProcessor processor) {
    PsiFile psiFile = processor.getElements()[0].getContainingFile();
    String title = (psiFile != null ? psiFile.getName() + ": " : "") + processor.getMethodName() + "()";
    PreviewPanel panel = new PreviewPanel(processor);
    Content content = myContentManager.getFactory().createContent(panel, title, true);
    Disposer.register(content, panel);
    myContentManager.addContent(content);
    myContentManager.setSelectedContent(content);
    panel.setContent(content);

    ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.EXTRACT_METHOD).activate(panel::initLater);
  }

  public void closeContent(Content content) {
    myContentManager.removeContent(content, true);
  }

  public static ExtractMethodPreviewManager getInstance(Project project) {
    return project.getService(ExtractMethodPreviewManager.class);
  }
}
