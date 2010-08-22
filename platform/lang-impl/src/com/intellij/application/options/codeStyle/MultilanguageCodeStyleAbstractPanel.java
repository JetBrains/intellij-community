/*
 * Copyright 2010 JetBrains s.r.o.
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
package com.intellij.application.options.codeStyle;

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.ide.DataManager;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

/**
 * Base class for code style settings panels supporting multiple programming languages.
 *
 * @author rvishnyakov
 */
public abstract class MultilanguageCodeStyleAbstractPanel extends CodeStyleAbstractPanel implements CodeStyleSettingsCustomizable {
  private static final Logger LOG = Logger.getInstance("com.intellij.application.options.codeStyle.MultilanguageCodeStyleAbstractPanel");

  private Language myLanguage;
  private LanguageSelector myLanguageSelector;
  private int myLangSelectionIndex;
  private JTabbedPane tabbedPane;

  protected MultilanguageCodeStyleAbstractPanel(CodeStyleSettings settings) {
    super(settings);
  }

  protected void init() {
    for(LanguageCodeStyleSettingsProvider provider: Extensions.getExtensions(LanguageCodeStyleSettingsProvider.EP_NAME)) {
      provider.customizeSettings(this, getSettingsType());
    }
  }

  public void setLanguageSelector(LanguageSelector langSelector) {
    myLanguageSelector = langSelector;
    setPanelLanguage(langSelector.getLanguage());
  }

  public void setPanelLanguage(Language language) {
    myLanguage = language;
    updatePreviewEditor();

    for(LanguageCodeStyleSettingsProvider provider: Extensions.getExtensions(LanguageCodeStyleSettingsProvider.EP_NAME)) {
      if (provider.getLanguage().is(language)) {
        provider.customizeSettings(this, getSettingsType());
      }
    }
    onLanguageChange(language);
  }

  protected abstract LanguageCodeStyleSettingsProvider.SettingsType getSettingsType();

  protected void onLanguageChange(Language language) {
  }

  @Override
  protected String getPreviewText() {
    if (myLanguage == null) return "";
    String sample = LanguageCodeStyleSettingsProvider.getCodeSample(myLanguage, getSettingsType());
    if (sample == null) return "";
    return sample;
  }

  @NotNull
  @Override
  protected FileType getFileType() {
    if (myLanguage != null) {
      FileType assocType = myLanguage.getAssociatedFileType();
      if (assocType != null) {
        return assocType;
      }
    }
    Language[] langs = LanguageCodeStyleSettingsProvider.getLanguagesWithCodeStyleSettings();
    if (langs.length > 0) {
      myLanguage = langs[0];
      FileType type = langs[0].getAssociatedFileType();
      if (type != null) return type;
    }
    return StdFileTypes.JAVA;
  }

  @Nullable
  protected EditorHighlighter createHighlighter(final EditorColorsScheme scheme) {
    Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(this.getPanel()));
    if (project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }
    if (getFileType() instanceof LanguageFileType) {
      return ((LanguageFileType)getFileType()).getEditorHighlighter(project, null, scheme);
    }
    return null;
  }


  @Override
  protected PsiFile doReformat(final Project project, final PsiFile psiFile) {
    final String text = psiFile.getText();
    final PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
    final Document doc = manager.getDocument(psiFile);
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            doc.replaceString(0, doc.getTextLength(), text);
            manager.commitDocument(doc);
            try {
              CodeStyleManager.getInstance(project).reformat(psiFile);
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        });
      }
    }, "", "");
    if (doc != null) {
      manager.commitDocument(doc);
    }
    return psiFile;
  }

  protected static JPanel createPreviewPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(IdeBorderFactory.createTitledBorder("Preview"));
    panel.setPreferredSize(new Dimension(200, 0));
    return panel;
  }


  @Override
  protected void installPreviewPanel(final JPanel previewPanel) {
    if (getSettingsType() != LanguageCodeStyleSettingsProvider.SettingsType.LANGUAGE_SPECIFIC) {
      tabbedPane = new JTabbedPane();
      tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
      Language[] langs = LanguageCodeStyleSettingsProvider.getLanguagesWithCodeStyleSettings();
      if (langs.length == 0) return;
      for (Language lang : langs) {
        tabbedPane.addTab(lang.getDisplayName(), createDummy());
      }
      tabbedPane.setComponentAt(0, getEditor().getComponent());
      myLangSelectionIndex = 0;
      if (myLanguage == null) {
        setPanelLanguage(langs[0]);
      }
      else {
        updatePreviewEditor();
      }
      tabbedPane.addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          onTabSelection((JTabbedPane)e.getSource());
        }
      });
      previewPanel.add(tabbedPane, BorderLayout.CENTER);
      previewPanel.addAncestorListener(new AncestorListener() {
        public void ancestorAdded(AncestorEvent event) {
          selectCurrentLanguageTab();
        }

        public void ancestorRemoved(AncestorEvent event) {
          // Do nothing
        }

        public void ancestorMoved(AncestorEvent event) {
          // Do nothing
        }
      });
    }
    else {
      // If settings are language-specific
      previewPanel.add(getEditor().getComponent(), BorderLayout.CENTER);
      updatePreviewEditor();
    }
  }

  private void selectCurrentLanguageTab() {
    for(int i = 0; i < tabbedPane.getTabCount(); i ++) {
      if (myLanguage.getDisplayName().equals(tabbedPane.getTitleAt(i))) {
        tabbedPane.setSelectedIndex(i);
        return;
      }
    }
  }

  private void onTabSelection(JTabbedPane tabs) {
    int i = tabs.getSelectedIndex();
    tabs.setComponentAt(myLangSelectionIndex, createDummy());
    tabs.setComponentAt(i, getEditor().getComponent());
    myLangSelectionIndex = i;
    String selectionTitle = tabs.getTitleAt(i);
    Language lang = LanguageCodeStyleSettingsProvider.getLanguage(selectionTitle);
    if (lang != null && myLanguageSelector != null) {
      myLanguageSelector.setLanguage(lang);
    }
  }


  private static JComponent createDummy() {
    return new JLabel("");
  }
}
