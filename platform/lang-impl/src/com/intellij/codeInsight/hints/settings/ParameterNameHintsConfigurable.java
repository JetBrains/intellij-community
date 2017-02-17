/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.codeInsight.hints.Option;
import com.intellij.codeInsight.hints.filtering.MatcherConstructor;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jdesktop.swingx.combobox.ListComboBoxModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.intellij.openapi.editor.colors.CodeInsightColors.ERRORS_ATTRIBUTES;

public class ParameterNameHintsConfigurable extends DialogWrapper {

  public JPanel myConfigurable;
  private EditorTextField myEditorTextField;
  private ComboBox<Language> myCurrentLanguageCombo;

  private JPanel myOptionsPanel;
  private JPanel myBlacklistPanel;
  
  private Map<Language, String> myBlackLists = ContainerUtil.newHashMap();
  private Map<Option, JBCheckBox> myOptions;
  
  private CardLayout myOptionsCardLayout;

  public ParameterNameHintsConfigurable() {
    this(null, null);
  }
  
  public ParameterNameHintsConfigurable(@Nullable Language selectedLanguage,
                                        @Nullable String newPreselectedPattern) {
    super(null);

    setTitle("Configure Parameter Name Hints");
    init();

    myOptionsPanel.setVisible(true);
    myBlacklistPanel.setBorder(IdeBorderFactory.createTitledBorder("Blacklist"));

    if (selectedLanguage != null) {
      myCurrentLanguageCombo.setSelectedItem(selectedLanguage);
      showLanguageOptions(selectedLanguage);
      if (newPreselectedPattern != null) {
        addSelectedText(newPreselectedPattern);
      }
    }
  }

  private void addSelectedText(@Nullable String newPreselectedPattern) {
    String text = myEditorTextField.getText();
    final int startOffset = text.length();
    text += "\n" + newPreselectedPattern;
    final int endOffset = text.length();
    
    myEditorTextField.setText(text);
    myEditorTextField.addSettingsProvider((editor) -> {
      SelectionModel model = editor.getSelectionModel();
      model.setSelection(startOffset + 1, endOffset);
    });
  }

  private void updateOkEnabled() {
    String text = myEditorTextField.getText();
    List<String> rules = StringUtil.split(text, "\n", true, false);

    Stream<Integer> intStream = IntStream.iterate(0, i -> i + 1).boxed();
    List<Integer> invalidLines = StreamEx.of(rules)
      .zipWith(intStream)
      .filterKeys((rule) -> !rule.isEmpty())
      .mapKeys((rule) -> MatcherConstructor.INSTANCE.createMatcher(rule))
      .filterKeys((matcher) -> matcher == null)
      .values()
      .toList();

    getOKAction().setEnabled(invalidLines.isEmpty());
    highlightErrorLines(invalidLines);
  }

  private void highlightErrorLines(@NotNull List<Integer> lines) {
    Editor editor = myEditorTextField.getEditor();
    if (editor == null) return;

    final TextAttributes attributes = editor.getColorsScheme().getAttributes(ERRORS_ATTRIBUTES);
    final Document document = editor.getDocument();
    final int totalLines = document.getLineCount();

    MarkupModel model = editor.getMarkupModel();
    model.removeAllHighlighters();
    lines.stream()
      .filter((current) -> current < totalLines)
      .forEach((line) -> model.addLineHighlighter(line, HighlighterLayer.ERROR, attributes));
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();

    Language language = (Language)myCurrentLanguageCombo.getModel().getSelectedItem();
    myBlackLists.put(language, myEditorTextField.getText());

    myBlackLists.forEach((lang, text) -> storeBlackListDiff(lang, text));
    myOptions.forEach((option, checkBox) -> option.set(checkBox.isSelected()));
  }

  private static void storeBlackListDiff(@NotNull Language language, @NotNull String text) {
    Set<String> updatedBlackList = StringUtil
      .split(text, "\n")
      .stream()
      .filter((e) -> !e.trim().isEmpty())
      .collect(Collectors.toCollection(LinkedHashSet::new));

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
    List<Language> allLanguages = getBaseLanguagesWithProviders();
    Language selected = allLanguages.get(0);

    String text = getLanguageBlackList(selected);
    myEditorTextField = createEditorField(text);
    myEditorTextField.addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        updateOkEnabled();
      }
    });

    initLanguageCombo(selected, allLanguages);
    createOptionsPanel(selected, allLanguages);
  }

  private void createOptionsPanel(final Language selected,
                                  final List<Language> allLanguages) {
    myOptionsCardLayout = new CardLayout();
    myOptionsPanel = new JPanel();
    myOptionsPanel.setLayout(myOptionsCardLayout);
    myOptions = ContainerUtil.newHashMap();

    allLanguages.forEach(language -> {
      final List<Option> options = getOptions(language);

      final JPanel languagePanel = new JPanel();
      final BoxLayout boxLayout = new BoxLayout(languagePanel, BoxLayout.Y_AXIS);
      languagePanel.setLayout(boxLayout);

      if (!options.isEmpty()) {
        languagePanel.setBorder(IdeBorderFactory.createTitledBorder("Options"));
      }

      for (Option option : options) {
        JBCheckBox box = new JBCheckBox(option.getName(), option.get());
        myOptions.put(option, box);
        languagePanel.add(box);
      }
      
      myOptionsPanel.add(language.getDisplayName(), languagePanel);
    });
    
    myOptionsCardLayout.show(myOptionsPanel, selected.getDisplayName());
  }

  private void initLanguageCombo(Language selected, List<Language> languages) {
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
          deselectLanguage(language);
        }
        else if (e.getStateChange() == ItemEvent.SELECTED) {
          showLanguageOptions(language);
        }
      }
    });
  }

  private void deselectLanguage(Language language) {
    myBlackLists.put(language, myEditorTextField.getText());
  }

  private void showLanguageOptions(Language language) {
    String text = myBlackLists.get(language);
    if (text == null) {
      text = getLanguageBlackList(language);
    }
    myEditorTextField.setText(text);
    myOptionsCardLayout.show(myOptionsPanel, language.getDisplayName());
  }

  private static List<Option> getOptions(Language language) {
    InlayParameterHintsProvider provider = InlayParameterHintsExtension.INSTANCE.forLanguage(language);
    if (provider != null) {
      return provider.getSupportedOptions();
    }
    return ContainerUtil.emptyList();
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
  
  @NotNull
  private static EditorTextField createEditorField(@NotNull String text) {
    Document document = EditorFactory.getInstance().createDocument(text);
    EditorTextField field = new EditorTextField(document, null, FileTypes.PLAIN_TEXT, false, false);
    field.setPreferredSize(new Dimension(200, 350));
    field.addSettingsProvider(editor -> {
      editor.setVerticalScrollbarVisible(true);
      editor.setHorizontalScrollbarVisible(true);
      editor.getSettings().setAdditionalLinesCount(2);
    });
    return field;
  }
}
