// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.colors;

import com.intellij.application.options.EditorFontsConstants;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.colors.ModifiableFontPreferences;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.ui.*;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.dsl.builder.impl.UtilsKt;
import com.intellij.util.EventDispatcher;
import com.intellij.util.MathUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
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

import static com.intellij.ui.render.RenderersKt.fontInfoRenderer;

public abstract class AbstractFontOptionsPanel extends JPanel implements OptionsPanel {

  private static final ListCellRenderer<Object> DEFAULT_FONT_COMBO_RENDERER = fontInfoRenderer(true);

  private final EventDispatcher<ColorAndFontSettingsListener> myDispatcher = EventDispatcher.create(ColorAndFontSettingsListener.class);

  private final JLabel myPrimaryLabel = new JLabel(ApplicationBundle.message("primary.font"));
  private final AbstractFontCombo<?> myPrimaryCombo;
  private final JCheckBox myEnableLigaturesCheckbox = new JCheckBox(ApplicationBundle.message("use.ligatures"));
  private final JLabel enableLigaturesHintLabel = new JLabel(AllIcons.General.ContextHelp);
  private final AbstractFontCombo<?> mySecondaryCombo;

  private final @NotNull JBCheckBox myOnlyMonospacedCheckBox =
    new JBCheckBox(ApplicationBundle.message("checkbox.show.only.monospaced.fonts"));

  private boolean myIsInSchemeChange;
  private final JLabel mySizeLabel = new JLabel(ApplicationBundle.message("editbox.font.size"));
  private final JTextField myEditorFontSizeField = new JBTextField(4);

  protected static final int ADDITIONAL_VERTICAL_GAP = 12;
  protected static final int BASE_INSET = 5;
  private JLabel mySecondaryFontLabel;
  private final JLabel myLineSpacingLabel = new JLabel(ApplicationBundle.message("editbox.line.spacing"));
  private final JTextField myLineSpacingField = new JBTextField(4);

  protected AbstractFontOptionsPanel() {
    this(null);
  }

