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
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.*;
import com.intellij.ui.OptionGroup;
import com.intellij.ui.TabbedPaneWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.*;
import java.util.List;

public class OtherTabsAndIndentsPanel extends CodeStyleAbstractPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.application.options.GeneralCodeStylePanel");

  private JCheckBox myCbUseSameIndents;

  private final Map<FileType, IndentOptionsEditor> myAdditionalIndentOptions = new LinkedHashMap<FileType, IndentOptionsEditor>();
  private final List<FileTypeIndentOptionsProvider> myIndentOptionsProviders = new ArrayList<FileTypeIndentOptionsProvider>();

  private TabbedPaneWrapper myIndentOptionsTabs;
  private JPanel myIndentPanel;
  private JPanel myPreviewPanel;
  private JPanel myPanel;
  private final int myRightMargin;
  private int myLastSelectedTab = 0;


  public OtherTabsAndIndentsPanel(CodeStyleSettings settings) {
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
    addPanelToWatch(myPanel);

    myRightMargin = settings.RIGHT_MARGIN;

  }

  @Nullable
  private static Language getLanguage(FileType fileType) {
    return (fileType instanceof LanguageFileType) ? ((LanguageFileType)fileType).getLanguage() : null;
  }


  @Override
  protected void somethingChanged() {
    super.somethingChanged();
    update();
  }

  private void update() {
    boolean enabled = !myCbUseSameIndents.isSelected();
    if (myIndentOptionsTabs.getTabCount() <= 0) return;
    if (!enabled && myIndentOptionsTabs.getSelectedIndex() != 0) {
      myIndentOptionsTabs.setSelectedIndex(0);
    }

    int index = 0;
    for(IndentOptionsEditor options: myAdditionalIndentOptions.values()) {
      options.setEnabled(enabled);
      myIndentOptionsTabs.setEnabledAt(index, enabled);
      index++;
    }
    if (myIndentOptionsTabs.getTabCount() > 0) {
      myIndentOptionsTabs.setEnabledAt(myIndentOptionsTabs.getTabCount()-1, enabled);
    }
  }

  private JPanel createTabOptionsPanel() {
    OptionGroup optionGroup = new OptionGroup(null);

    myIndentOptionsTabs = new TabbedPaneWrapper(this);

    for(Map.Entry<FileType, IndentOptionsEditor> entry: myAdditionalIndentOptions.entrySet()) {
      FileType ft = entry.getKey();
      String tabName = ft instanceof LanguageFileType ? ((LanguageFileType)ft).getLanguage().getDisplayName() : ft.getName();
      myIndentOptionsTabs.addTab(tabName, entry.getValue().createPanel());
    }

    myIndentOptionsTabs.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(final ChangeEvent e) {
        final int selIndex = myIndentOptionsTabs.getSelectedIndex();
        if (selIndex != myLastSelectedTab) {
          myLastSelectedTab = selIndex;
          updatePreview(true);
          somethingChanged();
        }
      }
    });

    myIndentOptionsTabs.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        onTabChange();
      }
    });

    optionGroup.add(myIndentOptionsTabs.getComponent());

    myCbUseSameIndents = new JCheckBox(ApplicationBundle.message("checkbox.indent.use.same.settings.for.all.file.types"));
    optionGroup.add(myCbUseSameIndents, true);

    return optionGroup.createPanel();
  }


  @Override
  protected int getRightMargin() {
    return myRightMargin;
  }

  @Override
  @NotNull
  protected FileType getFileType() {
    FileTypeIndentOptionsProvider provider = getSelectedIndentProvider();
    if (provider == null) return FileTypes.PLAIN_TEXT;
    return provider.getFileType();
  }

  @Override
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

  @Override
  public void apply(CodeStyleSettings settings) {
    settings.USE_SAME_INDENTS = myCbUseSameIndents.isSelected();
    if (!settings.USE_SAME_INDENTS) {
      for(Map.Entry<FileType, IndentOptionsEditor> entry : myAdditionalIndentOptions.entrySet()) {
        FileType fileType = entry.getKey();
        CommonCodeStyleSettings.IndentOptions additionalIndentOptions = settings.getAdditionalIndentOptions(fileType);
        if (additionalIndentOptions == null) {
          continue;
        }
        IndentOptionsEditor editor = entry.getValue();
        editor.apply(settings, additionalIndentOptions);
      }
    }

  }


  @Override
  public boolean isModified(CodeStyleSettings settings) {
    if (myCbUseSameIndents.isSelected() != settings.USE_SAME_INDENTS) {
      return true;
    }
    if (!settings.USE_SAME_INDENTS) {
      for(Map.Entry<FileType, IndentOptionsEditor> entry : myAdditionalIndentOptions.entrySet()) {
        FileType fileType = entry.getKey();
        CommonCodeStyleSettings.IndentOptions additionalIndentOptions = settings.getAdditionalIndentOptions(fileType);
        if (additionalIndentOptions == null) {
          continue;
        }
        IndentOptionsEditor editor = entry.getValue();
        if (editor.isModified(settings, additionalIndentOptions)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public JComponent getPanel() {
    return myPanel;
  }

  @Override
  protected void resetImpl(final CodeStyleSettings settings) {
    myCbUseSameIndents.setSelected(settings.USE_SAME_INDENTS);

    for (Map.Entry<FileType, IndentOptionsEditor> entry : myAdditionalIndentOptions.entrySet()) {
      final IndentOptionsEditor editor = entry.getValue();
      FileType type = entry.getKey();
      CommonCodeStyleSettings.IndentOptions additionalIndentOptions = settings.getAdditionalIndentOptions(type);
      if (additionalIndentOptions != null) {
        editor.reset(settings, additionalIndentOptions);
      }
    }

    update();
  }

  @Override
  protected EditorHighlighter createHighlighter(final EditorColorsScheme scheme) {
    //noinspection NullableProblems
    return EditorHighlighterFactory.getInstance().createEditorHighlighter(getFileType(), scheme, null);
  }

  @Override
  protected void prepareForReformat(final PsiFile psiFile) {
    final FileTypeIndentOptionsProvider provider = getSelectedIndentProvider();
    if (provider != null) {
      provider.prepareForReformat(psiFile);
    }
  }

  private void onTabChange() {
    getLanguageSelector().setLanguage(getSelectedLanguage());
  }

  @Nullable
  private Language getSelectedLanguage() {
    int selectedIndex = myIndentOptionsTabs.getSelectedIndex();
    int i = 0;
    for (Map.Entry<FileType, IndentOptionsEditor> entry : myAdditionalIndentOptions.entrySet()) {
      if (i == selectedIndex) {
        FileType ft = entry.getKey();
        if (ft instanceof LanguageFileType) {
          return ((LanguageFileType)ft).getLanguage();
        }
        return null;
      }
      i++;
    }
    return null;
  }

  @Override
  public Language getDefaultLanguage() {
    return getSelectedLanguage();
  }
}
