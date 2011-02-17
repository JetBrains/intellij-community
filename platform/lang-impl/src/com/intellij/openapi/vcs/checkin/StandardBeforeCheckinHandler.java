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

package com.intellij.openapi.vcs.checkin;

import com.intellij.codeInsight.actions.OptimizeImportsProcessor;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;

public class StandardBeforeCheckinHandler extends CheckinHandler implements CheckinMetaHandler {
  protected final Project myProject;
  private final CheckinProjectPanel myPanel;

  public StandardBeforeCheckinHandler(final Project project, final CheckinProjectPanel panel) {
    myProject = project;
    myPanel = panel;
  }

  @Nullable
  public RefreshableOnComponent getBeforeCheckinConfigurationPanel() {
    final JCheckBox optimizeBox = new JCheckBox(VcsBundle.message("checkbox.checkin.options.optimize.imports"));
    final JCheckBox reformatBox = new JCheckBox(VcsBundle.message("checkbox.checkin.options.reformat.code"));

    return new RefreshableOnComponent() {
      public JComponent getComponent() {
        final JPanel panel = new JPanel(new GridLayout(2, 0));
        panel.add(optimizeBox);
        panel.add(reformatBox);
        return panel;
      }

      public void refresh() {
      }

      public void saveState() {
        getSettings().OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT = optimizeBox.isSelected();
        getSettings().REFORMAT_BEFORE_PROJECT_COMMIT = reformatBox.isSelected();
      }

      public void restoreState() {
        optimizeBox.setSelected(getSettings().OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT);
        reformatBox.setSelected(getSettings().REFORMAT_BEFORE_PROJECT_COMMIT);
      }
    };

  }

  protected VcsConfiguration getSettings() {
    return VcsConfiguration.getInstance(myProject);
  }

  public void runCheckinHandlers(final Runnable finishAction) {
    final VcsConfiguration configuration = VcsConfiguration.getInstance(myProject);
    final Collection<VirtualFile> files = myPanel.getVirtualFiles();

    final Runnable performCheckoutAction = new Runnable() {
      public void run() {
        FileDocumentManager.getInstance().saveAllDocuments();
        finishAction.run();
      }
    };

    final Runnable reformatCodeAndPerformCheckout = new Runnable() {
      public void run() {
        if (reformat(configuration, true)) {
          new ReformatCodeProcessor(myProject, getPsiFiles(files), performCheckoutAction).run();
        }
        else {
          performCheckoutAction.run();
        }
      }
    };

    if (configuration.OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT) {
      new OptimizeImportsProcessor(myProject, getPsiFiles(files), reformatCodeAndPerformCheckout).run();
    }
    else {
      reformatCodeAndPerformCheckout.run();
    }

  }

  private static boolean reformat(final VcsConfiguration configuration, boolean checkinProject) {
    return checkinProject ? configuration.REFORMAT_BEFORE_PROJECT_COMMIT : configuration.REFORMAT_BEFORE_FILE_COMMIT;
  }

  private PsiFile[] getPsiFiles(Collection<VirtualFile> selectedFiles) {
    ArrayList<PsiFile> result = new ArrayList<PsiFile>();
    PsiManager psiManager = PsiManager.getInstance(myProject);

    VirtualFile projectFileDir = null;
    final StorageScheme storageScheme = ((ProjectEx) myProject).getStateStore().getStorageScheme();
    if (StorageScheme.DIRECTORY_BASED.equals(storageScheme)) {
      VirtualFile baseDir = myProject.getBaseDir();
      if (baseDir != null) {
        projectFileDir = baseDir.findChild(Project.DIRECTORY_STORE_FOLDER);
      }
    }

    for (VirtualFile file : selectedFiles) {
      if (file.isValid()) {
        if (projectFileDir != null && VfsUtil.isAncestor(projectFileDir, file, false)) {
          continue;
        }
        PsiFile psiFile = psiManager.findFile(file);
        if (psiFile != null) result.add(psiFile);
      }
    }
    return PsiUtilBase.toPsiFileArray(result);
  }


}
