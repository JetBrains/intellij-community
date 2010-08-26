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
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.FileTypeIndentOptionsProvider;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.OptionGroup;
import com.intellij.ui.TabbedPaneWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GeneralCodeStylePanel extends CodeStyleAbstractPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.application.options.GeneralCodeStylePanel");

  private static final String SYSTEM_DEPENDANT_STRING = ApplicationBundle.message("combobox.crlf.system.dependent");
  private static final String UNIX_STRING = ApplicationBundle.message("combobox.crlf.unix");
  private static final String WINDOWS_STRING = ApplicationBundle.message("combobox.crlf.windows");
  private static final String MACINTOSH_STRING = ApplicationBundle.message("combobox.crlf.mac");

  private JCheckBox myCbUseSameIndents;
  private final IndentOptionsEditor myOtherIndentOptions = new IndentOptionsEditor();

  private final Map<FileType, IndentOptionsEditor> myAdditionalIndentOptions = new LinkedHashMap<FileType, IndentOptionsEditor>();
  private final List<FileTypeIndentOptionsProvider> myIndentOptionsProviders = new ArrayList<FileTypeIndentOptionsProvider>();

  private TabbedPaneWrapper myIndentOptionsTabs;
  private JPanel myIndentPanel;
  private JPanel myPreviewPanel;
  private JTextField myRightMarginField;
  private JComboBox myLineSeparatorCombo;
  private JPanel myPanel;
  private JCheckBox myCbWrapWhenTypingReachesRightMargin;
  private int myRightMargin;
  private int myLastSelectedTab = 0;


  public GeneralCodeStylePanel(CodeStyleSettings settings) {
    super(settings);

    final FileTypeIndentOptionsProvider[] indentOptionsProviders = Extensions.getExtensions(FileTypeIndentOptionsProvider.EP_NAME);
    for (FileTypeIndentOptionsProvider indentOptionsProvider : indentOptionsProviders) {
      myIndentOptionsProviders.add(indentOptionsProvider);
      if (myAdditionalIndentOptions.containsKey(indentOptionsProvider.getFileType())) {
        LOG.error("Duplicate extension: " + indentOptionsProvider);
      }
      else {
        myAdditionalIndentOptions.put(indentOptionsProvider.getFileType(), indentOptionsProvider.createOptionsEditor());
      }
    }

    myIndentPanel.setLayout(new BorderLayout());
    myIndentPanel.add(createTabOptionsPanel(), BorderLayout.CENTER);
    installPreviewPanel(myPreviewPanel);
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
  }

  protected void somethingChanged() {
    super.somethingChanged();
    update();
  }

  private void update() {
    boolean enabled = !myCbUseSameIndents.isSelected();
    if (!enabled && myIndentOptionsTabs.getSelectedIndex() != 0) {
      myIndentOptionsTabs.setSelectedIndex(0);
    }

    int index = 0;
    for(IndentOptionsEditor options: myAdditionalIndentOptions.values()) {
      final boolean tabEnabled = enabled || index == 0;
      options.setEnabled(tabEnabled);
      myIndentOptionsTabs.setEnabledAt(index, tabEnabled);
      index++;
    }
    myOtherIndentOptions.setEnabled(enabled);
    myIndentOptionsTabs.setEnabledAt(myIndentOptionsTabs.getTabCount()-1, enabled);
  }

  private JPanel createTabOptionsPanel() {
    OptionGroup optionGroup = new OptionGroup(ApplicationBundle.message("group.tabs.and.indents"));

    myCbUseSameIndents = new JCheckBox(ApplicationBundle.message("checkbox.indent.use.same.settings.for.all.file.types"));
    optionGroup.add(myCbUseSameIndents);

    myIndentOptionsTabs = new TabbedPaneWrapper(this);

    for(Map.Entry<FileType, IndentOptionsEditor> entry: myAdditionalIndentOptions.entrySet()) {
      FileType ft = entry.getKey();
      String tabName = ft instanceof LanguageFileType ? ((LanguageFileType)ft).getLanguage().getDisplayName() : ft.getName();
      myIndentOptionsTabs.addTab(tabName, entry.getValue().createPanel());
    }

    myIndentOptionsTabs.addTab(ApplicationBundle.message("tab.indent.other"), myOtherIndentOptions.createPanel());

    myIndentOptionsTabs.addChangeListener(new ChangeListener() {
      public void stateChanged(final ChangeEvent e) {
        final int selIndex = myIndentOptionsTabs.getSelectedIndex();
        if (selIndex != myLastSelectedTab) {
          myLastSelectedTab = selIndex;
          updatePreviewEditor();
          somethingChanged();
        }
      }
    });

    optionGroup.add(myIndentOptionsTabs.getComponent());

    /*
    UiNotifyConnector.doWhenFirstShown(myPanel, new Runnable() {
      public void run() {
        updatePreviewEditor();
      }
    });*/

    return optionGroup.createPanel();
  }


  protected int getRightMargin() {
    return myRightMargin;
  }

  @NotNull
  protected FileType getFileType() {
    FileTypeIndentOptionsProvider provider = getSelectedIndentProvider();
    if (provider == null) return FileTypes.PLAIN_TEXT;
    return provider.getFileType();
  }

  protected String getPreviewText() {
    final FileTypeIndentOptionsProvider provider = getSelectedIndentProvider();
    if (provider != null) return provider.getPreviewText();
    return "";
  }

  @Nullable
  private FileTypeIndentOptionsProvider getSelectedIndentProvider() {
    if (myIndentOptionsTabs == null) return getDefaultIndentProvider();
    final int selIndex = myIndentOptionsTabs.getSelectedIndex();
    if (selIndex >= 0 && selIndex < myIndentOptionsProviders.size()) {
      return myIndentOptionsProviders.get(selIndex);
    }
    return getDefaultIndentProvider();
  }

  @Nullable
  private static FileTypeIndentOptionsProvider getDefaultIndentProvider() {
    FileTypeIndentOptionsProvider[] providers = Extensions.getExtensions(FileTypeIndentOptionsProvider.EP_NAME);
    return providers.length == 0 ? null : providers[0];
  }

  public void apply(CodeStyleSettings settings) {
    settings.LINE_SEPARATOR = getSelectedLineSeparator();
    settings.USE_SAME_INDENTS = myCbUseSameIndents.isSelected();
    if (settings.USE_SAME_INDENTS) {
      IndentOptionsEditor theEditor = findEditorForSameIndents();
      theEditor.apply(settings, settings.OTHER_INDENT_OPTIONS);
    }
    else {
      myOtherIndentOptions.apply(settings, settings.OTHER_INDENT_OPTIONS);

      for(Map.Entry<FileType, IndentOptionsEditor> entry : myAdditionalIndentOptions.entrySet()) {
        FileType fileType = entry.getKey();
        IndentOptionsEditor editor = entry.getValue();
        editor.apply(settings, settings.getAdditionalIndentOptions(fileType));
      }
    }

    int rightMarginImpl = getRightMarginImpl();
    if (rightMarginImpl > 0) {
      settings.RIGHT_MARGIN = rightMarginImpl;
    }
    settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MAGIN = myCbWrapWhenTypingReachesRightMargin.isSelected();
  }

  private IndentOptionsEditor findEditorForSameIndents() {
    return myAdditionalIndentOptions.isEmpty() ? myOtherIndentOptions : myAdditionalIndentOptions.values().iterator().next();
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
    if (myCbUseSameIndents.isSelected() != settings.USE_SAME_INDENTS) {
      return true;
    }
    if (settings.USE_SAME_INDENTS) {
      final IndentOptionsEditor editor = findEditorForSameIndents();
      // since the values from the editor will be saved into all options,
      if (editor.isModified(settings, settings.OTHER_INDENT_OPTIONS)) {
        return true;
      }
    }
    else {
      if (myOtherIndentOptions.isModified(settings, settings.OTHER_INDENT_OPTIONS)) {
        return true;
      }

      for(Map.Entry<FileType, IndentOptionsEditor> entry : myAdditionalIndentOptions.entrySet()) {
        IndentOptionsEditor editor = entry.getValue();
        FileType fileType = entry.getKey();
        if (editor.isModified(settings, settings.getAdditionalIndentOptions(fileType))) {
          return true;
        }
      }
    }

    if (settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MAGIN ^ myCbWrapWhenTypingReachesRightMargin.isSelected()) {
      return true;
    }

    return !myRightMarginField.getText().equals(String.valueOf(settings.RIGHT_MARGIN));
  }

  public JComponent getPanel() {
    return myPanel;
  }

  protected void resetImpl(final CodeStyleSettings settings) {
    myCbUseSameIndents.setSelected(settings.USE_SAME_INDENTS);

    myOtherIndentOptions.reset(settings, settings.OTHER_INDENT_OPTIONS);

    boolean first = true;
    for(Map.Entry<FileType, IndentOptionsEditor> entry : myAdditionalIndentOptions.entrySet()) {
      final IndentOptionsEditor editor = entry.getValue();
      if (settings.USE_SAME_INDENTS && first) {
        first = false;
        editor.reset(settings, settings.OTHER_INDENT_OPTIONS);
      }
      else {
        FileType type = entry.getKey();
        editor.reset(settings, settings.getAdditionalIndentOptions(type));
      }
    }

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
    myCbWrapWhenTypingReachesRightMargin.setSelected(settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MAGIN);
    update();
  }

  protected EditorHighlighter createHighlighter(final EditorColorsScheme scheme) {
    return EditorHighlighterFactory.getInstance().createEditorHighlighter(getFileType(), scheme, null);
  }

  protected void prepareForReformat(final PsiFile psiFile) {
    final FileTypeIndentOptionsProvider provider = getSelectedIndentProvider();
    if (provider != null) {
      provider.prepareForReformat(psiFile);
    }
  }
}
