/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.application.options;

import com.intellij.formatting.contextConfiguration.CodeFragmentSettingProvider;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.*;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Rustam Vishnyakov
 */
public class JavaCodeStyleSettingsProvider extends CodeStyleSettingsProvider implements CodeFragmentSettingProvider {
  @NotNull
  @Override
  public Configurable createSettingsPage(CodeStyleSettings settings, CodeStyleSettings originalSettings) {
    return new CodeStyleAbstractConfigurable(settings, originalSettings, "Java") {
      protected CodeStyleAbstractPanel createPanel(final CodeStyleSettings settings) {
        return new JavaCodeStyleMainPanel(getCurrentSettings(), settings);
      }
      public String getHelpTopic() {
        return "reference.settingsdialog.codestyle.java";
      }
    };
  }

  @Override
  @NotNull
  public TabbedLanguageCodeStylePanel createSettingsForSelectedFragment(final @NotNull Editor editor,
                                                                        CodeStyleSettings currentSettings,
                                                                        CodeStyleSettings settings) 
  {


    //todo may be better way will be to return only list of settings so we can show only selected ones


    return new TabbedLanguageCodeStylePanel(getLanguage(), currentSettings, settings) {
      @Override
      protected void initTabs(CodeStyleSettings settings) {

        MySpacesPanel spacesPanel = new MySpacesPanel(settings) {
          private JPanel myPanel;

          @Override
          protected void somethingChanged() {
            reformatWithNewSettings();
          }

          @Override
          protected void init() {
            //todo what this is doing?
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
          
        };


        MyWrappingAndBracesPanel bracesPanel = new MyWrappingAndBracesPanel(settings) {

          public JPanel myPanel;

          @Override
          protected void init() {
            super.init();

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
            reformatWithNewSettings();
          }
        };
        
        addTab(spacesPanel);
        addTab(bracesPanel);
        reset(getSettings());
      }

      
      private void reformatWithNewSettings() {
        final SelectionModel model = editor.getSelectionModel();
        if (model.hasSelection()) {

          try {
            apply(getSettings());
          }
          catch (ConfigurationException e) {
            //todo log!
            e.printStackTrace();
          }

          CodeStyleSettings clone = getSettings().clone();

          Project project = editor.getProject();
          if (project == null) return;
          
          try {
            CodeStyleSettingsManager.getInstance(project).setTemporarySettings(clone);
            Document document = editor.getDocument();
            PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
            if (file != null) {
              reformatSelectedFragment(model, file);
            }
          }
          finally {
            CodeStyleSettingsManager.getInstance(project).dropTemporarySettings();
          }
        }
      }

      private void reformatSelectedFragment(final SelectionModel model, final PsiFile file) {
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
    };
  }

  @Override
  public DisplayPriority getPriority() {
    return PlatformUtils.isIntelliJ() ? DisplayPriority.KEY_LANGUAGE_SETTINGS : DisplayPriority.LANGUAGE_SETTINGS;
  }

  @Override
  public String getConfigurableDisplayName() {
    return "Java";
  }

  @Nullable
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }

  @Nullable
  @Override
  public CustomCodeStyleSettings createCustomSettings(CodeStyleSettings settings) {
    return new JavaCodeStyleSettings(settings);
  }
}
