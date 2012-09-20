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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/18/12
 */
public class ProblemsViewImpl extends ProblemsView{
  private static final String PROBLEMS_TOOLWINDOW_ID = "Problems";
  
  private final ProblemsViewPanel myPanel;

  public ProblemsViewImpl(final Project project, final ToolWindowManager wm) {
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
        final ToolWindow tw = wm.registerToolWindow(PROBLEMS_TOOLWINDOW_ID, false, ToolWindowAnchor.BOTTOM, project);
        final Content content = ContentFactory.SERVICE.getInstance().createContent(myPanel, "", false);
        // todo: setup content?
        tw.getContentManager().addContent(content);
        Disposer.register(project, new Disposable() {
          @Override
          public void dispose() {
            tw.getContentManager().removeAllContents(true);
          }
        });
      }
    });
  }

  @Override
  public void clearMessages(CompileScope scope) {
    // todo: temporary solution:
    clearMessages();
    
    /*
    final ErrorViewStructure structure = myPanel.getErrorViewStructure();
    for (ErrorTreeElement element : structure.getChildElements(structure.getRootElement())) {
      // todo: add ability to remove selected messages in structure
    }
    */
  }

  @Override
  public void clearMessages() {
    myPanel.clearMessages();
  }

  @Override
  public void addMessage(final int type, @NotNull final String[] text, @Nullable final VirtualFile file, final int line, final int column, @Nullable final Object data) {
    myPanel.addMessage(type, text, file, line, column, data);
  }
  
  @Override
  public void addMessage(final int type,
                         @NotNull final String[] text,
                         @Nullable final VirtualFile underFileGroup,
                         @Nullable final VirtualFile file,
                         final int line,
                         final int column, @Nullable final Object data) {
    myPanel.addMessage(type, text, underFileGroup, file, line, column, data);
  }

  @Override
  public void addMessage(final int type,
                         @NotNull final String[] text,
                         @Nullable final String groupName,
                         @NotNull final Navigatable navigatable,
                         @Nullable final String exportTextPrefix, @Nullable final String rendererTextPrefix, @Nullable final Object data) {
    myPanel.addMessage(type, text, groupName, navigatable, exportTextPrefix, rendererTextPrefix, data);
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
