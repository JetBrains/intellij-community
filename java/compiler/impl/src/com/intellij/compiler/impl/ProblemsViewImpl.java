/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.compiler.impl;

import com.intellij.compiler.ProblemsView;
import com.intellij.icons.AllIcons;
import com.intellij.ide.errorTreeView.ErrorTreeElement;
import com.intellij.ide.errorTreeView.ErrorTreeElementKind;
import com.intellij.ide.errorTreeView.ErrorViewStructure;
import com.intellij.ide.errorTreeView.GroupingElement;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

import javax.swing.*;
import java.util.EnumSet;
import java.util.UUID;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/18/12
 */
public class ProblemsViewImpl extends ProblemsView{
  private static final String PROBLEMS_TOOLWINDOW_ID = "Problems";
  private static final EnumSet<ErrorTreeElementKind> ALL_MESSAGE_KINDS = EnumSet.allOf(ErrorTreeElementKind.class);

  private final ProblemsViewPanel myPanel;
  private final SequentialTaskExecutor myViewUpdater = new SequentialTaskExecutor(PooledThreadExecutor.INSTANCE);
  private final Icon myActiveIcon = AllIcons.Toolwindows.Problems;
  private final Icon myPassiveIcon = IconLoader.getDisabledIcon(myActiveIcon);

  public ProblemsViewImpl(final Project project, final ToolWindowManager wm) {
    super(project);
    myPanel = new ProblemsViewPanel(project);
    Disposer.register(project, new Disposable() {
      @Override
      public void dispose() {
        Disposer.dispose(myPanel);
      }
    });
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (project.isDisposed()) {
          return;
        }
        final ToolWindow tw = wm.registerToolWindow(PROBLEMS_TOOLWINDOW_ID, false, ToolWindowAnchor.BOTTOM, project, true);
        final Content content = ContentFactory.SERVICE.getInstance().createContent(myPanel, "", false);
        // todo: setup content?
        tw.getContentManager().addContent(content);
        Disposer.register(project, new Disposable() {
          @Override
          public void dispose() {
            tw.getContentManager().removeAllContents(true);
          }
        });
        updateIcon();
      }
    });
  }

  @Override
  public void clearOldMessages(@Nullable final CompileScope scope, @NotNull final UUID currentSessionId) {
    myViewUpdater.execute(new Runnable() {
      @Override
      public void run() {
        cleanupChildrenRecursively(myPanel.getErrorViewStructure().getRootElement(), scope, currentSessionId);
        updateIcon();
        myPanel.reload();
      }
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
  public void addMessage(final int type,
                         @NotNull final String[] text,
                         @Nullable final String groupName,
                         @Nullable final Navigatable navigatable,
                         @Nullable final String exportTextPrefix, @Nullable final String rendererTextPrefix, @Nullable final UUID sessionId) {

    myViewUpdater.execute(new Runnable() {
      @Override
      public void run() {
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
        updateIcon();
      }
    });
  }

  private void updateIcon() {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (!myProject.isDisposed()) {
          final ToolWindow tw = ToolWindowManager.getInstance(myProject).getToolWindow(PROBLEMS_TOOLWINDOW_ID);
          if (tw != null) {
            final boolean active = myPanel.getErrorViewStructure().hasMessages(ALL_MESSAGE_KINDS);
            tw.setIcon(active ? myActiveIcon : myPassiveIcon);
          }
        }
      }
    });
  }

  @Override
  public void setProgress(String text, float fraction) {
    myPanel.setProgress(text, fraction);
  }

  @Override
  public void setProgress(String text) {
    myPanel.setProgressText(text);
  }

  @Override
  public void clearProgress() {
    myPanel.clearProgressData();
  }
}
