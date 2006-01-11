/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Used to deexternalize breakpoints of certain category while reading saved configuration and for creating configuration UI
 */
public abstract class BreakpointFactory implements ApplicationComponent{

  public abstract Breakpoint createBreakpoint(Project project, final Element element);

  public abstract String getBreakpointCategory();

  public abstract @Nullable BreakpointPanel createBreakpointPanel(Project project, DialogWrapper parentDialog);

  public abstract Icon getIcon();

  public abstract Icon getDisabledIcon();

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public static BreakpointFactory getInstance(String category) {
    final BreakpointFactory[] allFactories = ApplicationManager.getApplication().getComponents(BreakpointFactory.class);
    for (int idx = 0; idx < allFactories.length; idx++) {
      final BreakpointFactory factory = allFactories[idx];
      if (category.equals(factory.getBreakpointCategory())) {
        return factory;
      }
    }
    return null;
  }

}