  protected AbstractFontOptionsPanel(@Nullable Boolean isMonospacedOnly) {
    myPrimaryCombo = createPrimaryFontCombo();
    mySecondaryCombo = createSecondaryFontCombo();
    myPrimaryLabel.setLabelFor(myPrimaryCombo);
    mySizeLabel.setLabelFor(myEditorFontSizeField);
    myEditorFontSizeField.setColumns(4);
    myLineSpacingLabel.setLabelFor(myLineSpacingField);
    enableLigaturesHintLabel.setToolTipText(ApplicationBundle.message("ligatures.tooltip"));
    myOnlyMonospacedCheckBox.setFont(JBUI.Fonts.smallFont());

    addControls();

    myEditorFontSizeField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void textChanged(@NotNull DocumentEvent event) {
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
          float value = Float.parseFloat(myEditorFontSizeField.getText());
          value += (up ? 1 : -1);
          value = MathUtil.clamp(value, EditorFontsConstants.getMinEditorFontSize(), EditorFontsConstants.getMaxEditorFontSize());
          myEditorFontSizeField.setText(String.valueOf(value));
        }
        catch (NumberFormatException ignored) {
        }
      }
    });
    mySecondaryCombo.setEnabled(false);

    if (myPrimaryCombo.isMonospacedOnlySupported()) {
      myOnlyMonospacedCheckBox.setVisible(true);
      myOnlyMonospacedCheckBox.setBorder(null);
      myOnlyMonospacedCheckBox.setSelected(isMonospacedOnly != null ? isMonospacedOnly : EditorColorsManager.getInstance().isUseOnlyMonospacedFonts());
      myOnlyMonospacedCheckBox.addActionListener(e -> {
        EditorColorsManager.getInstance().setUseOnlyMonospacedFonts(myOnlyMonospacedCheckBox.isSelected());
        myPrimaryCombo.setMonospacedOnly(myOnlyMonospacedCheckBox.isSelected());
        mySecondaryCombo.setMonospacedOnly(myOnlyMonospacedCheckBox.isSelected());
      });
    }
    else {
      myOnlyMonospacedCheckBox.setVisible(false);
    }

    myPrimaryCombo.setMonospacedOnly(myOnlyMonospacedCheckBox.isSelected());
    mySecondaryCombo.setMonospacedOnly(myOnlyMonospacedCheckBox.isSelected());

    ItemListener itemListener = this::syncFontFamilies;
    myPrimaryCombo.addItemListener(itemListener);
    mySecondaryCombo.addItemListener(itemListener);

    ActionListener actionListener = this::syncFontFamilies;
    myPrimaryCombo.addActionListener(actionListener);
    mySecondaryCombo.addActionListener(actionListener);

    myLineSpacingField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void textChanged(@NotNull DocumentEvent event) {
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
          value = MathUtil.clamp(value, EditorFontsConstants.getMinEditorLineSpacing(), EditorFontsConstants.getMaxEditorLineSpacing());
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

  protected JLabel getPrimaryLabel() {
    return myPrimaryLabel;
  }

  protected AbstractFontCombo<?> getPrimaryCombo() {
    return myPrimaryCombo;
  }

  protected AbstractFontCombo<?> getSecondaryCombo() {
    return mySecondaryCombo;
  }

  protected void setSecondaryFontLabel(JLabel secondaryFontLabel) {
    mySecondaryFontLabel = secondaryFontLabel;
  }

  protected JLabel getSizeLabel() {
    return mySizeLabel;
  }

  protected JTextField getEditorFontSizeField() {
    return myEditorFontSizeField;
  }

  protected JLabel getLineSpacingLabel() {
    return myLineSpacingLabel;
  }

  protected JTextField getLineSpacingField() {
    return myLineSpacingField;
  }

  protected JCheckBox getEnableLigaturesCheckbox() {
    return myEnableLigaturesCheckbox;
  }

  protected JLabel getEnableLigaturesHintLabel() {
    return enableLigaturesHintLabel;
  }

  protected void addControls() {
    setLayout(new FlowLayout(FlowLayout.LEFT));
    add(createControls());
  }

  protected AbstractFontCombo<?> createPrimaryFontCombo() {
    FontComboBox primaryCombo = new FontComboBox();
    //noinspection unchecked
    primaryCombo.setRenderer(DEFAULT_FONT_COMBO_RENDERER);
    return primaryCombo;
  }

  protected AbstractFontCombo<?> createSecondaryFontCombo() {
     FontComboBox secondaryCombo = new FontComboBox(false, false, true);
    //noinspection unchecked
    secondaryCombo.setRenderer(DEFAULT_FONT_COMBO_RENDERER);
    return secondaryCombo;
  }

  protected JComponent createControls() {
    return createFontSettingsPanel();
  }

  protected final JPanel createFontSettingsPanel() {
    Insets baseInsets = getInsets(0, 0);

    JPanel fontPanel = new JPanel(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.anchor = GridBagConstraints.WEST;
    c.insets = baseInsets;

    c.gridx = 0;
    c.gridy = 0;
    fontPanel.add(myPrimaryLabel, c);

    c.gridx = 1;
    fontPanel.add(myPrimaryCombo, c);

    c.gridx = 1;
    c.gridy ++;
    c.insets = getInsets(0, BASE_INSET);
    fontPanel.add(myOnlyMonospacedCheckBox, c);

    c.gridx = 0;
    c.gridy ++;
    c.insets = baseInsets;
    fontPanel.add(mySizeLabel, c);

    c.gridx = 1;

    JPanel sizePanel = new JPanel();
    FlowLayout sizeLayout = new FlowLayout(FlowLayout.LEFT);
    sizeLayout.setHgap(0);
    sizePanel.setLayout(sizeLayout);
    sizePanel.setBorder(JBUI.Borders.empty());
    sizePanel.add(myEditorFontSizeField);
    sizePanel.add(Box.createHorizontalStrut(20));
    sizePanel.add(myLineSpacingLabel);
    sizePanel.add(myLineSpacingField);
    fontPanel.add(sizePanel,c);

    c.gridy ++;
    c.gridx = 0;
    c.insets = getInsets(ADDITIONAL_VERTICAL_GAP, 0);

    JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
    panel.setBorder(JBUI.Borders.empty());
    panel.add(myEnableLigaturesCheckbox);
    enableLigaturesHintLabel.setBorder(JBUI.Borders.emptyLeft(5));
    panel.add(enableLigaturesHintLabel);
    c.gridx = 0;
    c.gridy ++;
    c.gridwidth = 2;
    c.insets = getInsets(ADDITIONAL_VERTICAL_GAP, 0);
    c.insets.bottom = BASE_INSET;
    fontPanel.add(panel, c);

    c.gridx = 0;
    c.gridy ++;
    fontPanel.add(createReaderModeComment(), c);

    return fontPanel;
  }

  private static @NotNull JEditorPane createReaderModeComment() {
    return UtilsKt.createComment(ApplicationBundle.message("comment.use.ligatures.with.reader.mode"), -1,
                                     e -> goToReaderMode()
    );
  }

  protected static void goToReaderMode() {
    DataManager.getInstance().getDataContextFromFocusAsync().onSuccess(context -> {
      if (context == null) return;
      Settings settings = Settings.KEY.getData(context);
      if (settings == null) return;
      settings.select(settings.find("editor.reader.mode"));
      ReaderModeStatsCollector.logSeeAlsoNavigation();
    });
  }

  protected final void createSecondaryFontComboAndLabel(@NotNull JPanel target, @NotNull GridBagConstraints c) {
    mySecondaryFontLabel = new JLabel(ApplicationBundle.message("secondary.font"));
    mySecondaryFontLabel.setLabelFor(mySecondaryCombo);
    target.add(mySecondaryFontLabel, c);
    c.insets = getInsets(0, 0);
    c.gridx = 1;
    target.add(mySecondaryCombo, c);
    c.gridy ++;
    JBLabel fallbackLabel = new JBLabel("<html>" + ApplicationBundle.message("label.fallback.fonts.list.description"));
    fallbackLabel.setFont(JBUI.Fonts.smallFont());
    fallbackLabel.setForeground(UIUtil.getContextHelpForeground());
    target.add(fallbackLabel, c);
  }

  protected static Insets getInsets(int extraTopSpacing, int extraLeftSpacing) {
    return JBUI.insets(BASE_INSET + extraTopSpacing, BASE_INSET + extraLeftSpacing, 0, 0);
  }

  protected void setDelegatingPreferences(boolean isDelegating) {
  }

  private float getFontSizeFromField() {
    try {
      return MathUtil.clamp(Float.parseFloat(myEditorFontSizeField.getText()),
                            EditorFontsConstants.getMinEditorFontSize(), EditorFontsConstants.getMaxEditorFontSize());
    }
    catch (NumberFormatException e) {
      return EditorFontsConstants.getDefaultEditorFontSize();
    }
  }

  private float getLineSpacingFromField() {
    try {
      return MathUtil.clamp(Float.parseFloat(myLineSpacingField.getText()), 
                            EditorFontsConstants.getMinEditorLineSpacing(), EditorFontsConstants.getMaxEditorLineSpacing());
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
    if (source instanceof AbstractFontCombo<?> combo) {
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
    if (fontPreferences instanceof ModifiableFontPreferences modifiableFontPreferences) {
      modifiableFontPreferences.clearFonts();
      modifiableFontPreferences.setUseLigatures(myEnableLigaturesCheckbox.isSelected());
      String primaryFontFamily = myPrimaryCombo.getFontName();
      String secondaryFontFamily = mySecondaryCombo.isNoFontSelected() ? null : mySecondaryCombo.getFontName();
      float fontSize = getFontSizeFromField();
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
    mySecondaryCombo.setFontName(isThereSecondaryFont ? fontFamilies.get(1) : null);
    myEditorFontSizeField.setText(String.valueOf(fontPreferences.getSize2D(fontPreferences.getFontFamily())));

    boolean isReadOnlyColorScheme = isReadOnly();
    updateCustomOptions();
    boolean readOnly = isReadOnlyColorScheme || !(getFontPreferences() instanceof ModifiableFontPreferences);
    myPrimaryCombo.setEnabled(!readOnly);
    myPrimaryLabel.setEnabled(!readOnly);
    mySecondaryCombo.setEnabled(!readOnly);
    if (mySecondaryFontLabel != null) mySecondaryFontLabel.setEnabled(!readOnly);
    myOnlyMonospacedCheckBox.setEnabled(!readOnly);
    myLineSpacingField.setEnabled(!readOnly);
    myLineSpacingLabel.setEnabled(!readOnly);
    myEditorFontSizeField.setEnabled(!readOnly);
    mySizeLabel.setEnabled(!readOnly);

    myEnableLigaturesCheckbox.setEnabled(!readOnly);
    myEnableLigaturesCheckbox.setSelected(fontPreferences.useLigatures());

    myIsInSchemeChange = false;
  }

  protected void updateCustomOptions() {
  }

  protected abstract boolean isReadOnly();

  protected abstract boolean isDelegating();

  protected abstract @NotNull FontPreferences getFontPreferences();

  protected abstract void setFontSize(int fontSize);

  protected void setFontSize(float fontSize) {
    setFontSize((int)(fontSize + 0.5));
  }

  protected abstract float getLineSpacing();

  protected abstract void setCurrentLineSpacing(float lineSpacing);

  @Override
  public @Nullable Runnable showOption(final String option) {
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
