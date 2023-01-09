// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.impl;

import com.intellij.compiler.ProblemsView;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.errorTreeView.ErrorTreeElement;
import com.intellij.ide.errorTreeView.ErrorTreeElementKind;
import com.intellij.ide.errorTreeView.ErrorViewStructure;
import com.intellij.ide.errorTreeView.GroupingElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import kotlin.Unit;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

@SuppressWarnings("IncorrectParentDisposable")
final class ProblemsViewImpl extends ProblemsView {
  private static final String AUTO_BUILD_TOOLWINDOW_ID = "Problems";

  private volatile ProblemsViewPanel myPanel;
  private final ExecutorService myViewUpdater = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("ProblemsView Pool");
  private static final EnumSet<ErrorTreeElementKind> interestingMessageKinds =
    EnumSet.of(ErrorTreeElementKind.ERROR, ErrorTreeElementKind.WARNING, ErrorTreeElementKind.NOTE);

  ProblemsViewImpl(@NotNull Project project) {
    super(project);

    Disposer.register(project, myViewUpdater::shutdownNow);
    myViewUpdater.execute(() -> {
      ApplicationManager.getApplication().invokeAndWait(() -> {
        if (project.isDisposed()) {
          return;
        }

        ProblemsViewPanel panel = new ProblemsViewPanel(project);

        ToolWindow toolWindow = ToolWindowManager.getInstance(project).registerToolWindow(AUTO_BUILD_TOOLWINDOW_ID, builder -> {
          builder.icon = AllIcons.Toolwindows.ProblemsEmpty;
          builder.stripeTitle = IdeBundle.messagePointer("toolwindow.stripe.Problems");
          builder.canCloseContent = false;
          return Unit.INSTANCE;
        });
        Disposer.register(toolWindow.getDisposable(), panel);

        Content content = ContentFactory.getInstance().createContent(panel, "", false);
        content.setHelpId("reference.problems.tool.window");
        toolWindow.getContentManager().addContent(content);

        doUpdateIcon(panel, toolWindow, null);

        myPanel = panel;
      }, ModalityState.NON_MODAL);
    });
  }

  @Override
  public void clearOldMessages(@Nullable CompileScope scope, @NotNull UUID currentSessionId) {
    myViewUpdater.execute(() -> {
      cleanupChildrenRecursively(myPanel.getErrorViewStructure().getRootElement(), scope, currentSessionId);
      updateIcon(null);
      myPanel.reload();
    });
  }

  private void cleanupChildrenRecursively(@NotNull final Object fromElement, final @Nullable CompileScope scope, @NotNull UUID currentSessionId) {
    final ErrorViewStructure structure = myPanel.getErrorViewStructure();
    for (ErrorTreeElement element : structure.getChildElements(fromElement)) {
      if (element instanceof GroupingElement) {
        if (scope != null) {
          final VirtualFile file = ((GroupingElement)element).getFile();
          if (file != null && !scope.belongs(file.getUrl())) {
            continue;
          }
        }
        if (!currentSessionId.equals(element.getData())) {
          structure.removeElement(element);
        }
        else {
          cleanupChildrenRecursively(element, scope, currentSessionId);
        }
      }
      else {
        if (!currentSessionId.equals(element.getData())) {
          structure.removeElement(element);
        }
      }
    }
  }

  @Override
  public void addMessage(int type,
                         String @NotNull [] text,
                         @Nullable String groupName,
                         @Nullable Navigatable navigatable,
                         @Nullable String exportTextPrefix, @Nullable String rendererTextPrefix, @Nullable UUID sessionId) {
    myViewUpdater.execute(() -> {
      final ErrorViewStructure structure = myPanel.getErrorViewStructure();
      final GroupingElement group = structure.lookupGroupingElement(groupName);
      if (group != null && sessionId != null && !sessionId.equals(group.getData())) {
        structure.removeElement(group);
      }
      if (navigatable != null) {
        myPanel.addMessage(type, text, groupName, navigatable, exportTextPrefix, rendererTextPrefix, sessionId);
      }
      else {
        myPanel.addMessage(type, text, null, -1, -1, sessionId);
      }
      updateIcon(sessionId);
    });
  }

  private void updateIcon(final @Nullable UUID currentSessionId) {
    ApplicationManager.getApplication().invokeLater(() -> {
      ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(AUTO_BUILD_TOOLWINDOW_ID);
      if (toolWindow != null) {
        doUpdateIcon(myPanel, toolWindow, currentSessionId);
      }
    }, myProject.getDisposed());
  }

  private static void doUpdateIcon(@NotNull ProblemsViewPanel panel, @NotNull ToolWindow toolWindow, @Nullable UUID currentSessionId) {
    boolean active = panel.getErrorViewStructure().hasMessages(interestingMessageKinds, element -> currentSessionId == null || currentSessionId.equals(element.getData()));
    toolWindow.setIcon(active ? AllIcons.Toolwindows.Problems : AllIcons.Toolwindows.ProblemsEmpty);
  }

  @Override
  public void buildStarted(@NotNull UUID sessionId) {
    super.buildStarted(sessionId);
    updateIcon(sessionId);
  }

  @Override
  public void setProgress(@Nls String text, float fraction) {
    ProblemsViewPanel panel = myPanel;
    if (panel == null) {
      myViewUpdater.execute(() -> myPanel.setProgress(text, fraction));
    }
    else {
      panel.setProgress(text, fraction);
    }
  }

  @Override
  public void setProgress(@Nls String text) {
    ProblemsViewPanel panel = myPanel;
    if (panel == null) {
      myViewUpdater.execute(() -> myPanel.setProgressText(text));
    }
    else {
      panel.setProgressText(text);
    }
  }

  @Override
  public void clearProgress() {
    ProblemsViewPanel panel = myPanel;
    if (panel != null) {
      panel.clearProgressData();
    }
  }
}
