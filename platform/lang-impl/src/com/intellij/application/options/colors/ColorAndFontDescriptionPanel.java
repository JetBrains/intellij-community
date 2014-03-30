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

import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.ui.ColorPanel;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author cdr
 */
public class ColorAndFontDescriptionPanel extends JPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.application.options.colors.ColorAndFontDescriptionPanel");

  private final ColorPanel myBackgroundChooser = new ColorPanel();
  private final ColorPanel myForegroundChooser = new ColorPanel();
  private final ColorPanel myEffectsColorChooser = new ColorPanel();
  private final ColorPanel myErrorStripeColorChooser = new ColorPanel();

  private final JCheckBox myCbBackground = new JCheckBox(ApplicationBundle.message("checkbox.color.background"));
  private final JCheckBox myCbForeground = new JCheckBox(ApplicationBundle.message("checkbox.color.foreground"));
  private final JCheckBox myCbEffects = new JCheckBox(ApplicationBundle.message("checkbox.color.effects"));
  private final JCheckBox myCbErrorStripe = new JCheckBox(ApplicationBundle.message("checkbox.color.error.stripe.mark"));

  private static final String BORDERED_EFFECT = ApplicationBundle.message("combobox.effect.bordered");
  private static final String UNDERSCORED_EFFECT = ApplicationBundle.message("combobox.effect.underscored");
  private static final String BOLD_UNDERSCORED_EFFECT = ApplicationBundle.message("combobox.effect.boldunderscored");
  private static final String UNDERWAVED_EFFECT = ApplicationBundle.message("combobox.effect.underwaved");
  private static final String STRIKEOUT_EFFECT = ApplicationBundle.message("combobox.effect.strikeout");
  private static final String BOLD_DOTTED_LINE_EFFECT = ApplicationBundle.message("combobox.effect.bold.dottedline");

  private final JComboBox myEffectsCombo = new JComboBox(
    new String[]{UNDERSCORED_EFFECT, BOLD_UNDERSCORED_EFFECT, UNDERWAVED_EFFECT, BORDERED_EFFECT, STRIKEOUT_EFFECT, BOLD_DOTTED_LINE_EFFECT});

  private final JCheckBox myCbBold = new JCheckBox(ApplicationBundle.message("checkbox.font.bold"));
  private final JCheckBox myCbItalic = new JCheckBox(ApplicationBundle.message("checkbox.font.italic"));
  private boolean updatingEffects;
  private ActionListener myActionListener;
  private JLabel myLabelFont;

  private final JLabel myInheritanceLabel = new JLabel("X");
  private final JCheckBox myInheritAttributesBox = new JCheckBox(ApplicationBundle.message("label.inherit.attributes"));


  public ColorAndFontDescriptionPanel() {
    super(new BorderLayout());

    JPanel settingsPanel = createSettingsPanel();

    add(settingsPanel, BorderLayout.CENTER);
    setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 4));

    myEffectsCombo.setRenderer(new ListCellRendererWrapper<String>() {
      @Override
      public void customize(JList list, String value, int index, boolean selected, boolean hasFocus) {
        setText(value != null ? value : "Invalid");
      }
    });
  }

  private JPanel createSettingsPanel() {
    JPanel settingsPanel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.weightx = 1;
    gbConstraints.weighty = 0;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.insets = new Insets(4, 4, 4, 4);

    settingsPanel.add(createFontSettingsPanel(), gbConstraints);
    settingsPanel.add(createColorSettingsPanel(), gbConstraints);
    gbConstraints.weighty = 1;
    settingsPanel.add(new TailPanel(), gbConstraints);
    settingsPanel.add(createInheritancePanel(), gbConstraints);
    return settingsPanel;
  }

  private JPanel createInheritancePanel() {
    JPanel inheritancePanel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 0;
    gbConstraints.weightx = 1;
    gbConstraints.gridwidth = 2;
    inheritancePanel.add(myInheritAttributesBox, gbConstraints);
    gbConstraints.gridy = 1;
    gbConstraints.insets = new Insets(0, 20, 0,0);
    inheritancePanel.add(myInheritanceLabel, gbConstraints);
    myInheritAttributesBox.addActionListener(new ActionListener(){
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myActionListener != null) {
          myActionListener.actionPerformed(e);
        }
      }
    });
    return inheritancePanel;
  }

  private JPanel createFontSettingsPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 0;
    gbConstraints.gridwidth = 1;
    gbConstraints.gridy = 1;
    gbConstraints.gridx = 1;
    myLabelFont = new JLabel(ApplicationBundle.message("label.font.type"));
    panel.add(myLabelFont, gbConstraints);
    gbConstraints.gridx = 2;
    panel.add(myCbBold, gbConstraints);
    gbConstraints.gridx = 3;
    panel.add(myCbItalic, gbConstraints);
    gbConstraints.gridx = 4;
    gbConstraints.weightx = 1;
    panel.add(new TailPanel(), gbConstraints);

    myCbBold.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myActionListener != null) {
          myActionListener.actionPerformed(e);
        }
      }
    });
    myCbItalic.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myActionListener != null) {
          myActionListener.actionPerformed(e);
        }
      }
    });
    return panel;
  }

  private JPanel createColorSettingsPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());

    GridBagConstraints gc = new GridBagConstraints();
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.weightx = 0;
    gc.weighty = 1;
    gc.gridwidth = 1;

    gc.gridx = 0;
    gc.gridy = 0;
    panel.add(myCbForeground, gc);

    gc.gridy++;
    panel.add(myCbBackground, gc);

    gc.gridy++;
    panel.add(myCbErrorStripe, gc);

    gc.gridy++;
    panel.add(myCbEffects, gc);

    gc.gridy++;
    gc.insets = new Insets(0, 10, 0, 0);
    gc.weighty = 0;
    panel.add(myEffectsCombo, gc);

    gc.gridx = 1;
    gc.gridy = 0;
    panel.add(myForegroundChooser, gc);

    gc.gridy++;
    panel.add(myBackgroundChooser, gc);

    gc.gridy++;
    panel.add(myErrorStripeColorChooser, gc);

    gc.gridy += 2;
    panel.add(myEffectsColorChooser, gc);

    gc.gridx = 2;
    gc.gridy = 0;
    gc.weightx = 1;
    panel.add(new TailPanel(), gc);

    gc.gridy++;
    panel.add(new TailPanel(), gc);

    gc.gridy++;
    panel.add(new TailPanel(), gc);

    gc.gridy++;
    panel.add(new TailPanel(), gc);


    myEffectsColorChooser.addActionListener(
      new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          if (myActionListener != null) {
            myActionListener.actionPerformed(e);
          }
        }
      }
    );

    myForegroundChooser.addActionListener(
      new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          if (myActionListener != null) {
            myActionListener.actionPerformed(e);
          }
        }
      }
    );

    myCbForeground.addActionListener(
      new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          myForegroundChooser.setEnabled(myCbForeground.isSelected());
          if (myActionListener != null) {
            myActionListener.actionPerformed(e);
          }
        }
      }
    );

    myBackgroundChooser.addActionListener(
      new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          if (myActionListener != null) {
            myActionListener.actionPerformed(e);
          }
        }
      }
    );
    myErrorStripeColorChooser.addActionListener(
      new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          if (myActionListener != null) {
            myActionListener.actionPerformed(e);
          }
        }
      }
    );

    myCbBackground.addActionListener(
      new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          myBackgroundChooser.setEnabled(myCbBackground.isSelected());
          if (myActionListener != null) {
            myActionListener.actionPerformed(e);
          }
        }
      }
    );
    myCbErrorStripe.addActionListener(
      new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          myErrorStripeColorChooser.setEnabled(myCbErrorStripe.isSelected());
          if (myActionListener != null) {
            myActionListener.actionPerformed(e);
          }
        }
      }
    );


    myCbEffects.addActionListener(
      new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          boolean checked = myCbEffects.isSelected();
          myEffectsColorChooser.setEnabled(checked);
          myEffectsCombo.setEnabled(checked);
          if (myActionListener != null) {
            myActionListener.actionPerformed(e);
          }
        }
      }
    );

    myEffectsCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (!updatingEffects) {
          if (myActionListener != null) {
            myActionListener.actionPerformed(e);
          }
        }
      }
    });

    return panel;
  }

  public void setActionListener(ActionListener actionListener) {
    myActionListener = actionListener;
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
      colorPanel.setSelectedColor(Color.white);
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
      updatingEffects = true;
      if (effectType == EffectType.BOXED) {
        myEffectsCombo.setSelectedItem(BORDERED_EFFECT);
      }
      else if (effectType == EffectType.LINE_UNDERSCORE) {
        myEffectsCombo.setSelectedItem(UNDERSCORED_EFFECT);
      }
      else if (effectType == EffectType.WAVE_UNDERSCORE) {
        myEffectsCombo.setSelectedItem(UNDERWAVED_EFFECT);
      }
      else if (effectType == EffectType.BOLD_LINE_UNDERSCORE) {
        myEffectsCombo.setSelectedItem(BOLD_UNDERSCORED_EFFECT);
      }
      else if (effectType == EffectType.STRIKEOUT) {
        myEffectsCombo.setSelectedItem(STRIKEOUT_EFFECT);
      }
      else if (effectType == EffectType.BOLD_DOTTED_LINE) {
        myEffectsCombo.setSelectedItem(BOLD_DOTTED_LINE_EFFECT);
      }
      else {
        myEffectsCombo.setSelectedItem(null);
      }
      updatingEffects = false;
    }
    else {
      myEffectsCombo.setEnabled(false);
    }
    setInheritanceInfo(description);
  }


  private void setInheritanceInfo(ColorAndFontDescription description) {
    Pair<ColorSettingsPage, AttributesDescriptor> baseDescriptor = description.getBaseAttributeDescriptor();
    if (baseDescriptor != null && baseDescriptor.second.getDisplayName() != null) {
      String attrName = baseDescriptor.second.getDisplayName();
      ColorSettingsPage settingsPage = baseDescriptor.first;
      String pageName = "?";
      if (settingsPage != null) {
        pageName = settingsPage.getDisplayName();
      }
      String tooltipText = attrName + " (" + pageName + ")";
      String labelText = tooltipText;
      if (labelText.length() > 30 && pageName.length() >= 4) {
        labelText = attrName + " (" + pageName.substring(0, 4) + "...)";
      }
      myInheritanceLabel.setText(labelText);
      myInheritanceLabel.setToolTipText(tooltipText);
      myInheritanceLabel.setForeground(myLabelFont.getForeground());
      myInheritanceLabel.setEnabled(description.isInherited());
      myInheritAttributesBox.setEnabled(true);
      myInheritAttributesBox.setSelected(description.isInherited());
      setEditEnabled(!description.isInherited(), description);
    }
    else {
      myInheritanceLabel.setText("X");
      myInheritanceLabel.setIcon(null);
      myInheritanceLabel.setDisabledIcon(null);
      myInheritanceLabel.setEnabled(true);
      myInheritanceLabel.setForeground(myLabelFont.getBackground());
      myInheritAttributesBox.setEnabled(false);
      myInheritAttributesBox.setSelected(false);
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
          Object effectType = myEffectsCombo.getModel().getSelectedItem();

          if (BORDERED_EFFECT.equals(effectType)) {
            description.setEffectType(EffectType.BOXED);
          }
          else if (UNDERWAVED_EFFECT.equals(effectType)) {
            description.setEffectType(EffectType.WAVE_UNDERSCORE);
          }
          else if (UNDERSCORED_EFFECT.equals(effectType)) {
            description.setEffectType(EffectType.LINE_UNDERSCORE);
          }
          else if (BOLD_UNDERSCORED_EFFECT.equals(effectType)) {
            description.setEffectType(EffectType.BOLD_LINE_UNDERSCORE);
          }
          else if (STRIKEOUT_EFFECT.equals(effectType)) {
            description.setEffectType(EffectType.STRIKEOUT);
          }
          else if (BOLD_DOTTED_LINE_EFFECT.equals(effectType)) {
            description.setEffectType(EffectType.BOLD_DOTTED_LINE);
          }
          else {
            LOG.assertTrue(false);
          }
        }
      }
      description.apply(scheme);
    }
  }
}
