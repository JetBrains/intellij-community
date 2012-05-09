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
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.ui.breakpoints.actions.BreakpointPanelAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.popup.util.DetailView;
import com.intellij.ui.popup.util.DetailViewImpl;
import com.intellij.xdebugger.breakpoints.ui.BreakpointItem;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Used to deexternalize breakpoints of certain category while reading saved configuration and for creating configuration UI
 */
public abstract class BreakpointFactory {
  public static final ExtensionPointName<BreakpointFactory> EXTENSION_POINT_NAME =
    ExtensionPointName.create("com.intellij.debugger.breakpointFactory");

  public static BreakpointFactory[] getBreakpointFactories() {
    return ApplicationManager.getApplication().getExtensions(EXTENSION_POINT_NAME);
  }

  public abstract Breakpoint createBreakpoint(Project project, final Element element);

  public abstract Key<? extends Breakpoint> getBreakpointCategory();

  public BreakpointPanel createBreakpointPanel(final Project project, final DialogWrapper parentDialog) {
    BreakpointPanel panel =
      new BreakpointPanel(project, createBreakpointPropertiesPanel(project, false), createBreakpointPanelActions(project, parentDialog),
                          getBreakpointCategory(), getDisplayName(), getHelpID());
    configureBreakpointPanel(panel);
    return panel;
  }

  public abstract Icon getIcon();

  public abstract Icon getDisabledIcon();

  @Nullable
  public static BreakpointFactory getInstance(Key<? extends Breakpoint> category) {
    final BreakpointFactory[] allFactories = getBreakpointFactories();
    for (final BreakpointFactory factory : allFactories) {
      if (category.equals(factory.getBreakpointCategory())) {
        return factory;
      }
    }
    return null;
  }

  protected void configureBreakpointPanel(BreakpointPanel panel) {
  }

  ;

  protected abstract String getHelpID();

  public abstract String getDisplayName();

  @Nullable
  public abstract BreakpointPropertiesPanel createBreakpointPropertiesPanel(Project project, boolean compact);

  protected abstract BreakpointPanelAction[] createBreakpointPanelActions(Project project, DialogWrapper parentDialog);

  public BreakpointItem createBreakpointItem(final Breakpoint breakpoint) {
    return new BreakpointItem() {
      @Override
      public void setupRenderer(ColoredListCellRenderer renderer, Project project, boolean selected) {
        renderer.setIcon(breakpoint.getIcon());
        renderer.append(breakpoint.getDisplayName());
      }

      @Override
      public void updateMnemonicLabel(JLabel label) {
        //To change body of implemented methods use File | Settings | File Templates.
      }

      @Override
      public void execute(Project project) {
        //To change body of implemented methods use File | Settings | File Templates.
      }

      @Override
      public String speedSearchText() {
        return breakpoint.getDisplayName();
      }

      @Override
      public String footerText() {
        return breakpoint.getDisplayName();
      }

      @Override
      public void updateDetailView(DetailView panel) {
        if (breakpoint instanceof LineBreakpoint) {
          SourcePosition sourcePosition = ((LineBreakpoint)breakpoint).getSourcePosition();
          VirtualFile virtualFile = sourcePosition.getFile().getVirtualFile();
          panel.navigateInPreviewEditor(virtualFile, new LogicalPosition(sourcePosition.getLine(), 0));
        }

        BreakpointPropertiesPanel breakpointPropertiesPanel = createBreakpointPropertiesPanel(breakpoint.getProject(), false);
        if (breakpointPropertiesPanel != null) {
          breakpointPropertiesPanel.initFrom(breakpoint, true);
          final JPanel mainPanel = breakpointPropertiesPanel.getPanel();
          panel.setDetailPanel(mainPanel);
        }
      }
    };
  }
}
