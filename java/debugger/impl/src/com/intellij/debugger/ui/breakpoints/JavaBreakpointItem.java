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
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.SourcePosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.popup.util.DetailView;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

class JavaBreakpointItem extends BreakpointItem {
  private final Breakpoint myBreakpoint;
  private BreakpointFactory myBreakpointFactory;
  private BreakpointPropertiesPanel myBreakpointPropertiesPanel;

  public JavaBreakpointItem(@Nullable BreakpointFactory breakpointFactory, Breakpoint breakpoint) {
    myBreakpointFactory = breakpointFactory;
    myBreakpoint = breakpoint;
  }

  @Override
  public void setupGenericRenderer(SimpleColoredComponent renderer, boolean plainView) {
    if (plainView) {
      renderer.setIcon(myBreakpoint.getIcon());
    }
    renderer.append(plainView ? StringUtil.shortenTextWithEllipsis(myBreakpoint.getShortName(), 60, 0) : myBreakpoint.getDisplayName(),
                    isEnabled() ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.GRAY_ATTRIBUTES);
  }

  @Override
  public Icon getIcon() {
    return myBreakpoint.getIcon();
  }

  @Override
  public String getDisplayText() {
    return myBreakpoint.getDisplayName();
  }

  @Override
  public String speedSearchText() {
    return myBreakpoint.getDisplayName();
  }

  @Override
  public String footerText() {
    return myBreakpoint.getDisplayName();
  }

  @Override
  protected void doUpdateDetailView(DetailView panel, boolean editorOnly) {
    //saveState();
    myBreakpointPropertiesPanel = null;

    if (!editorOnly) {
      myBreakpointPropertiesPanel = myBreakpointFactory != null ? myBreakpointFactory
        .createBreakpointPropertiesPanel(myBreakpoint.getProject(), false) : null;

      if (myBreakpointPropertiesPanel != null) {
        myBreakpointPropertiesPanel.initFrom(myBreakpoint, true);

        final JPanel mainPanel = myBreakpointPropertiesPanel.getPanel();
        panel.setPropertiesPanel(mainPanel);
      }
      else {
        panel.setPropertiesPanel(null);
      }
    }

    if (myBreakpoint instanceof BreakpointWithHighlighter) {
      SourcePosition sourcePosition = ((BreakpointWithHighlighter)myBreakpoint).getSourcePosition();
      VirtualFile virtualFile = sourcePosition.getFile().getVirtualFile();
      showInEditor(panel, virtualFile, sourcePosition.getLine());
    } else {
      panel.clearEditor();
    }
    if (myBreakpointPropertiesPanel != null) {
      myBreakpointPropertiesPanel.setDetailView(panel);
    }
  }

  @Override
  public void navigate(boolean requestFocus) {
    if (myBreakpoint instanceof BreakpointWithHighlighter) {
      ((BreakpointWithHighlighter)myBreakpoint).getSourcePosition().navigate(requestFocus);
    }
  }

  @Override
  public boolean canNavigate() {
    return myBreakpoint instanceof BreakpointWithHighlighter && ((BreakpointWithHighlighter)myBreakpoint).getSourcePosition().canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    return myBreakpoint instanceof BreakpointWithHighlighter &&
           ((BreakpointWithHighlighter)myBreakpoint).getSourcePosition().canNavigateToSource();
  }

  @Override
  public boolean allowedToRemove() {
    return myBreakpointFactory != null && myBreakpointFactory.breakpointCanBeRemoved(myBreakpoint);
  }

  @Override
  public void removed(Project project) {
    DebuggerManagerEx.getInstanceEx(project).getBreakpointManager().removeBreakpoint(myBreakpoint);
  }

  @Override
  public void saveState() {
    if (myBreakpointPropertiesPanel != null) {
      myBreakpointPropertiesPanel.saveTo(myBreakpoint, EmptyRunnable.INSTANCE);
    }
  }

  @Override
  public Object getBreakpoint() {
    return myBreakpoint;
  }

  @Override
  public boolean isEnabled() {
    return myBreakpoint.ENABLED;
  }

  @Override
  public void setEnabled(boolean state) {
    myBreakpoint.ENABLED = state;
    myBreakpoint.updateUI();
    DebuggerManagerEx.getInstanceEx(myBreakpoint.getProject()).getBreakpointManager().fireBreakpointChanged(myBreakpoint);
  }

  @Override
  public boolean isDefaultBreakpoint() {
    return myBreakpoint.getCategory().equals(ExceptionBreakpoint.CATEGORY);
  }

  @Override
  public int compareTo(@NotNull BreakpointItem breakpointItem) {
    final Object breakpoint = breakpointItem.getBreakpoint();
    if (breakpoint instanceof Breakpoint) {
      return -getIndexOf(myBreakpoint) + getIndexOf((Breakpoint)breakpoint);
    }
    return getDisplayText().compareTo(breakpointItem.getDisplayText());
  }

  private int getIndexOf(Breakpoint breakpoint) {
    return DebuggerManagerEx.getInstanceEx(myBreakpoint.getProject()).getBreakpointManager().getBreakpoints().indexOf(breakpoint);
  }
}
