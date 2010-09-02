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

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.AbstractConfigurableEP;
import com.intellij.openapi.options.CompositeConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;

/**
 * To provide an additional settings editor register implementation of {@link com.intellij.openapi.options.UnnamedConfigurable} in the plugin.xml:
 * <p/>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;<br>
 * &nbsp;&nbsp;&lt;editorSmartKeysConfigurable instance="class-name"/&gt;<br>
 * &lt;/extensions&gt;
 * <p>
 * A new instance of the specified class will be created each time then the Settings dialog is opened
 *
 * @author yole
 */
public class EditorSmartKeysConfigurable extends CompositeConfigurable<UnnamedConfigurable> implements EditorOptionsProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.application.options.editor.EditorSmartKeysConfigurable");
  private static final ExtensionPointName<EditorSmartKeysConfigurableEP> EP_NAME = ExtensionPointName.create("com.intellij.editorSmartKeysConfigurable");

  private JCheckBox myCbSmartHome;
  private JCheckBox myCbSmartEnd;
  private JCheckBox myCbInsertPairBracket;
  private JCheckBox myCbInsertPairQuote;
  private JCheckBox myCbCamelWords;
  private JCheckBox myCbSmartIndentOnEnter;
  private JComboBox myReformatOnPasteCombo;
  private JPanel myRootPanel;
  private JPanel myAddonPanel;
  private JCheckBox myCbInsertPairCurlyBraceOnEnter;
  private JCheckBox myCbInsertJavadocStubOnEnter;

  private static final String NO_REFORMAT = ApplicationBundle.message("combobox.paste.reformat.none");
  private static final String INDENT_BLOCK = ApplicationBundle.message("combobox.paste.reformat.indent.block");
  private static final String INDENT_EACH_LINE = ApplicationBundle.message("combobox.paste.reformat.indent.each.line");
  private static final String REFORMAT_BLOCK = ApplicationBundle.message("combobox.paste.reformat.reformat.block");

  public EditorSmartKeysConfigurable() {
    myReformatOnPasteCombo.addItem(NO_REFORMAT);
    myReformatOnPasteCombo.addItem(INDENT_BLOCK);
    myReformatOnPasteCombo.addItem(INDENT_EACH_LINE);
    myReformatOnPasteCombo.addItem(REFORMAT_BLOCK);

    myCbInsertJavadocStubOnEnter.setVisible(hasAnyDocAwareCommenters());
  }

  private static boolean hasAnyDocAwareCommenters() {
    final Collection<Language> languages = Language.getRegisteredLanguages();
    for (Language language : languages) {
      final Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(language);
      if (commenter instanceof CodeDocumentationAwareCommenter) {
        final CodeDocumentationAwareCommenter docCommenter = (CodeDocumentationAwareCommenter)commenter;
        if (docCommenter.getDocumentationCommentLinePrefix() != null) {
          return true;
        }
      }
    }
    return false;
  }

  protected List<UnnamedConfigurable> createConfigurables() {
    return AbstractConfigurableEP.createConfigurables(EP_NAME);
  }

  @Nls
  public String getDisplayName() {
    return "Smart Keys";
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return "reference.settingsdialog.IDE.editor.smartkey";
  }

  public JComponent createComponent() {
    for (UnnamedConfigurable provider : getConfigurables()) {
      myAddonPanel.add(provider.createComponent(), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0, 0, GridBagConstraints.NORTHWEST,
                                                                            GridBagConstraints.NONE, new Insets(0,0,0,0), 0,0));
    }
    return myRootPanel;
  }

  @Override
  public void reset() {
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();

    // Paste
    switch(codeInsightSettings.REFORMAT_ON_PASTE){
      case CodeInsightSettings.NO_REFORMAT:
        myReformatOnPasteCombo.setSelectedItem(NO_REFORMAT);
      break;

      case CodeInsightSettings.INDENT_BLOCK:
        myReformatOnPasteCombo.setSelectedItem(INDENT_BLOCK);
      break;

      case CodeInsightSettings.INDENT_EACH_LINE:
        myReformatOnPasteCombo.setSelectedItem(INDENT_EACH_LINE);
      break;

      case CodeInsightSettings.REFORMAT_BLOCK:
        myReformatOnPasteCombo.setSelectedItem(REFORMAT_BLOCK);
      break;
    }

    myCbSmartHome.setSelected(editorSettings.isSmartHome());
    myCbSmartEnd.setSelected(codeInsightSettings.SMART_END_ACTION);

    myCbSmartIndentOnEnter.setSelected(codeInsightSettings.SMART_INDENT_ON_ENTER);
    myCbInsertPairCurlyBraceOnEnter.setSelected(codeInsightSettings.INSERT_BRACE_ON_ENTER);
    myCbInsertJavadocStubOnEnter.setSelected(codeInsightSettings.JAVADOC_STUB_ON_ENTER);

    myCbInsertPairBracket.setSelected(codeInsightSettings.AUTOINSERT_PAIR_BRACKET);
    myCbInsertPairQuote.setSelected(codeInsightSettings.AUTOINSERT_PAIR_QUOTE);
    myCbCamelWords.setSelected(editorSettings.isCamelWords());

    super.reset();
  }

  @Override
  public void apply() throws ConfigurationException {
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();

    editorSettings.setSmartHome(myCbSmartHome.isSelected());
    codeInsightSettings.SMART_END_ACTION = myCbSmartEnd.isSelected();
    codeInsightSettings.SMART_INDENT_ON_ENTER = myCbSmartIndentOnEnter.isSelected();
    codeInsightSettings.INSERT_BRACE_ON_ENTER = myCbInsertPairCurlyBraceOnEnter.isSelected();
    codeInsightSettings.JAVADOC_STUB_ON_ENTER = myCbInsertJavadocStubOnEnter.isSelected();
    codeInsightSettings.AUTOINSERT_PAIR_BRACKET = myCbInsertPairBracket.isSelected();
    codeInsightSettings.AUTOINSERT_PAIR_QUOTE = myCbInsertPairQuote.isSelected();
    editorSettings.setCamelWords(myCbCamelWords.isSelected());
    codeInsightSettings.REFORMAT_ON_PASTE = getReformatPastedBlockValue();

    super.apply();
  }

  @Override
  public boolean isModified() {
    if (super.isModified()) return true;
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();

    boolean isModified = getReformatPastedBlockValue() != codeInsightSettings.REFORMAT_ON_PASTE;
    isModified |= isModified(myCbSmartHome, editorSettings.isSmartHome());
    isModified |= isModified(myCbSmartEnd, codeInsightSettings.SMART_END_ACTION);

    isModified |= isModified(myCbSmartIndentOnEnter, codeInsightSettings.SMART_INDENT_ON_ENTER);
    isModified |= isModified(myCbInsertPairCurlyBraceOnEnter, codeInsightSettings.INSERT_BRACE_ON_ENTER);
    isModified |= isModified(myCbInsertJavadocStubOnEnter, codeInsightSettings.JAVADOC_STUB_ON_ENTER);

    isModified |= isModified(myCbInsertPairBracket, codeInsightSettings.AUTOINSERT_PAIR_BRACKET);
    isModified |= isModified(myCbInsertPairQuote, codeInsightSettings.AUTOINSERT_PAIR_QUOTE);
    isModified |= isModified(myCbCamelWords, editorSettings.isCamelWords());

    return isModified;

  }

  private static boolean isModified(JToggleButton checkBox, boolean value) {
    return checkBox.isSelected() != value;
  }

  private int getReformatPastedBlockValue(){
    Object selectedItem = myReformatOnPasteCombo.getSelectedItem();
    if (NO_REFORMAT.equals(selectedItem)){
      return CodeInsightSettings.NO_REFORMAT;
    }
    else if (INDENT_BLOCK.equals(selectedItem)){
      return CodeInsightSettings.INDENT_BLOCK;
    }
    else if (INDENT_EACH_LINE.equals(selectedItem)){
      return CodeInsightSettings.INDENT_EACH_LINE;
    }
    else if (REFORMAT_BLOCK.equals(selectedItem)){
      return CodeInsightSettings.REFORMAT_BLOCK;
    }
    else{
      LOG.assertTrue(false);
      return -1;
    }
  }

  public String getId() {
    return "editor.preferences.smartKeys";
  }

  public Runnable enableSearch(final String option) {
    return null;
  }
}
