/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.diff;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.markup.MarkupEditorFilter;
import com.intellij.openapi.project.Project;

import java.awt.*;

public abstract class DiffManager implements ApplicationComponent {
  public void disposeComponent() {
  }

  public String getComponentName() {
    return "DiffManager";
  }

  public void initComponent() {
  }

  public static DiffManager getInstance() {
    return ApplicationManager.getApplication().getComponent(DiffManager.class);
  }

  public abstract DiffTool getDiffTool();

  /**
   * Use to ignore use settings and get Idea own DiffTool.
   * Internal tool knows hints: <br>
   * {@link DiffTool#HINT_SHOW_MODAL_DIALOG} force show diff in modal dialog <br>
   * {@link DiffTool#HINT_SHOW_FRAME} don't check modal window open.
   * Show diff in frame in any case. May help as workaround when closing
   * modal dialog right before openning diff.<br>
   * {@link DiffTool#HINT_SHOW_NOT_MODAL_DIALOG} Show diff in not modal dialog<br>
   */
  public abstract DiffTool getIdeaDiffTool();

  /**
   * <B>Work in progess. Don't rely on this functionality</B><br>
   * Adds new diff tool.
   * 
   * @param tool diff tool to register
   * @return false iff tool already registered (tool won't be registered twice). Otherwise true.
   * @throws NullPointerException iff tool == null
   */
  public abstract boolean registerDiffTool(DiffTool tool) throws NullPointerException;

  /**
   * <B>Work in progess. Don't rely on this functionality</B><br>
   * Unregisters tool, registered with {@link #registerDiffTool(DiffTool)}
   * 
   * @param tool diff tool to unregister
   */
  public abstract void unregisterDiffTool(DiffTool tool);

  public abstract MarkupEditorFilter getDiffEditorFilter();

  /**
   * @param window this window will be disposed, when user clicks on the line number
   */
  public abstract DiffPanel createDiffPanel(Window window, Project project);
}
