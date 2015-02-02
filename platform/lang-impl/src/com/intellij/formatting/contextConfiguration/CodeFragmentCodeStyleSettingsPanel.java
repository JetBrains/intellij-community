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
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.*;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

class CodeFragmentCodeStyleSettingsPanel extends TabbedLanguageCodeStylePanel {
  private static final Logger LOG = Logger.getInstance(CodeFragmentCodeStyleSettingsPanel.class);

  private final Project myProject;
  private final Editor myEditor;
  private final PsiFile myFile;

  public CodeFragmentCodeStyleSettingsPanel(@NotNull CodeStyleSettings settings, 
                                            @NotNull Project project, 
                                            @NotNull Editor editor, 
                                            @NotNull PsiFile file) 
  {
    super(file.getLanguage(), settings, settings.clone());
    myProject = project;
    myEditor = editor;
    myFile = file;
    ensureTabs();
  }

  @Override
  protected String getPreviewText() {
    return null;
  }

  @Override
  protected void updatePreview(boolean useDefaultSample) {
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

      try {
        CodeStyleSettingsManager.getInstance(myProject).setTemporarySettings(clone);
        reformatRange(myFile, getSelectedRange());
      }
      finally {
        CodeStyleSettingsManager.getInstance(myProject).dropTemporarySettings();
      }
    }
  }

  private static void reformatRange(final @NotNull PsiFile file, final @NotNull TextRange range) {
    final Project project = file.getProject();
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            CodeStyleManager.getInstance(project).reformatText(file, range.getStartOffset(), range.getEndOffset());
          }
        });
      }
    }, "Reformat", null);
  }

  private void customizeSettingsForSelectedFragment(@NotNull CodeStyleSettingsCustomizable customizable, 
                                                    @NotNull LanguageCodeStyleSettingsProvider.SettingsType type) 
  {
    LanguageCodeStyleSettingsProvider provider = LanguageCodeStyleSettingsProvider.forLanguage(getDefaultLanguage());
    if (provider != null) {
      provider.customizeSettingsForCodeFragment(customizable, type, myFile, getSelectedRange());
    }
  }
  
  @NotNull
  private TextRange getSelectedRange() {
    SelectionModel model = myEditor.getSelectionModel();
    int start = model.getSelectionStart();
    int end = model.getSelectionEnd();
    return TextRange.create(start, end);
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
      customizeSettingsForSelectedFragment(this, getSettingsType());
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

      isFirstUpdate = false;
    }
    
    @Override
    public JComponent getPanel() {
      return myPanel;
    }

    @Override
    protected String getPreviewText() {
      return null;
    }
  }

  private class WrappingAndBracesPanelWithoutPreview extends MyWrappingAndBracesPanel {
    public JPanel myPanel;

    public WrappingAndBracesPanelWithoutPreview(CodeStyleSettings settings) {
      super(settings);
    }

    @Override
    protected void init() {
      customizeSettingsForSelectedFragment(this, getSettingsType());
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

      //todo why without it options are disabled ?
      //todo why on combo value change - nothing happens?
      customizeSettingsForSelectedFragment(this, getSettingsType());

      isFirstUpdate = false;
    }

    @Override
    public JComponent getPanel() {
      return myPanel;
    }
    
    @Override
    protected void somethingChanged() {
      reformatSelectedTextWithNewSettings();
    }
    
    @Override
    protected String getPreviewText() {
      return null;
    }
  }
}
