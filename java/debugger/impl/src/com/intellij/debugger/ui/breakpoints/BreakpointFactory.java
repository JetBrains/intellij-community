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

import com.intellij.debugger.ui.breakpoints.actions.BreakpointPanelAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Key;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Used to deexternalize breakpoints of certain category while reading saved configuration and for creating configuration UI
 */
public abstract class BreakpointFactory {
  public static final ExtensionPointName<BreakpointFactory> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.debugger.breakpointFactory");

  public abstract Breakpoint createBreakpoint(Project project, final Element element);

  public abstract Key<? extends Breakpoint> getBreakpointCategory();

  public BreakpointPanel createBreakpointPanel(final Project project, final DialogWrapper parentDialog) {
    BreakpointPanel panel = new BreakpointPanel(project,
                                                createBreakpointPropertiesPanel(project),
                                                createBreakpointPanelActions(project, parentDialog),
                                                getBreakpointCategory(), getDisplayName(), getHelpID());
    configureBreakpointPanel(panel);
    return panel;
  }

  public abstract Icon getIcon();

  public abstract Icon getDisabledIcon();

  @Nullable
  public static BreakpointFactory getInstance(Key<? extends Breakpoint> category) {
    final BreakpointFactory[] allFactories = ApplicationManager.getApplication().getExtensions(BreakpointFactory.EXTENSION_POINT_NAME);
    for (final BreakpointFactory factory : allFactories) {
      if (category.equals(factory.getBreakpointCategory())) {
        return factory;
      }
    }
    return null;
  }

  protected void configureBreakpointPanel(BreakpointPanel panel){};

  protected abstract String getHelpID();

  public abstract String getDisplayName();

  public abstract BreakpointPropertiesPanel createBreakpointPropertiesPanel(Project project);

  protected abstract BreakpointPanelAction[] createBreakpointPanelActions(Project project, DialogWrapper parentDialog);
}
