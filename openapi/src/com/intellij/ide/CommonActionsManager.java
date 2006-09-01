/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.ide;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 20, 2004
 * Time: 9:39:48 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class CommonActionsManager {
  public static CommonActionsManager getInstance() {
    return ApplicationManager.getApplication().getComponent(CommonActionsManager.class);
  }

  public abstract AnAction createPrevOccurenceAction(OccurenceNavigator navigator);
  public abstract AnAction createNextOccurenceAction(OccurenceNavigator navigator);

  @Deprecated
  public abstract AnAction createExpandAllAction(TreeExpander expander);
  public abstract AnAction createExpandAllAction(TreeExpander expander, JComponent component);

  @Deprecated
  public abstract AnAction createCollapseAllAction(TreeExpander expander);
  public abstract AnAction createCollapseAllAction(TreeExpander expander, JComponent component);

  public abstract AnAction createHelpAction(String helpId);

  /**
   * Installs autoscroll capability support to JTree passed. Toggle action returned.
   * @param project
   * @return toggle action to be inserted to appropriate toolbar
   * @param tree should provide DataContstants.NAVIGATABLE for handler to work on
   * @param optionProvider get/set API to externalizable property.
   */
  public abstract AnAction installAutoscrollToSourceHandler(Project project, JTree tree, AutoScrollToSourceOptionProvider optionProvider);

  public abstract AnAction createExportToTextFileAction(ExporterToTextFile exporter);
}
