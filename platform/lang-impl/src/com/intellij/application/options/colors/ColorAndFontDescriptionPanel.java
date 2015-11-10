/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.*;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.FontUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author cdr
 */
public class ColorAndFontDescriptionPanel extends JPanel {

  private JPanel myPanel;

  private ColorPanel myBackgroundChooser;
  private ColorPanel myForegroundChooser;
  private ColorPanel myEffectsColorChooser;
  private ColorPanel myErrorStripeColorChooser;

  private JBCheckBox myCbBackground;
  private JBCheckBox myCbForeground;
  private JBCheckBox myCbEffects;
  private JBCheckBox myCbErrorStripe;

  private Map<String, EffectType> myEffectsMap;
  {
    Map<String, EffectType> map = ContainerUtil.newLinkedHashMap();
    map.put(ApplicationBundle.message("combobox.effect.underscored"), EffectType.LINE_UNDERSCORE);
    map.put(ApplicationBundle.message("combobox.effect.boldunderscored"), EffectType.BOLD_LINE_UNDERSCORE);
    map.put(ApplicationBundle.message("combobox.effect.underwaved"), EffectType.WAVE_UNDERSCORE);
    map.put(ApplicationBundle.message("combobox.effect.bordered"), EffectType.BOXED);
    map.put(ApplicationBundle.message("combobox.effect.strikeout"), EffectType.STRIKEOUT);
    map.put(ApplicationBundle.message("combobox.effect.bold.dottedline"), EffectType.BOLD_DOTTED_LINE);
    myEffectsMap = Collections.unmodifiableMap(map);
  }
  private JComboBox myEffectsCombo;
  private final EffectsComboModel myEffectsModel;

  private JBCheckBox myCbBold;
  private JBCheckBox myCbItalic;
  private JLabel myLabelFont;
  private JTextPane myInheritanceLabel;

  private JBCheckBox myInheritAttributesBox;

