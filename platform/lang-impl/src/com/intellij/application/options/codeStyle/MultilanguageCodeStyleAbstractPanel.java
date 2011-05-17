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
import com.intellij.lang.Language;
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
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Base class for code style settings panels supporting multiple programming languages.
 *
 * @author rvishnyakov
 */
public abstract class MultilanguageCodeStyleAbstractPanel extends CodeStyleAbstractPanel implements CodeStyleSettingsCustomizable {
  private static final Logger LOG = Logger.getInstance("com.intellij.application.options.codeStyle.MultilanguageCodeStyleAbstractPanel");

  private Language myLanguage;
  private int myLangSelectionIndex;
  private JTabbedPane tabbedPane;

  protected MultilanguageCodeStyleAbstractPanel(CodeStyleSettings settings) {
    super(settings);
  }

  protected void init() {
    for (LanguageCodeStyleSettingsProvider provider : Extensions.getExtensions(LanguageCodeStyleSettingsProvider.EP_NAME)) {
      provider.customizeSettings(this, getSettingsType());
    }
  }

  public boolean setPanelLanguage(Language language) {

    boolean languageProviderFound = false;
    for (LanguageCodeStyleSettingsProvider provider : Extensions.getExtensions(LanguageCodeStyleSettingsProvider.EP_NAME)) {
      if (provider.getLanguage().is(language)) {
        provider.customizeSettings(this, getSettingsType());
        languageProviderFound = true;
        break;
      }
    }
    if (!languageProviderFound) return false;

    myLanguage = language;

    setSkipPreviewHighlighting(true);
    try {
      onLanguageChange(language);
    }
    finally {
      setSkipPreviewHighlighting(false);
    }
    updatePreview();
    return true;
  }

  public Language getSelectedLanguage() {
    return myLanguage;
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

  @Override
  protected PsiFile createFileFromText(final Project project, final String text) {
    final PsiFile file = LanguageCodeStyleSettingsProvider.createFileFromText(myLanguage, project, text);
    return file != null ? file : super.createFileFromText(project, text);
  }

  @Override
  protected int getRightMargin() {
    if (myLanguage == null) return -1;
    return LanguageCodeStyleSettingsProvider.getRightMargin(myLanguage, getSettingsType());
  }

  @Override
  protected String getFileExt() {
    String fileExt = LanguageCodeStyleSettingsProvider.getFileExt(myLanguage);
    if (fileExt != null) return fileExt;
    return super.getFileExt();
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
    if (getFileType() instanceof LanguageFileType) {
      return ((LanguageFileType)getFileType()).getEditorHighlighter(getCurrentProject(), null, scheme);
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
        tabbedPane.addTab(getTabName(lang), createDummy());
      }
      tabbedPane.setComponentAt(0, getEditor().getComponent());
      myLangSelectionIndex = 0;
      if (myLanguage == null) {
        setPanelLanguage(langs[0]);
      }
      else {
        updatePreview();
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
      updatePreview();
    }
  }

  private void selectCurrentLanguageTab() {
    for (int i = 0; i < tabbedPane.getTabCount(); i++) {
      if (getTabName(myLanguage).equals(tabbedPane.getTitleAt(i))) {
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
    if (lang != null && getLanguageSelector() != null) {
      getLanguageSelector().setLanguage(lang);
    }
  }


  private static JComponent createDummy() {
    return new JLabel("");
  }

  private static String getTabName(Language language) {
    String tabName = LanguageCodeStyleSettingsProvider.getLanguageName(language);
    if (tabName == null) tabName = language.getDisplayName();
    return tabName;
  }

  @Override
  public Language getDefaultLanguage() {
    return getSelectedLanguage();
  }

  protected <T extends OrderedOption>List<T> sortOptions(Collection<T> options) {
    Set<String> names = new THashSet<String>(ContainerUtil.map(options, new Function<OrderedOption, String>() {
      @Override
      public String fun(OrderedOption option) {
        return option.getOptionName();
      }
    }));

    List<T> order = new ArrayList<T>(options.size());
    MultiMap<String, T> afters = new MultiMap<String, T>();
    MultiMap<String, T> befores = new MultiMap<String, T>();

    for (T each : options) {
        String anchorOptionName = each.getAnchorOptionName();
        if (anchorOptionName != null && names.contains(anchorOptionName)) {
          if (each.getAnchor() == OptionAnchor.AFTER) {
            afters.putValue(anchorOptionName, each);
            continue;
          }
          else if (each.getAnchor() == OptionAnchor.BEFORE) {
            befores.putValue(anchorOptionName, each);
            continue;
          }
        }
      order.add(each);
    }

    List<T> result = new ArrayList<T>(options.size());
    for (T each : order) {
      result.addAll(befores.get(each.getOptionName()));
      result.add(each);
      result.addAll(afters.get(each.getOptionName()));
    }

    assert result.size() == options.size();
    return result;
  }

  protected abstract static class OrderedOption {
    @NotNull private final String optionName;
    @Nullable private final OptionAnchor anchor;
    @Nullable private final String anchorOptionName;

    protected OrderedOption(@NotNull String optionName,
                            OptionAnchor anchor,
                            String anchorOptionName) {
      this.optionName = optionName;
      this.anchor = anchor;
      this.anchorOptionName = anchorOptionName;
    }

    @NotNull
    public String getOptionName() {
      return optionName;
    }

    @Nullable
    public OptionAnchor getAnchor() {
      return anchor;
    }

    @Nullable
    public String getAnchorOptionName() {
      return anchorOptionName;
    }
  }
}
