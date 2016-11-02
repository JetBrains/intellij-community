/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.hints.settings;

import com.intellij.codeInsight.hints.InlayParameterHintsExtension;
import com.intellij.codeInsight.hints.InlayParameterHintsProvider;
import com.intellij.codeInsight.hints.filtering.MatcherConstructor;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.util.containers.ContainerUtil;
import org.jdesktop.swingx.combobox.ListComboBoxModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ParameterNameHintsConfigurable extends DialogWrapper {
  public JPanel myConfigurable;
  private EditorTextField myEditorTextField;
  private ComboBox<Language> myCurrentLanguageCombo;

  private final Language myInitiallySelectedLanguage;
  private final String myNewPreselectedItem;
  private final Project myProject;

  private final Map<Language, String> myBlackLists;

  public ParameterNameHintsConfigurable(@NotNull Project project,
                                        @NotNull Language selectedLanguage,
                                        @Nullable String newPreselectedPattern) {
    super(project);
    myProject = project;
    myInitiallySelectedLanguage = selectedLanguage;

    myNewPreselectedItem = newPreselectedPattern;
    myBlackLists = ContainerUtil.newHashMap();
    
    setTitle("Configure Parameter Name Hints Blacklist");
    init();
  }

  private void updateOkEnabled() {
    String text = myEditorTextField.getText();
    List<String> rules = StringUtil.split(text, "\n");
    boolean hasAnyInvalid = rules
      .stream()
      .filter((e) -> !e.trim().isEmpty())
      .map((s) -> MatcherConstructor.INSTANCE.createMatcher(s))
      .anyMatch((e) -> e == null);
    
    getOKAction().setEnabled(!hasAnyInvalid);
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();

    Language language = (Language)myCurrentLanguageCombo.getModel().getSelectedItem();
    myBlackLists.put(language, myEditorTextField.getText());

    myBlackLists.entrySet().forEach((entry) -> {
      Language lang = entry.getKey();
      String text = entry.getValue();
      storeBlackListDiff(lang, text);
    });
  }

  private static void storeBlackListDiff(@NotNull Language language, @NotNull String text) {
    Set<String> updatedBlackList = StringUtil
      .split(text, "\n")
      .stream()
      .filter((e) -> !e.trim().isEmpty())
      .collect(Collectors.toSet());

    InlayParameterHintsProvider provider = InlayParameterHintsExtension.INSTANCE.forLanguage(language);
    Set<String> defaultBlackList = provider.getDefaultBlackList();
    Diff diff = Diff.Builder.build(defaultBlackList, updatedBlackList);
    ParameterNameHintsSettings.getInstance().setBlackListDiff(language, diff);
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myConfigurable;
  }

  private void createUIComponents() {
    List<Language> languages = getBaseLanguagesWithProviders();

    Language selected = myInitiallySelectedLanguage;
    if (selected == null) {
      selected = languages.get(0);
    }

    String text = getLanguageBlackList(selected);
    myEditorTextField = createEditor(text, myNewPreselectedItem);
    myEditorTextField.addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        updateOkEnabled();
      }
    });

    initLanguageCombo(languages, selected);
  }

  private void initLanguageCombo(List<Language> languages, Language selected) {
    ListComboBoxModel<Language> model = new ListComboBoxModel<>(languages);
    
    myCurrentLanguageCombo = new ComboBox<>(model);
    myCurrentLanguageCombo.setSelectedItem(selected);
    myCurrentLanguageCombo.setRenderer(new ListCellRendererWrapper<Language>() {
      @Override
      public void customize(JList list, Language value, int index, boolean selected, boolean hasFocus) {
        setText(value.getDisplayName());
      }
    });

    myCurrentLanguageCombo.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        Language language = (Language)e.getItem();
        if (e.getStateChange() == ItemEvent.DESELECTED) {
          myBlackLists.put(language, myEditorTextField.getText());
        }
        else if (e.getStateChange() == ItemEvent.SELECTED) {
          String text = myBlackLists.get(language);
          if (text == null) {
            text = getLanguageBlackList(language);
          }
          myEditorTextField.setText(text);
        }
      }
    });
  }

  @NotNull
  private static String getLanguageBlackList(@NotNull Language language) {
    InlayParameterHintsProvider hintsProvider = InlayParameterHintsExtension.INSTANCE.forLanguage(language);
    if (hintsProvider == null) {
      return "";
    }
    Diff diff = ParameterNameHintsSettings.getInstance().getBlackListDiff(language);
    Set<String> blackList = diff.applyOn(hintsProvider.getDefaultBlackList());
    return StringUtil.join(blackList, "\n");
  }

  @NotNull
  private static List<Language> getBaseLanguagesWithProviders() {
    return Language.getRegisteredLanguages()
      .stream()
      .filter(lang -> lang.getBaseLanguage() == null)
      .filter(lang -> InlayParameterHintsExtension.INSTANCE.forLanguage(lang) != null)
      .sorted(Comparator.comparingInt(l -> l.getDisplayName().length()))
      .collect(Collectors.toList());
  }

  private EditorTextField createEditor(@NotNull String text, @Nullable String newPreselectedItem) {
    final TextRange range;
    if (newPreselectedItem != null) {
      text += "\n";
      
      final int startOffset = text.length();
      text += newPreselectedItem;
      range = new TextRange(startOffset, text.length());
    }
    else {
      range = null;
    }

    return createEditorField(text, range);
  }

  @NotNull
  private EditorTextField createEditorField(@NotNull String text, @Nullable TextRange rangeToSelect) {
    Document document = EditorFactory.getInstance().createDocument(text);
    EditorTextField field = new EditorTextField(document, myProject, FileTypes.PLAIN_TEXT, false, false);
    field.setPreferredSize(new Dimension(200, 350));
    field.addSettingsProvider(editor -> {
      editor.setVerticalScrollbarVisible(true);
      editor.setHorizontalScrollbarVisible(true);
      editor.getSettings().setAdditionalLinesCount(2);
      if (rangeToSelect != null) {
        editor.getCaretModel().moveToOffset(rangeToSelect.getStartOffset());
        editor.getScrollingModel().scrollVertically(document.getTextLength() - 1);
        editor.getSelectionModel().setSelection(rangeToSelect.getStartOffset(), rangeToSelect.getEndOffset());
      }
    });
    return field;
  }
}