  public ColorAndFontDescriptionPanel() {
    super(new BorderLayout());
    add(myPanel, BorderLayout.CENTER);

    setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 4));
    myEffectsModel = new EffectsComboModel(ContainerUtil.newArrayList(myEffectsMap.keySet()));
    //noinspection unchecked
    myEffectsCombo.setModel(myEffectsModel);
    //noinspection unchecked
    myEffectsCombo.setRenderer(new ListCellRendererWrapper<String>() {
      @Override
      public void customize(JList list, String value, int index, boolean selected, boolean hasFocus) {
        setText(value != null ? value : "<invalid>");
      }
    });

    ActionListener actionListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        onSettingsChanged(e);
      }
    };
    for (JBCheckBox c : new JBCheckBox[]{myCbBackground, myCbForeground, myCbEffects, myCbErrorStripe, myCbItalic, myCbBold, myInheritAttributesBox}) {
      c.addActionListener(actionListener);
    }
    for (ColorPanel c : new ColorPanel[]{myBackgroundChooser, myForegroundChooser, myEffectsColorChooser, myErrorStripeColorChooser}) {
      c.addActionListener(actionListener);
    }
    myEffectsCombo.addActionListener(actionListener);
    Messages.configureMessagePaneUi(myInheritanceLabel, "<html>", null);
    myInheritanceLabel.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        onHyperLinkClicked(e);
      }
    });
    myInheritanceLabel.setBorder(BorderFactory.createEmptyBorder());
    myLabelFont.setVisible(false); // hide for now as it doesn't look that good
  }

  protected void onHyperLinkClicked(HyperlinkEvent e) {
  }

  protected void onSettingsChanged(ActionEvent e) {
    myErrorStripeColorChooser.setEnabled(myCbErrorStripe.isSelected());
    myForegroundChooser.setEnabled(myCbForeground.isSelected());
    myBackgroundChooser.setEnabled(myCbBackground.isSelected());
    myEffectsColorChooser.setEnabled(myCbEffects.isSelected());
    myEffectsCombo.setEnabled(myCbEffects.isSelected());
  }

  public void resetDefault() {
    myLabelFont.setEnabled(false);
    myCbBold.setSelected(false);
    myCbBold.setEnabled(false);
    myCbItalic.setSelected(false);
    myCbItalic.setEnabled(false);
    updateColorChooser(myCbForeground, myForegroundChooser, false, false, null);
    updateColorChooser(myCbBackground, myBackgroundChooser, false, false, null);
    updateColorChooser(myCbErrorStripe, myErrorStripeColorChooser, false, false, null);
    updateColorChooser(myCbEffects, myEffectsColorChooser, false, false, null);
    myEffectsCombo.setEnabled(false);
    myInheritanceLabel.setVisible(false);
    myInheritAttributesBox.setVisible(false);
  }

  private static void updateColorChooser(JCheckBox checkBox,
                                         ColorPanel colorPanel,
                                         boolean isEnabled,
                                         boolean isChecked,
                                         @Nullable Color color) {
    checkBox.setEnabled(isEnabled);
    checkBox.setSelected(isChecked);
    if (color != null) {
      colorPanel.setSelectedColor(color);
    }
    else {
      colorPanel.setSelectedColor(JBColor.WHITE);
    }
    colorPanel.setEnabled(isChecked);
  }

  public void reset(ColorAndFontDescription description) {
    if (description.isFontEnabled()) {
      myLabelFont.setEnabled(true);
      myCbBold.setEnabled(true);
      myCbItalic.setEnabled(true);
      int fontType = description.getFontType();
      myCbBold.setSelected((fontType & Font.BOLD) != 0);
      myCbItalic.setSelected((fontType & Font.ITALIC) != 0);
    }
    else {
      myLabelFont.setEnabled(false);
      myCbBold.setSelected(false);
      myCbBold.setEnabled(false);
      myCbItalic.setSelected(false);
      myCbItalic.setEnabled(false);
    }

    updateColorChooser(myCbForeground, myForegroundChooser, description.isForegroundEnabled(),
                       description.isForegroundChecked(), description.getForegroundColor());

    updateColorChooser(myCbBackground, myBackgroundChooser, description.isBackgroundEnabled(),
                       description.isBackgroundChecked(), description.getBackgroundColor());

    updateColorChooser(myCbErrorStripe, myErrorStripeColorChooser, description.isErrorStripeEnabled(),
                       description.isErrorStripeChecked(), description.getErrorStripeColor());

    EffectType effectType = description.getEffectType();
    updateColorChooser(myCbEffects, myEffectsColorChooser, description.isEffectsColorEnabled(),
                       description.isEffectsColorChecked(), description.getEffectColor());

    if (description.isEffectsColorEnabled() && description.isEffectsColorChecked()) {
      myEffectsCombo.setEnabled(true);
      myEffectsModel.setEffectName(ContainerUtil.reverseMap(myEffectsMap).get(effectType));
    }
    else {
      myEffectsCombo.setEnabled(false);
    }
    setInheritanceInfo(description);
    myLabelFont.setEnabled(myCbBold.isEnabled() || myCbItalic.isEnabled());
  }


  private void setInheritanceInfo(ColorAndFontDescription description) {
    Pair<ColorSettingsPage, AttributesDescriptor> baseDescriptor = description.getBaseAttributeDescriptor();
    if (baseDescriptor != null && baseDescriptor.second.getDisplayName() != null) {
      String attrName = baseDescriptor.second.getDisplayName();
      String attrLabel = attrName.replaceAll(ColorOptionsTree.NAME_SEPARATOR, FontUtil.rightArrow(UIUtil.getLabelFont()));
      ColorSettingsPage settingsPage = baseDescriptor.first;
      String style = "<div style=\"text-align:right\" vertical-align=\"top\">";
      String tooltipText;
      String labelText;
      if (settingsPage != null) {
        String pageName = settingsPage.getDisplayName();
        tooltipText = "'" + attrLabel + "' from<br>'" + pageName + "' section";
        labelText = style + "'" + attrLabel + "'<br>of <a href=\"" + attrName + "\">" + pageName;
      }
      else {
        tooltipText = attrLabel;
        labelText = style + attrLabel + "<br>&nbsp;";
      }

      myInheritanceLabel.setVisible(true);
      myInheritanceLabel.setText(labelText);
      myInheritanceLabel.setToolTipText(tooltipText);
      myInheritanceLabel.setEnabled(true);
      myInheritAttributesBox.setVisible(true);
      myInheritAttributesBox.setEnabled(true);
      myInheritAttributesBox.setSelected(description.isInherited());
      setEditEnabled(!description.isInherited(), description);
    }
    else {
      myInheritanceLabel.setVisible(false);
      myInheritAttributesBox.setSelected(false);
      myInheritAttributesBox.setVisible(false);
      setEditEnabled(true, description);
    }
  }

  private void setEditEnabled(boolean isEditEnabled, ColorAndFontDescription description) {
    myCbBackground.setEnabled(isEditEnabled && description.isBackgroundEnabled());
    myCbForeground.setEnabled(isEditEnabled && description.isForegroundEnabled());
    myCbBold.setEnabled(isEditEnabled && description.isFontEnabled());
    myCbItalic.setEnabled(isEditEnabled && description.isFontEnabled());
    myCbEffects.setEnabled(isEditEnabled && description.isEffectsColorEnabled());
    myCbErrorStripe.setEnabled(isEditEnabled && description.isErrorStripeEnabled());
    myErrorStripeColorChooser.setEditable(isEditEnabled);
    myEffectsColorChooser.setEditable(isEditEnabled);
    myForegroundChooser.setEditable(isEditEnabled);
    myBackgroundChooser.setEditable(isEditEnabled);
  }

  public void apply(ColorAndFontDescription description, EditorColorsScheme scheme) {
    if (description != null) {
      description.setInherited(myInheritAttributesBox.isSelected());
      if (description.isInherited()) {
        TextAttributes baseAttributes = description.getBaseAttributes();
        if (baseAttributes != null) {
          description.setFontType(baseAttributes.getFontType());
          description.setForegroundChecked(baseAttributes.getForegroundColor() != null);
          description.setForegroundColor(baseAttributes.getForegroundColor());
          description.setBackgroundChecked(baseAttributes.getBackgroundColor() != null);
          description.setBackgroundColor(baseAttributes.getBackgroundColor());
          description.setErrorStripeChecked(baseAttributes.getErrorStripeColor() != null);
          description.setErrorStripeColor(baseAttributes.getErrorStripeColor());
          description.setEffectColor(baseAttributes.getEffectColor());
          description.setEffectType(baseAttributes.getEffectType());
          description.setEffectsColorChecked(baseAttributes.getEffectColor() != null);
        }
        else {
          description.setInherited(false);
        }
        reset(description);
      }
      else {
        setInheritanceInfo(description);
        int fontType = Font.PLAIN;
        if (myCbBold.isSelected()) fontType |= Font.BOLD;
        if (myCbItalic.isSelected()) fontType |= Font.ITALIC;
        description.setFontType(fontType);
        description.setForegroundChecked(myCbForeground.isSelected());
        description.setForegroundColor(myForegroundChooser.getSelectedColor());
        description.setBackgroundChecked(myCbBackground.isSelected());
        description.setBackgroundColor(myBackgroundChooser.getSelectedColor());
        description.setErrorStripeChecked(myCbErrorStripe.isSelected());
        description.setErrorStripeColor(myErrorStripeColorChooser.getSelectedColor());
        description.setEffectsColorChecked(myCbEffects.isSelected());
        description.setEffectColor(myEffectsColorChooser.getSelectedColor());

        if (myEffectsCombo.isEnabled()) {
          String effectType = (String)myEffectsCombo.getModel().getSelectedItem();
          description.setEffectType(myEffectsMap.get(effectType));
        }
      }
      description.apply(scheme);
    }
  }

  private static class EffectsComboModel extends CollectionComboBoxModel<String> {
    public EffectsComboModel(List<String> names) {
      super(names);
    }

    /**
     * Set the current effect name when a text attribute selection changes without notifying the listeners since otherwise it will
     * be considered as an actual change and lead to unnecessary evens including 'read-only scheme' check.
     *
     * @param effectName
     */
    public void setEffectName(@NotNull String effectName) {
      mySelection = effectName;
    }
  }
}
