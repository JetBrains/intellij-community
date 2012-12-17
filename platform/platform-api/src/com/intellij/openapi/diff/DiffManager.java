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
package com.intellij.openapi.diff;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.markup.MarkupEditorFilter;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public abstract class DiffManager {

  public static DiffManager getInstance() {
    return ServiceManager.getService(DiffManager.class);
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
   *
   * @param window this window will be disposed, when user clicks on the line number
   *        You must call Disposer.dispose() when done.
   * @param disposable
   *@param parentTool @deprecated use {@link #createDiffPanel(Window, Project, Disposable)} instead
   */
  public abstract DiffPanel createDiffPanel(Window window, Project project, DiffTool parentTool);
  public abstract DiffPanel createDiffPanel(Window window, Project project, @NotNull Disposable parentDisposable, DiffTool parentTool);
}
