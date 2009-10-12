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

package com.intellij.facet.impl.autodetecting.facetsTree;

import com.intellij.facet.impl.autodetecting.DetectedFacetManager;
import com.intellij.facet.impl.autodetecting.model.DetectedFacetInfo;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.help.HelpManager;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/**
 * @author nik
 */
public class DetectedFacetsDialog extends DialogWrapper {
  private final ImplicitFacetsTreeComponent myFacetsTreeComponent;
  private JPanel myMainPanel;
  private JPanel myTreePanel;

  public DetectedFacetsDialog(final Project project, final DetectedFacetManager detectedFacetsManager, final Collection<DetectedFacetInfo<Module>> detectedFacets,
                              final HashMap<DetectedFacetInfo<Module>, List<VirtualFile>> files) {
    super(project, true);
    setTitle(ProjectBundle.message("dialog.title.facets.detected"));
    myFacetsTreeComponent = new ImplicitFacetsTreeComponent(detectedFacetsManager, detectedFacets, files);
    DetectedFacetsTree tree = myFacetsTreeComponent.getTree();
    TreeUtil.expandAll(tree);
    myTreePanel.add(tree, BorderLayout.CENTER);
    setOKButtonText(ProjectBundle.message("button.text.accept.detected.facets"));
    setCancelButtonText(ProjectBundle.message("button.text.postpone.detected.facets"));
    init();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("procedures.workingwithmodules.facet");
  }

  protected void doOKAction() {
    new WriteAction() {
      protected void run(final Result result) {
        myFacetsTreeComponent.createFacets();
      }
    }.execute();
    super.doOKAction();
  }

}
