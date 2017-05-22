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
package com.intellij.application.options.colors;

import com.intellij.application.options.EditorFontsConstants;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.colors.ModifiableFontPreferences;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FontComboBox;
import com.intellij.ui.FontInfoRenderer;
import com.intellij.ui.TooltipWithClickableLinks;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.JBUI;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public abstract class AbstractFontOptionsPanel extends JPanel implements OptionsPanel {
  private static final FontInfoRenderer RENDERER = new FontInfoRenderer() {
    @Override
    protected boolean isEditorFont() {
      return true;
    }
  };

  private final EventDispatcher<ColorAndFontSettingsListener> myDispatcher = EventDispatcher.create(ColorAndFontSettingsListener.class);

  @NotNull private final JTextField myEditorFontSizeField = new JTextField(4);
  @NotNull private final JTextField myLineSpacingField = new JTextField(4);
  private final FontComboBox myPrimaryCombo = new FontComboBox();
  private final JCheckBox myUseSecondaryFontCheckbox = new JCheckBox(ApplicationBundle.message("secondary.font"));
  private final JCheckBox myEnableLigaturesCheckbox = new JCheckBox(ApplicationBundle.message("use.ligatures"));
  private final FontComboBox mySecondaryCombo = new FontComboBox(false, false);

  @NotNull private final JBCheckBox myOnlyMonospacedCheckBox =
    new JBCheckBox(ApplicationBundle.message("checkbox.show.only.monospaced.fonts"));

  private boolean myIsInSchemeChange;
  private JLabel myPrimaryLabel;
  private JLabel mySizeLabel;


  protected AbstractFontOptionsPanel() {
    setLayout(new MigLayout("ins 0, gap 5, flowx"));
    initControls();
  }

  @SuppressWarnings("unchecked")
  protected void initControls() {
    add(myOnlyMonospacedCheckBox, "newline 10, sgx b, sx 2");

    myPrimaryLabel = new JLabel(ApplicationBundle.message("primary.font"));
    add(myPrimaryLabel, "newline, ax right");
    add(myPrimaryCombo, "sgx b");
    mySizeLabel = new JLabel(ApplicationBundle.message("editbox.font.size"));
    add(mySizeLabel, "gapleft 20");
    add(myEditorFontSizeField);
    add(new JLabel(ApplicationBundle.message("editbox.line.spacing")), "gapleft 20");
    add(myLineSpacingField);

    add(new JLabel(ApplicationBundle.message("label.fallback.fonts.list.description"),
                   MessageType.INFO.getDefaultIcon(),
                   SwingConstants.LEFT), "newline, sx 5");
    add(myUseSecondaryFontCheckbox, "newline, ax right");
    add(mySecondaryCombo, "sgx b");
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
    myEnableLigaturesCheckbox.setBorder(null);
    panel.add(myEnableLigaturesCheckbox);
    JLabel warningIcon = new JLabel(AllIcons.General.BalloonWarning);
    IdeTooltipManager.getInstance().setCustomTooltip(
      warningIcon,
      new TooltipWithClickableLinks.ForBrowser(warningIcon,
                                               ApplicationBundle.message("ligatures.jre.warning",
                                                                         ApplicationNamesInfo.getInstance().getFullProductName())));
    warningIcon.setBorder(JBUI.Borders.emptyLeft(5));
    warningIcon.setVisible(!SystemInfo.isJetBrainsJvm);
    panel.add(warningIcon);
    add(panel, "newline, sx 2");

    myOnlyMonospacedCheckBox.setBorder(null);
    myUseSecondaryFontCheckbox.setBorder(null);
    mySecondaryCombo.setEnabled(false);

    myOnlyMonospacedCheckBox.setSelected(EditorColorsManager.getInstance().isUseOnlyMonospacedFonts());
    myOnlyMonospacedCheckBox.addActionListener(e -> {
      EditorColorsManager.getInstance().setUseOnlyMonospacedFonts(myOnlyMonospacedCheckBox.isSelected());
      myPrimaryCombo.setMonospacedOnly(myOnlyMonospacedCheckBox.isSelected());
      mySecondaryCombo.setMonospacedOnly(myOnlyMonospacedCheckBox.isSelected());
    });
    myPrimaryCombo.setMonospacedOnly(myOnlyMonospacedCheckBox.isSelected());
    myPrimaryCombo.setRenderer(RENDERER);

    mySecondaryCombo.setMonospacedOnly(myOnlyMonospacedCheckBox.isSelected());
    mySecondaryCombo.setRenderer(RENDERER);

    myUseSecondaryFontCheckbox.addActionListener(e -> {
      mySecondaryCombo.setEnabled(myUseSecondaryFontCheckbox.isSelected());
      syncFontFamilies();
    });
    ItemListener itemListener = this::syncFontFamilies;
    myPrimaryCombo.addItemListener(itemListener);
    mySecondaryCombo.addItemListener(itemListener);

    ActionListener actionListener = this::syncFontFamilies;
    myPrimaryCombo.addActionListener(actionListener);
    mySecondaryCombo.addActionListener(actionListener);

    myEditorFontSizeField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void textChanged(DocumentEvent event) {
        if (myIsInSchemeChange || !SwingUtilities.isEventDispatchThread()) return;
        String selectedFont = myPrimaryCombo.getFontName();
        if (selectedFont != null) {
          setFontSize(getFontSizeFromField());
        }
        updateDescription(true);
      }
    });
    myEditorFontSizeField.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.VK_UP && e.getKeyCode() != KeyEvent.VK_DOWN) return;
        boolean up = e.getKeyCode() == KeyEvent.VK_UP;
        try {
          int value = Integer.parseInt(myEditorFontSizeField.getText());
          value += (up ? 1 : -1);
          value = Math.min(EditorFontsConstants.getMaxEditorFontSize(), Math.max(EditorFontsConstants.getMinEditorFontSize(), value));
          myEditorFontSizeField.setText(String.valueOf(value));
        }
        catch (NumberFormatException ignored) {
        }
      }
    });

    myLineSpacingField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void textChanged(DocumentEvent event) {
        if (myIsInSchemeChange) return;
        float lineSpacing = getLineSpacingFromField();
        if (getLineSpacing() != lineSpacing) {
          setCurrentLineSpacing(lineSpacing);
        }
        updateDescription(true);
      }
    });
    myLineSpacingField.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.VK_UP && e.getKeyCode() != KeyEvent.VK_DOWN) return;
        boolean up = e.getKeyCode() == KeyEvent.VK_UP;
        try {
          float value = Float.parseFloat(myLineSpacingField.getText());
          value += (up ? 1 : -1) * .1F;
          value = Math.min(EditorFontsConstants.getMaxEditorLineSpacing(), Math.max(EditorFontsConstants.getMinEditorLineSpacing(), value));
          myLineSpacingField.setText(String.format(Locale.ENGLISH, "%.1f", value));
        }
        catch (NumberFormatException ignored) {
        }
      }
    });
    myEnableLigaturesCheckbox.addActionListener(e -> {
      FontPreferences preferences = getFontPreferences();
      if (preferences instanceof ModifiableFontPreferences) {
        ((ModifiableFontPreferences)preferences).setUseLigatures(myEnableLigaturesCheckbox.isSelected());
        updateDescription(true);
      }
    });
  }

  protected void setDelegatingPreferences(boolean isDelegating) {
  }

  private int getFontSizeFromField() {
    try {
      return Math.min(EditorFontsConstants.getMaxEditorFontSize(),
                      Math.max(EditorFontsConstants.getMinEditorFontSize(), Integer.parseInt(myEditorFontSizeField.getText())));
    }
    catch (NumberFormatException e) {
      return EditorFontsConstants.getDefaultEditorFontSize();
    }
  }

  private float getLineSpacingFromField() {
    try {
      return Math.min(EditorFontsConstants.getMaxEditorLineSpacing(),
                      Math.max(EditorFontsConstants.getMinEditorLineSpacing(), Float.parseFloat(myLineSpacingField.getText())));
    }
    catch (NumberFormatException e) {
      return EditorFontsConstants.getDefaultEditorLineSpacing();
    }
  }

  /**
   * Processes an event from {@code FontComboBox}
   * if it is enabled and its item is selected.
   *
   * @param event the event to process
   */
  private void syncFontFamilies(AWTEvent event) {
    Object source = event.getSource();
    if (source instanceof FontComboBox) {
      FontComboBox combo = (FontComboBox)source;
      if (combo.isEnabled() && combo.isShowing() && combo.getSelectedItem() != null) {
        syncFontFamilies();
      }
    }
  }

  private void syncFontFamilies() {
    if (myIsInSchemeChange) {
      return;
    }
    FontPreferences fontPreferences = getFontPreferences();
    if (fontPreferences instanceof ModifiableFontPreferences) {
      ModifiableFontPreferences modifiableFontPreferences = (ModifiableFontPreferences)fontPreferences;
      modifiableFontPreferences.clearFonts();
      String primaryFontFamily = myPrimaryCombo.getFontName();
      String secondaryFontFamily = mySecondaryCombo.isEnabled() ? mySecondaryCombo.getFontName() : null;
      int fontSize = getFontSizeFromField();
      if (primaryFontFamily != null) {
        if (!FontPreferences.DEFAULT_FONT_NAME.equals(primaryFontFamily)) {
          modifiableFontPreferences.addFontFamily(primaryFontFamily);
        }
        modifiableFontPreferences.register(primaryFontFamily, fontSize);
      }
      if (secondaryFontFamily != null) {
        if (!FontPreferences.DEFAULT_FONT_NAME.equals(secondaryFontFamily)) {
          modifiableFontPreferences.addFontFamily(secondaryFontFamily);
        }
        modifiableFontPreferences.register(secondaryFontFamily, fontSize);
      }
      updateDescription(true);
    }
  }

  @Override
  public void updateOptionsList() {
    myIsInSchemeChange = true;

    myLineSpacingField.setText(Float.toString(getLineSpacing()));
    FontPreferences fontPreferences = getFontPreferences();
    List<String> fontFamilies = fontPreferences.getEffectiveFontFamilies();
    myPrimaryCombo.setFontName(fontPreferences.getFontFamily());
    boolean isThereSecondaryFont = fontFamilies.size() > 1;
    myUseSecondaryFontCheckbox.setSelected(isThereSecondaryFont);
    mySecondaryCombo.setFontName(isThereSecondaryFont ? fontFamilies.get(1) : null);
    myEditorFontSizeField.setText(String.valueOf(fontPreferences.getSize(fontPreferences.getFontFamily())));

    boolean isReadOnlyColorScheme = isReadOnly();
    updateCustomOptions();
    boolean readOnly = isReadOnlyColorScheme || !(getFontPreferences() instanceof ModifiableFontPreferences);
    myPrimaryCombo.setEnabled(!readOnly);
    myPrimaryLabel.setEnabled(!readOnly);
    mySecondaryCombo.setEnabled(isThereSecondaryFont && !readOnly);
    myOnlyMonospacedCheckBox.setEnabled(!readOnly);
    myLineSpacingField.setEnabled(!readOnly);
    myEditorFontSizeField.setEnabled(!readOnly);
    mySizeLabel.setEnabled(!readOnly);
    myUseSecondaryFontCheckbox.setEnabled(!readOnly);

    myEnableLigaturesCheckbox.setEnabled(!readOnly && SystemInfo.isJetBrainsJvm);
    myEnableLigaturesCheckbox.setSelected(fontPreferences.useLigatures());

    myIsInSchemeChange = false;
  }

  protected void updateCustomOptions() {
  }

  protected abstract boolean isReadOnly();

  protected abstract boolean isDelegating();

  @NotNull
  protected abstract FontPreferences getFontPreferences();

  protected abstract void setFontSize(int fontSize);

  protected abstract float getLineSpacing();

  protected abstract void setCurrentLineSpacing(float lineSpacing);

  @Override
  @Nullable
  public Runnable showOption(final String option) {
    return null;
  }

  @Override
  public void applyChangesToScheme() {
  }

  @Override
  public void selectOption(final String typeToSelect) {
  }

  public boolean updateDescription(boolean modified) {
    if (modified && isReadOnly()) {
      return false;
    }
    fireFontChanged();
    return true;
  }

  @Override
  public void addListener(ColorAndFontSettingsListener listener) {
    myDispatcher.addListener(listener);
  }

  public void fireFontChanged() {
    myDispatcher.getMulticaster().fontChanged();
  }

  @Override
  public JPanel getPanel() {
    return this;
  }

  @Override
  public Set<String> processListOptions() {
    return new HashSet<>();
  }

}
