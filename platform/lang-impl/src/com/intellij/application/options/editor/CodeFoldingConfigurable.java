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

package com.intellij.application.options.editor;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.options.AbstractConfigurableEP;
import com.intellij.openapi.options.CompositeConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class CodeFoldingConfigurable extends CompositeConfigurable<CodeFoldingOptionsProvider> implements EditorOptionsProvider {
  private JCheckBox myCbFolding;
  private JPanel myRootPanel;
  private JPanel myFoldingPanel;

  @Nls
  public String getDisplayName() {
    return ApplicationBundle.message("group.code.folding");
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return "reference.settingsdialog.IDE.editor.code.folding";
  }

  public JComponent createComponent() {
    for (CodeFoldingOptionsProvider provider : getConfigurables()) {
      myFoldingPanel
        .add(provider.createComponent(), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0, 0, GridBagConstraints.NORTHWEST,
                                                                GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    }
    return myRootPanel;
  }

  public boolean isModified() {
    return myCbFolding.isSelected() != EditorSettingsExternalizable.getInstance().isFoldingOutlineShown() ||
           super.isModified();
  }

  public void apply() throws ConfigurationException {
    EditorSettingsExternalizable.getInstance().setFoldingOutlineShown(myCbFolding.isSelected());
    super.apply();

    final List<Pair<Editor, Project>> toUpdate = new ArrayList<Pair<Editor, Project>>();
    for (final Editor editor : EditorFactory.getInstance().getAllEditors()) {
      final Project project = editor.getProject();
      if (project != null && !project.isDefault()) {
        toUpdate.add(Pair.create(editor, project));
      }
    }

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        for (Pair<Editor, Project> each : toUpdate) {
          final CodeFoldingManager foldingManager = CodeFoldingManager.getInstance(each.second);
          if (foldingManager != null) {
            foldingManager.buildInitialFoldings(each.first);
          }
        }
        EditorOptionsPanel.reinitAllEditors();
      }
    }, ModalityState.NON_MODAL);
  }

  public void reset() {
    myCbFolding.setSelected(EditorSettingsExternalizable.getInstance().isFoldingOutlineShown());
    super.reset();
  }

  protected List<CodeFoldingOptionsProvider> createConfigurables() {
    return AbstractConfigurableEP.createConfigurables(CodeFoldingOptionsProviderEP.EP_NAME);
  }

  public String getId() {
    return "editor.preferences.folding";
  }

  public Runnable enableSearch(final String option) {
    return null;
  }
}
