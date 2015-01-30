/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.formatting.contextConfiguration;

import com.intellij.application.options.TabbedLanguageCodeStylePanel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;
import java.awt.*;

class CodeFragmentCodeStyleSettingsPanel extends TabbedLanguageCodeStylePanel {
  private static final Logger LOG = Logger.getInstance(CodeFragmentCodeStyleSettingsPanel.class); 
  
  private final Editor myEditor;
  private final PsiFile myFile;

  public CodeFragmentCodeStyleSettingsPanel(CodeStyleSettings settings, Editor editor, PsiFile file) {
    super(file.getLanguage(), settings, settings.clone());
    myEditor = editor;
    myFile = file;
  }

  @Override
  protected void initTabs(CodeStyleSettings settings) {
    addTab(new SpacesPanelWithoutPreview(settings));
    addTab(new WrappingAndBracesPanelWithoutPreview(settings));
    reset(getSettings());
  }
  
  private void reformatSelectedTextWithNewSettings() {
    final SelectionModel model = myEditor.getSelectionModel();
    if (model.hasSelection()) {
      
      try {
        apply(getSettings());
      }
      catch (ConfigurationException e) {
        LOG.debug("Cannot apply code style settings", e);
      }

      CodeStyleSettings clone = getSettings().clone();

      Project project = myEditor.getProject();
      if (project == null) return;

      try {
        CodeStyleSettingsManager.getInstance(project).setTemporarySettings(clone);
        reformatSelectedFragment(model, myFile);
      }
      finally {
        CodeStyleSettingsManager.getInstance(project).dropTemporarySettings();
      }
    }
  }

  private static void reformatSelectedFragment(final SelectionModel model, final PsiFile file) {
    final Project project = file.getProject();
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            CodeStyleManager.getInstance(project).reformatText(file, model.getSelectionStart(), model.getSelectionEnd());
          }
        });
      }
    }, "Reformat", null);
  }
  
  private class SpacesPanelWithoutPreview extends MySpacesPanel {
    private JPanel myPanel;

    public SpacesPanelWithoutPreview(CodeStyleSettings settings) {
      super(settings);
    }

    @Override
    protected void somethingChanged() {
      reformatSelectedTextWithNewSettings();
    }

    @Override
    protected void init() {
      customizeSettings();
      initTables();

      myOptionsTree = createOptionsTree();
      myOptionsTree.setCellRenderer(new MyTreeCellRenderer());

      JBScrollPane pane = new JBScrollPane(myOptionsTree) {
        @Override
        public Dimension getMinimumSize() {
          return super.getPreferredSize();
        }
      };

      myPanel = new JPanel(new BorderLayout());
      myPanel.add(pane);

      //todo what is this? is this really needed?
      //isFirstUpdate = false;
    }
    
    @Override
    public JComponent getPanel() {
      return myPanel;
    }
  }

  private class WrappingAndBracesPanelWithoutPreview extends MyWrappingAndBracesPanel {
    public JPanel myPanel;

    public WrappingAndBracesPanelWithoutPreview(CodeStyleSettings settings) {
      super(settings);
    }

    @Override
    protected void init() {
      customizeSettings();
      initTables();

      myTreeTable = createOptionsTree(getSettings());
      JBScrollPane scrollPane = new JBScrollPane(myTreeTable) {
        @Override
        public Dimension getMinimumSize() {
          return super.getPreferredSize();
        }
      };

      myPanel = new JPanel(new BorderLayout());
      myPanel.add(scrollPane);

      //todo why this needed here?
      customizeSettings();

      //todo needed?
      //isFirstUpdate = false;
    }

    @Override
    public JComponent getPanel() {
      return myPanel;
    }

    @Override
    protected void somethingChanged() {
      reformatSelectedTextWithNewSettings();
    }
  }
}
