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
import com.intellij.util.containers.JBIterable;
import org.jdesktop.swingx.combobox.ListComboBoxModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ParameterNameHintsConfigurable extends DialogWrapper {
  public JPanel myConfigurable;
  private EditorTextField myEditorTextField;
  private ComboBox<Language> myCurrentLanguageCombo;

  private final Set<String> myDefaultBlackList;
  private final Language myLanguage;
  private final String myNewPreselectedItem;
  private final Project myProject;

  public ParameterNameHintsConfigurable(@NotNull Project project,
                                        @NotNull Set<String> defaultBlackList,
                                        @NotNull Language language) {
    this(project, defaultBlackList, language, null);
  }

  public ParameterNameHintsConfigurable(@NotNull Project project,
                                        @NotNull Set<String> defaultBlackList,
                                        @NotNull Language language,
                                        @Nullable String newPreselectedPattern) {
    super(project);
    myProject = project;
    myLanguage = language;
    myDefaultBlackList = defaultBlackList;
    myNewPreselectedItem = newPreselectedPattern;
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

    Set<String> updatedBlackList = StringUtil
      .split(myEditorTextField.getText(), "\n")
      .stream()
      .filter((e) -> !e.trim().isEmpty())
      .collect(Collectors.toSet());
    
    Diff diff = Diff.Builder.build(myDefaultBlackList, updatedBlackList);
    ParameterNameHintsSettings.getInstance().setBlackListDiff(myLanguage, diff);
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myConfigurable;
  }

  private void createUIComponents() {
    Diff diff = ParameterNameHintsSettings.getInstance().getBlackListDiff(myLanguage);
    Set<String> blacklist = diff.applyOn(myDefaultBlackList);
    
    myEditorTextField = createEditor(blacklist, myNewPreselectedItem);
    myEditorTextField.addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        updateOkEnabled();
      }
    });
    
    Collection<Language> allLanguages = Language.getRegisteredLanguages();
    JBIterable<Language> languagesWithHintsSupport = JBIterable.from(allLanguages)
      .filter((lang) -> !InlayParameterHintsExtension.INSTANCE.forKey(lang).isEmpty());

    ListComboBoxModel<Language> model = new ListComboBoxModel<>(languagesWithHintsSupport.toList());
    myCurrentLanguageCombo = new ComboBox<>(model);
    myCurrentLanguageCombo.setRenderer(new ListCellRendererWrapper<Language>() {
      @Override
      public void customize(JList list, Language value, int index, boolean selected, boolean hasFocus) {
        setText(value.getDisplayName());
      }
    });
  }

  private EditorTextField createEditor(@NotNull Set<String> blacklist, @Nullable String newPreselectedItem) {
    String text = StringUtil.join(blacklist, "\n");
    
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
