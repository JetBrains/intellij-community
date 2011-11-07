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

package com.intellij.application.options;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.*;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.util.*;
import java.util.List;

public class GeneralCodeStylePanel extends CodeStyleAbstractPanel {
  @SuppressWarnings("UnusedDeclaration")
  private static final Logger LOG = Logger.getInstance("#com.intellij.application.options.GeneralCodeStylePanel");

  private static final String SYSTEM_DEPENDANT_STRING = ApplicationBundle.message("combobox.crlf.system.dependent");
  private static final String UNIX_STRING = ApplicationBundle.message("combobox.crlf.unix");
  private static final String WINDOWS_STRING = ApplicationBundle.message("combobox.crlf.windows");
  private static final String MACINTOSH_STRING = ApplicationBundle.message("combobox.crlf.mac");


  private JTextField myRightMarginField;
  private JComboBox myLineSeparatorCombo;
  private JPanel myPanel;
  private JCheckBox myCbWrapWhenTypingReachesRightMargin;
  private JPanel myDefaultIndentOptionsPanel;
  private int myRightMargin;
  private SmartIndentOptionsEditor myIndentOptionsEditor;


  public GeneralCodeStylePanel(CodeStyleSettings settings) {
    super(settings);

    final List<FileTypeIndentOptionsProvider> indentOptionsProviders =
      Arrays.asList(Extensions.getExtensions(FileTypeIndentOptionsProvider.EP_NAME));
    Collections.sort(indentOptionsProviders, new Comparator<FileTypeIndentOptionsProvider>() {
      @Override
      public int compare(FileTypeIndentOptionsProvider p1, FileTypeIndentOptionsProvider p2) {
        Language lang1 = getLanguage(p1.getFileType());
        if (lang1 == null) return -1;
        Language lang2 = getLanguage(p2.getFileType());
        if (lang2 == null) return 1;
        DisplayPriority priority1 = LanguageCodeStyleSettingsProvider.getDisplayPriority(lang1);
        DisplayPriority priority2 = LanguageCodeStyleSettingsProvider.getDisplayPriority(lang2);
        if (priority1.equals(priority2)) {
          return lang1.getDisplayName().compareTo(lang2.getDisplayName());
        }
        return priority1.compareTo(priority2);
      }
    });

    myLineSeparatorCombo.addItem(SYSTEM_DEPENDANT_STRING);
    myLineSeparatorCombo.addItem(UNIX_STRING);
    myLineSeparatorCombo.addItem(WINDOWS_STRING);
    myLineSeparatorCombo.addItem(MACINTOSH_STRING);
    addPanelToWatch(myPanel);

    myRightMargin = settings.RIGHT_MARGIN;

    myRightMarginField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        int valueFromControl = getRightMarginImpl();
        if (valueFromControl > 0) {
          myRightMargin = valueFromControl;
        }
      }
    });

    myIndentOptionsEditor = new SmartIndentOptionsEditor();
    myDefaultIndentOptionsPanel.add(myIndentOptionsEditor.createPanel(), BorderLayout.CENTER);
  }

  @Nullable
  private static Language getLanguage(FileType fileType) {
    return (fileType instanceof LanguageFileType) ? ((LanguageFileType)fileType).getLanguage() : null;
  }


  protected void somethingChanged() {
    super.somethingChanged();
  }


  protected int getRightMargin() {
    return myRightMargin;
  }

  @NotNull
  protected FileType getFileType() {
    return FileTypes.PLAIN_TEXT;
  }

  protected String getPreviewText() {
    return "";
  }


  public void apply(CodeStyleSettings settings) {
    settings.LINE_SEPARATOR = getSelectedLineSeparator();

    int rightMarginImpl = getRightMarginImpl();
    if (rightMarginImpl > 0) {
      settings.RIGHT_MARGIN = rightMarginImpl;
    }
    settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = myCbWrapWhenTypingReachesRightMargin.isSelected();
    myIndentOptionsEditor.setEnabled(true);
    myIndentOptionsEditor.apply(settings, settings.OTHER_INDENT_OPTIONS);
  }


  private int getRightMarginImpl() {
    if (myRightMarginField == null) return -1;
    try {
      return Integer.parseInt(myRightMarginField.getText());
    }
    catch (NumberFormatException e) {
      return -1;
    }
  }

  @Nullable
  private String getSelectedLineSeparator() {
    if (UNIX_STRING.equals(myLineSeparatorCombo.getSelectedItem())) {
      return "\n";
    }
    else if (MACINTOSH_STRING.equals(myLineSeparatorCombo.getSelectedItem())) {
      return "\r";
    }
    else if (WINDOWS_STRING.equals(myLineSeparatorCombo.getSelectedItem())) {
      return "\r\n";
    }
    return null;
  }


  public boolean isModified(CodeStyleSettings settings) {
    if (!Comparing.equal(getSelectedLineSeparator(), settings.LINE_SEPARATOR)) {
      return true;
    }

    if (settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN ^ myCbWrapWhenTypingReachesRightMargin.isSelected()) {
      return true;
    }

    if (!myRightMarginField.getText().equals(String.valueOf(settings.RIGHT_MARGIN))) return true;
    myIndentOptionsEditor.setEnabled(true);
    return myIndentOptionsEditor.isModified(settings, settings.OTHER_INDENT_OPTIONS);
  }

  public JComponent getPanel() {
    return myPanel;
  }

  protected void resetImpl(final CodeStyleSettings settings) {

    String lineSeparator = settings.LINE_SEPARATOR;
    if ("\n".equals(lineSeparator)) {
      myLineSeparatorCombo.setSelectedItem(UNIX_STRING);
    }
    else if ("\r\n".equals(lineSeparator)) {
      myLineSeparatorCombo.setSelectedItem(WINDOWS_STRING);
    }
    else if ("\r".equals(lineSeparator)) {
      myLineSeparatorCombo.setSelectedItem(MACINTOSH_STRING);
    }
    else {
      myLineSeparatorCombo.setSelectedItem(SYSTEM_DEPENDANT_STRING);
    }

    myRightMarginField.setText(String.valueOf(settings.RIGHT_MARGIN));
    myCbWrapWhenTypingReachesRightMargin.setSelected(settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN);
    myIndentOptionsEditor.reset(settings, settings.OTHER_INDENT_OPTIONS);
    myIndentOptionsEditor.setEnabled(true);
  }

  protected EditorHighlighter createHighlighter(final EditorColorsScheme scheme) {
    //noinspection NullableProblems
    return EditorHighlighterFactory.getInstance().createEditorHighlighter(getFileType(), scheme, null);
  }

  protected void prepareForReformat(final PsiFile psiFile) {
  }


  @Override
  public Language getDefaultLanguage() {
    return null;
  }
}
