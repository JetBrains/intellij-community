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

package com.intellij.application.options.colors;

import com.intellij.application.options.OptionsConstants;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.ui.AntialiasingType;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FontComboBox;
import com.intellij.ui.FontInfoRenderer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.JBUI;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.*;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class FontOptions extends JPanel implements OptionsPanel{
  private static final FontInfoRenderer RENDERER = new FontInfoRenderer() {
    @Override
    protected AntialiasingType getAntialiasingType() {
      return UISettings.getShadowInstance().EDITOR_AA_TYPE;
    }
  };
  private static final String HELP_URL = "https://confluence.jetbrains.com/display/IDEADEV/Support+for+Ligatures+in+Editor";

  private final EventDispatcher<ColorAndFontSettingsListener> myDispatcher = EventDispatcher.create(ColorAndFontSettingsListener.class);

  @NotNull private final ColorAndFontOptions myOptions;

  @NotNull private final JTextField myEditorFontSizeField = new JTextField(4);
  @NotNull private final JTextField myLineSpacingField    = new JTextField(4);
  private final FontComboBox myPrimaryCombo = new FontComboBox();
  private final JCheckBox myUseSecondaryFontCheckbox = new JCheckBox(ApplicationBundle.message("secondary.font"));
  private final JCheckBox myEnableLigaturesCheckbox = new JCheckBox(ApplicationBundle.message("use.ligatures"));
  private final JLabel myLigaturesInfoLinkLabel;
  private final FontComboBox mySecondaryCombo = new FontComboBox();

  @NotNull private final JBCheckBox myOnlyMonospacedCheckBox =
    new JBCheckBox(ApplicationBundle.message("checkbox.show.only.monospaced.fonts"));

  private boolean myIsInSchemeChange;


  public FontOptions(ColorAndFontOptions options) {
    this(options, ApplicationBundle.message("group.editor.font"));
  }

  protected FontOptions(@NotNull ColorAndFontOptions options, final String title) {
    setLayout(new MigLayout("ins 0, gap 5, flowx"));
    Insets borderInsets = new Insets(IdeBorderFactory.TITLED_BORDER_TOP_INSET,
                                     IdeBorderFactory.TITLED_BORDER_LEFT_INSET,
                                     0,
                                     IdeBorderFactory.TITLED_BORDER_RIGHT_INSET);
    setBorder(IdeBorderFactory.createTitledBorder(title, false, borderInsets));
    myOptions = options;
    add(myOnlyMonospacedCheckBox, "sgx b, sx 2");

    add(new JLabel(ApplicationBundle.message("primary.font")), "newline, ax right");
    add(myPrimaryCombo, "sgx b");
    add(new JLabel(ApplicationBundle.message("editbox.font.size")), "gapleft 20");
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
    myLigaturesInfoLinkLabel = new LinkLabel<>(ApplicationBundle.message("ligatures.more.info"), null, (LinkListener<Void>)(aSource, aLinkData) -> BrowserUtil.browse(HELP_URL));
    myLigaturesInfoLinkLabel.setBorder(JBUI.Borders.emptyLeft(5));
    panel.add(myLigaturesInfoLinkLabel);
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
    ItemListener itemListener = e -> {
      if (e.getStateChange() == ItemEvent.SELECTED) {
        syncFontFamilies();
      }
    };
    myPrimaryCombo.addItemListener(itemListener);
    mySecondaryCombo.addItemListener(itemListener);

    ActionListener actionListener = e -> syncFontFamilies();
    myPrimaryCombo.addActionListener(actionListener);
    mySecondaryCombo.addActionListener(actionListener);

    myEditorFontSizeField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void textChanged(DocumentEvent event) {
        if (myIsInSchemeChange || !SwingUtilities.isEventDispatchThread()) return;
        String selectedFont = myPrimaryCombo.getFontName();
        if (selectedFont != null) {
          FontPreferences fontPreferences = getFontPreferences();
          fontPreferences.register(selectedFont, getFontSizeFromField());
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
          value = Math.min(OptionsConstants.MAX_EDITOR_FONT_SIZE, Math.max(OptionsConstants.MIN_EDITOR_FONT_SIZE, value));
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
          value = Math.min(OptionsConstants.MAX_EDITOR_LINE_SPACING, Math.max(OptionsConstants.MIN_EDITOR_LINE_SPACING, value));
          myLineSpacingField.setText(String.format(Locale.ENGLISH, "%.1f", value));
        }
        catch (NumberFormatException ignored) {
        }
      }
    });
    myEnableLigaturesCheckbox.addActionListener(e -> getFontPreferences().setUseLigatures(myEnableLigaturesCheckbox.isSelected()));
  }

  private int getFontSizeFromField() {
    try {
      return Math.min(OptionsConstants.MAX_EDITOR_FONT_SIZE,
                      Math.max(OptionsConstants.MIN_EDITOR_FONT_SIZE, Integer.parseInt(myEditorFontSizeField.getText())));
    }
    catch (NumberFormatException e) {
      return OptionsConstants.DEFAULT_EDITOR_FONT_SIZE;
    }
  }

  private float getLineSpacingFromField() {
    try {
       return Math.min(OptionsConstants.MAX_EDITOR_LINE_SPACING, Math.max(OptionsConstants.MIN_EDITOR_LINE_SPACING, Float.parseFloat(myLineSpacingField.getText())));
    } catch (NumberFormatException e){
      return OptionsConstants.DEFAULT_EDITOR_LINE_SPACING;
    }
  }

  private void syncFontFamilies() {
    if (myIsInSchemeChange) {
      return;
    }
    FontPreferences fontPreferences = getFontPreferences();
    fontPreferences.clearFonts();
    String primaryFontFamily = myPrimaryCombo.getFontName();
    String secondaryFontFamily = mySecondaryCombo.isEnabled() ? mySecondaryCombo.getFontName() : null;
    int fontSize = getFontSizeFromField();
    if (primaryFontFamily != null ) {
      if (!FontPreferences.DEFAULT_FONT_NAME.equals(primaryFontFamily)) {
        fontPreferences.addFontFamily(primaryFontFamily);
      }
      fontPreferences.register(primaryFontFamily, JBUI.scale(fontSize));
    }
    if (secondaryFontFamily != null) {
      if (!FontPreferences.DEFAULT_FONT_NAME.equals(secondaryFontFamily)){
        fontPreferences.addFontFamily(secondaryFontFamily);
      }
      fontPreferences.register(secondaryFontFamily, JBUI.scale(fontSize));
    }
    updateDescription(true);
  }


  public static void showReadOnlyMessage(JComponent parent, final boolean sharedScheme) {
    if (!sharedScheme) {
      Messages.showMessageDialog(
        parent,
        ApplicationBundle.message("error.readonly.scheme.cannot.be.modified"),
        ApplicationBundle.message("title.cannot.modify.readonly.scheme"),
        Messages.getInformationIcon()
      );
    }
    else {
      Messages.showMessageDialog(
        parent,
        ApplicationBundle.message("error.shared.scheme.cannot.be.modified"),
        ApplicationBundle.message("title.cannot.modify.readonly.scheme"),
        Messages.getInformationIcon()
      );
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

    boolean readOnly = ColorAndFontOptions.isReadOnly(myOptions.getSelectedScheme());
    myPrimaryCombo.setEnabled(!readOnly);
    mySecondaryCombo.setEnabled(isThereSecondaryFont && !readOnly);
    myOnlyMonospacedCheckBox.setEnabled(!readOnly);
    myLineSpacingField.setEnabled(!readOnly);
    myEditorFontSizeField.setEnabled(!readOnly);
    myUseSecondaryFontCheckbox.setEnabled(!readOnly);

    myEnableLigaturesCheckbox.setEnabled(!readOnly);
    myLigaturesInfoLinkLabel.setEnabled(!readOnly);
    myEnableLigaturesCheckbox.setSelected(fontPreferences.useLigatures());

    myIsInSchemeChange = false;
  }

  @NotNull
  protected FontPreferences getFontPreferences() {
    return getCurrentScheme().getFontPreferences();
  }

  protected float getLineSpacing() {
    return getCurrentScheme().getLineSpacing();
  }

  protected void setCurrentLineSpacing(float lineSpacing) {
    getCurrentScheme().setLineSpacing(lineSpacing);
  }

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

  protected EditorColorsScheme getCurrentScheme() {
    return myOptions.getSelectedScheme();
  }

  public boolean updateDescription(boolean modified) {
    EditorColorsScheme scheme = myOptions.getSelectedScheme();

    if (modified && (ColorAndFontOptions.isReadOnly(scheme) || ColorSettingsUtil.isSharedScheme(scheme))) {
      showReadOnlyMessage(this, ColorSettingsUtil.isSharedScheme(scheme));
      return false;
    }

    myDispatcher.getMulticaster().fontChanged();

    return true;
  }

  @Override
  public void addListener(ColorAndFontSettingsListener listener) {
    myDispatcher.addListener(listener);
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
