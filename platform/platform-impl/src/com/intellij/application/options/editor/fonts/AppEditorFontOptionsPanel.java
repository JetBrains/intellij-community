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
package com.intellij.application.options.editor.fonts;

import com.intellij.application.options.colors.AbstractFontOptionsPanel;
import com.intellij.application.options.colors.ColorAndFontOptions;
import com.intellij.application.options.colors.ColorAndFontSettingsListener;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.colors.ModifiableFontPreferences;
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions;
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl;
import com.intellij.openapi.editor.impl.FontFamilyService;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.ui.AbstractFontCombo;
import com.intellij.ui.HoverHyperlinkLabel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.ActionLink;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.function.Consumer;

public class AppEditorFontOptionsPanel extends AbstractFontOptionsPanel {

  private final static int FONT_WEIGHT_COMBO_WIDTH = 250;

  private final EditorColorsScheme myScheme;
  private JPanel myWarningPanel;
  private JLabel myEditorFontLabel;
  private final FontPreferences myDefaultPreferences;
  private FontWeightCombo myRegularWeightCombo;
  private FontWeightCombo myBoldWeightCombo;

  private ActionLink myRestoreLabel;

  protected AppEditorFontOptionsPanel(EditorColorsScheme scheme) {
    myScheme = scheme;
    myDefaultPreferences = new FontPreferencesImpl();
    AppEditorFontOptions.initDefaults((ModifiableFontPreferences)myDefaultPreferences);
    updateOptionsList();
  }

