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

package com.intellij.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;

public abstract class AutoScrollFromSourceHandler implements Disposable {
  protected final Project myProject;

  protected AutoScrollFromSourceHandler(Project project) {
    this(project, null);
  }

  protected AutoScrollFromSourceHandler(Project project, Disposable parentDisposable) {
    myProject = project;
    if (parentDisposable != null) {
      Disposer.register(parentDisposable, this);
    }
  }

  protected abstract boolean isAutoScrollMode();
  protected abstract void setAutoScrollMode(boolean state);
  public abstract void install();

  public ToggleAction createToggleAction() {
    return new AutoScrollFromSourceAction();
  }

  private class AutoScrollFromSourceAction extends ToggleAction implements DumbAware {
    public AutoScrollFromSourceAction() {
      super(UIBundle.message("autoscroll.from.source.action.name"), UIBundle.message("autoscroll.from.source.action.description"),
            IconLoader.getIcon("/general/autoscrollFromSource.png"));
    }

    public boolean isSelected(AnActionEvent event) {
      return isAutoScrollMode();
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      setAutoScrollMode(flag);
    }
  }
}