  @Override
  protected JComponent createControls() {
    JPanel topPanel = new JPanel(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.insets = JBUI.emptyInsets();
    c.gridx = 0;
    c.gridy = 0;
    c.anchor = GridBagConstraints.WEST;
    myWarningPanel = createMessagePanel();
    topPanel.add(myWarningPanel, c);
    c.gridy ++;
    topPanel.add(createFontSettingsPanel(), c);
    c.gridy ++;
    c.insets = JBUI.insets(ADDITIONAL_VERTICAL_GAP, BASE_INSET, 0, 0);
    myRestoreLabel = createRestoreLabel();
    topPanel.add(myRestoreLabel, c);
    c.fill = GridBagConstraints.HORIZONTAL;
    c.gridy ++;
    c.insets = JBUI.insets(ADDITIONAL_VERTICAL_GAP, 0);
    topPanel.add(createTypographySettingsPanel(), c);
    addListener(new ColorAndFontSettingsListener.Abstract() {
      @Override
      public void fontChanged() {
        if (myRegularWeightCombo != null) {
          myRegularWeightCombo.update(getFontPreferences());
        }
        if (myBoldWeightCombo != null) {
          myBoldWeightCombo.update(getFontPreferences());
        }
        updateWarning();
        updateRestoreButtonState();
      }
    });
    return topPanel;
  }

  private JPanel createTypographySettingsPanel() {
    JPanel typographyPanel = new JPanel(new BorderLayout());

    JPanel expansionPanel = new JPanel(new BorderLayout());
    expansionPanel.setBorder(JBUI.Borders.empty());
    JPanel titlePanel = new JPanel(new BorderLayout());
    titlePanel.setBorder(IdeBorderFactory.createTitledBorder(ApplicationBundle.message("settings.editor.font.typography.settings"), false, JBUI.emptyInsets()));
    expansionPanel.add(titlePanel, BorderLayout.CENTER);
    JLabel arrowLabel = new JLabel(AllIcons.General.ArrowRight);
    arrowLabel.setBorder(JBUI.Borders.empty(0, 2, 10, 5));
    expansionPanel.add(arrowLabel, BorderLayout.WEST);

    typographyPanel.add(expansionPanel, BorderLayout.NORTH);

    JPanel internalPanel = new JPanel(new GridBagLayout());
    internalPanel.setBorder(JBUI.Borders.empty());
    GridBagConstraints c = new GridBagConstraints();
    c.insets = getInsets(0, 0);
    c.gridx = 0;
    c.gridy = 0;
    c.anchor = GridBagConstraints.LINE_START;
    if (isAdvancedFontFamiliesUI()) {
      internalPanel.add(new JLabel(ApplicationBundle.message("settings.editor.font.main.weight")), c);
      c.gridx = 1;
      c.insets = JBUI.insets(0, BASE_INSET, 0, 0);
      myRegularWeightCombo = new MyRegularFontWeightCombo();
      fixComboWidth(myRegularWeightCombo, JBUI.scale(FONT_WEIGHT_COMBO_WIDTH));
      myRegularWeightCombo.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          changeFontPreferences(
            preferences -> {
              String newSubFamily = myRegularWeightCombo.getSelectedSubFamily();
              if (!Objects.equals(preferences.getRegularSubFamily(), newSubFamily)) {
                preferences.setBoldSubFamily(null); // Reset bold subfamily for a different regular
              }
              preferences.setRegularSubFamily(newSubFamily);
            });
        }
      });
      internalPanel.add(myRegularWeightCombo, c);
      c.gridy ++;
      c.gridx = 0;
      internalPanel.add(new JLabel(ApplicationBundle.message("settings.editor.font.bold.weight")), c);
      c.gridx = 1;
      c.insets = getInsets(0, 0);
      myBoldWeightCombo = new MyBoldFontWeightCombo();
      myBoldWeightCombo.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          changeFontPreferences(
            preferences -> preferences.setBoldSubFamily(myBoldWeightCombo.getSelectedSubFamily()));
        }
      });
      fixComboWidth(myBoldWeightCombo, JBUI.scale(FONT_WEIGHT_COMBO_WIDTH));
      internalPanel.add(myBoldWeightCombo, c);
      c.gridy ++;
      JLabel boldHintLabel = new JLabel(ApplicationBundle.message("settings.editor.font.bold.weight.hint"));
      boldHintLabel.setFont(JBUI.Fonts.smallFont());
      boldHintLabel.setForeground(UIUtil.getContextHelpForeground());
      internalPanel.add(boldHintLabel, c);
      c.gridy ++;
    }
    c.gridx = 0;
    createSecondaryFontComboAndLabel(internalPanel, c);
    internalPanel.setVisible(false);
    typographyPanel.add(internalPanel, BorderLayout.CENTER);

    arrowLabel.addMouseListener(
      new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          internalPanel.setVisible(!internalPanel.isVisible());
          arrowLabel.setIcon(internalPanel.isVisible() ? AllIcons.General.ArrowDown : AllIcons.General.ArrowRight);
        }
      }
    );
    return typographyPanel;
  }

  private static void fixComboWidth(@NotNull FontWeightCombo combo, int width) {
    combo.setMinimumSize(new Dimension(width, 0));
    combo.setMinimumSize(new Dimension(width, Integer.MAX_VALUE));
    combo.setPreferredSize(new Dimension(width, combo.getPreferredSize().height));
  }

  void restoreDefaults() {
    AppEditorFontOptions.initDefaults((ModifiableFontPreferences)getFontPreferences());
    updateOnChangedFont();
  }


  @NotNull
  private ActionLink createRestoreLabel() {
    return new ActionLink(ApplicationBundle.message("settings.editor.font.restored.defaults"), e -> {
      restoreDefaults();
    });
  }

  public void updateOnChangedFont() {
    updateOptionsList();
    fireFontChanged();
  }

  private void updateRestoreButtonState() {
    myRestoreLabel.setEnabled(!myDefaultPreferences.equals(getFontPreferences()));
  }

  private JPanel createMessagePanel() {
    JPanel messagePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    messagePanel.add(new JLabel(AllIcons.General.BalloonWarning));
    myEditorFontLabel = createHyperlinkLabel();
    messagePanel.add(myEditorFontLabel);
    JLabel commentLabel = new JLabel(ApplicationBundle.message("settings.editor.font.defined.in.color.scheme.message"));
    commentLabel.setForeground(JBColor.GRAY);
    messagePanel.add(commentLabel);
    return messagePanel;
  }

  public void updateWarning() {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    if (!scheme.isUseAppFontPreferencesInEditor()) {
      myEditorFontLabel.setText(
        ApplicationBundle.message("settings.editor.font.overridden.message", scheme.getEditorFontName(), scheme.getEditorFontSize()));
      myWarningPanel.setVisible(true);
    }
    else {
      myWarningPanel.setVisible(false);
    }
  }

  @Override
  protected boolean isReadOnly() {
    return false;
  }

  @Override
  protected boolean isDelegating() {
    return false;
  }

  @NotNull
  @Override
  protected FontPreferences getFontPreferences() {
    return myScheme.getFontPreferences();
  }

  private void changeFontPreferences(Consumer<ModifiableFontPreferences> consumer) {
    FontPreferences preferences = getFontPreferences();
    assert preferences instanceof ModifiableFontPreferences;
    consumer.accept((ModifiableFontPreferences)preferences);
    fireFontChanged();
  }

  @Override
  protected void setFontSize(int fontSize) {
    myScheme.setEditorFontSize(fontSize);
  }

  @Override
  protected float getLineSpacing() {
    return myScheme.getLineSpacing();
  }

  @Override
  protected void setCurrentLineSpacing(float lineSpacing) {
    myScheme.setLineSpacing(lineSpacing);
  }

  @NotNull
  private JLabel createHyperlinkLabel() {
    HoverHyperlinkLabel label = new HoverHyperlinkLabel("");
    label.addHyperlinkListener(new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          navigateToColorSchemeFontConfigurable();
        }
      }
    });
    return label;
  }

  protected void navigateToColorSchemeFontConfigurable() {
    Settings allSettings = Settings.KEY.getData(DataManager.getInstance().getDataContext(getPanel()));
    if (allSettings != null) {
      final Configurable colorSchemeConfigurable = allSettings.find(ColorAndFontOptions.ID);
      if (colorSchemeConfigurable instanceof ColorAndFontOptions) {
        Configurable fontOptions =
          ((ColorAndFontOptions)colorSchemeConfigurable).findSubConfigurable(ColorAndFontOptions.getFontConfigurableName());
        if (fontOptions != null) {
          allSettings.select(fontOptions);
        }
      }
    }
  }

  @Override
  protected AbstractFontCombo<?> createPrimaryFontCombo() {
    if (isAdvancedFontFamiliesUI()) {
      return new FontFamilyCombo(true);
    }
    else {
      return super.createPrimaryFontCombo();
    }
  }

  @Override
  protected AbstractFontCombo<?> createSecondaryFontCombo() {
    if (isAdvancedFontFamiliesUI()) {
      return new FontFamilyCombo(false);
    }
    else {
      return super.createSecondaryFontCombo();
    }
  }

  private static boolean isAdvancedFontFamiliesUI() {
    return AppEditorFontOptions.NEW_FONT_SELECTOR;
  }


  private static class MyRegularFontWeightCombo extends FontWeightCombo {
    MyRegularFontWeightCombo() {
      super(false);
    }

    @Override
    @Nullable String getSubFamily(@NotNull FontPreferences preferences) {
      return preferences.getRegularSubFamily();
    }

    @Override
    @NotNull String getRecommendedSubFamily(@NotNull String family) {
      return FontFamilyService.getRecommendedSubFamily(family);
    }
  }


  private class MyBoldFontWeightCombo extends FontWeightCombo {
    MyBoldFontWeightCombo() {
      super(true);
    }

    @Override
    @Nullable String getSubFamily(@NotNull FontPreferences preferences) {
      return preferences.getBoldSubFamily();
    }

    @Override
    @NotNull String getRecommendedSubFamily(@NotNull String family) {
      return FontFamilyService.getRecommendedBoldSubFamily(
        family,
        ObjectUtils.notNull(myRegularWeightCombo.getSelectedSubFamily(),
                            FontFamilyService.getRecommendedSubFamily(family)));
    }
  }
}
