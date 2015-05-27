/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.laf.darcula.DarculaInstaller;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Dictionary;
import java.util.Hashtable;

/**
 * @author Eugene Belyaev
 */
public class AppearanceConfigurable extends BaseConfigurable implements SearchableConfigurable {
  private MyComponent myComponent;

  public String getDisplayName() {
    return IdeBundle.message("title.appearance");
  }

  public AppearanceConfigurable() {
    myComponent = new MyComponent();
  }

  private void initComponent() {
    if (myComponent == null)  {
      myComponent = new MyComponent();
    }

  }

  public JComponent createComponent() {
    initComponent();
    DefaultComboBoxModel aModel = new DefaultComboBoxModel(UIUtil.getValidFontNames(Registry.is("ide.settings.appearance.font.family.only")));
    myComponent.myFontCombo.setModel(aModel);
    myComponent.myFontSizeCombo.setModel(new DefaultComboBoxModel(UIUtil.getStandardFontSizes()));
    myComponent.myPresentationModeFontSize.setModel(new DefaultComboBoxModel(UIUtil.getStandardFontSizes()));
    myComponent.myFontSizeCombo.setEditable(true);
    myComponent.myPresentationModeFontSize.setEditable(true);

    myComponent.myLafComboBox.setModel(new DefaultComboBoxModel(LafManager.getInstance().getInstalledLookAndFeels()));
    myComponent.myLafComboBox.setRenderer(new LafComboBoxRenderer());

    myComponent.myAntialiasingCheckBox.setSelected(UISettings.getInstance().ANTIALIASING_IN_IDE);
    myComponent.myLCDRenderingScopeCombo.setEnabled(UISettings.getInstance().ANTIALIASING_IN_IDE);

    myComponent.myAntialiasingCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myComponent.myLCDRenderingScopeCombo.setEnabled(myComponent.myAntialiasingCheckBox.isSelected());
      }
    });

    myComponent.myLCDRenderingScopeCombo.setModel(new DefaultComboBoxModel(LCDRenderingScope.values()));
    myComponent.myLCDRenderingScopeCombo.setSelectedItem(UISettings.getInstance().LCD_RENDERING_SCOPE);

    Dictionary<Integer, JComponent> delayDictionary = new Hashtable<Integer, JComponent>();
    delayDictionary.put(new Integer(0), new JLabel("0"));
    delayDictionary.put(new Integer(1200), new JLabel("1200"));
    //delayDictionary.put(new Integer(2400), new JLabel("2400"));
    myComponent.myInitialTooltipDelaySlider.setLabelTable(delayDictionary);
    UIUtil.setSliderIsFilled(myComponent.myInitialTooltipDelaySlider, Boolean.TRUE);
    myComponent.myInitialTooltipDelaySlider.setMinimum(0);
    myComponent.myInitialTooltipDelaySlider.setMaximum(1200);
    myComponent.myInitialTooltipDelaySlider.setPaintLabels(true);
    myComponent.myInitialTooltipDelaySlider.setPaintTicks(true);
    myComponent.myInitialTooltipDelaySlider.setPaintTrack(true);
    myComponent.myInitialTooltipDelaySlider.setMajorTickSpacing(1200);
    myComponent.myInitialTooltipDelaySlider.setMinorTickSpacing(100);

    myComponent.myEnableAlphaModeCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        boolean state = myComponent.myEnableAlphaModeCheckBox.isSelected();
        myComponent.myAlphaModeDelayTextField.setEnabled(state);
        myComponent.myAlphaModeRatioSlider.setEnabled(state);
      }
    });

    myComponent.myAlphaModeRatioSlider.setSize(100, 50);
    @SuppressWarnings({"UseOfObsoleteCollectionType"})
    Dictionary<Integer, JComponent> dictionary = new Hashtable<Integer, JComponent>();
    dictionary.put(new Integer(0), new JLabel("0%"));
    dictionary.put(new Integer(50), new JLabel("50%"));
    dictionary.put(new Integer(100), new JLabel("100%"));
    myComponent.myAlphaModeRatioSlider.setLabelTable(dictionary);
    UIUtil.setSliderIsFilled(myComponent.myAlphaModeRatioSlider, Boolean.TRUE);
    myComponent.myAlphaModeRatioSlider.setPaintLabels(true);
    myComponent.myAlphaModeRatioSlider.setPaintTicks(true);
    myComponent.myAlphaModeRatioSlider.setPaintTrack(true);
    myComponent.myAlphaModeRatioSlider.setMajorTickSpacing(50);
    myComponent.myAlphaModeRatioSlider.setMinorTickSpacing(10);
    myComponent.myAlphaModeRatioSlider.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        myComponent.myAlphaModeRatioSlider.setToolTipText(myComponent.myAlphaModeRatioSlider.getValue() + "%");
      }
    });

    myComponent.myTransparencyPanel.setVisible(WindowManagerEx.getInstanceEx().isAlphaModeSupported());

    return myComponent.myPanel;
  }

  public void apply() {
    initComponent();
    UISettings settings = UISettings.getInstance();
    int _fontSize = getIntValue(myComponent.myFontSizeCombo, settings.FONT_SIZE);
    int _presentationFontSize = getIntValue(myComponent.myPresentationModeFontSize, settings.PRESENTATION_MODE_FONT_SIZE);
    boolean shouldUpdateUI = false;
    String _fontFace = (String)myComponent.myFontCombo.getSelectedItem();
    LafManager lafManager = LafManager.getInstance();
    if (_fontSize != settings.FONT_SIZE || !settings.FONT_FACE.equals(_fontFace)) {
      settings.FONT_SIZE = _fontSize;
      settings.FONT_FACE = _fontFace;
      shouldUpdateUI = true;
    }

    if (_presentationFontSize != settings.PRESENTATION_MODE_FONT_SIZE) {
      settings.PRESENTATION_MODE_FONT_SIZE = _presentationFontSize;
      shouldUpdateUI = true;
    }

    if (myComponent.myAntialiasingCheckBox.isSelected() != settings.ANTIALIASING_IN_IDE) {
      settings.ANTIALIASING_IN_IDE = myComponent.myAntialiasingCheckBox.isSelected();
      shouldUpdateUI = true;
    }

    if (!myComponent.myLCDRenderingScopeCombo.getSelectedItem().equals(settings.LCD_RENDERING_SCOPE)) {
      settings.LCD_RENDERING_SCOPE = (LCDRenderingScope)myComponent.myLCDRenderingScopeCombo.getSelectedItem();
      shouldUpdateUI = true;
    }

    settings.ANIMATE_WINDOWS = myComponent.myAnimateWindowsCheckBox.isSelected();
    boolean update = settings.SHOW_TOOL_WINDOW_NUMBERS != myComponent.myWindowShortcutsCheckBox.isSelected();
    settings.SHOW_TOOL_WINDOW_NUMBERS = myComponent.myWindowShortcutsCheckBox.isSelected();
    update |= settings.HIDE_TOOL_STRIPES != !myComponent.myShowToolStripesCheckBox.isSelected();
    settings.HIDE_TOOL_STRIPES = !myComponent.myShowToolStripesCheckBox.isSelected();
    update |= settings.SHOW_ICONS_IN_MENUS != myComponent.myCbDisplayIconsInMenu.isSelected();
    settings.SHOW_ICONS_IN_MENUS = myComponent.myCbDisplayIconsInMenu.isSelected();
    update |= settings.SHOW_MEMORY_INDICATOR != myComponent.myShowMemoryIndicatorCheckBox.isSelected();
    settings.SHOW_MEMORY_INDICATOR = myComponent.myShowMemoryIndicatorCheckBox.isSelected();
    update |= settings.ALLOW_MERGE_BUTTONS != myComponent.myAllowMergeButtons.isSelected();
    settings.ALLOW_MERGE_BUTTONS = myComponent.myAllowMergeButtons.isSelected();
    update |= settings.CYCLE_SCROLLING != myComponent.myCycleScrollingCheckBox.isSelected();
    settings.CYCLE_SCROLLING = myComponent.myCycleScrollingCheckBox.isSelected();
    if (settings.OVERRIDE_NONIDEA_LAF_FONTS != myComponent.myOverrideLAFFonts.isSelected()) {
      shouldUpdateUI = true;
    }
    settings.OVERRIDE_NONIDEA_LAF_FONTS = myComponent.myOverrideLAFFonts.isSelected();
    settings.MOVE_MOUSE_ON_DEFAULT_BUTTON = myComponent.myMoveMouseOnDefaultButtonCheckBox.isSelected();
    settings.HIDE_NAVIGATION_ON_FOCUS_LOSS = myComponent.myHideNavigationPopupsCheckBox.isSelected();
    settings.DND_WITH_PRESSED_ALT_ONLY = myComponent.myAltDNDCheckBox.isSelected();

    update |= settings.DISABLE_MNEMONICS != myComponent.myDisableMnemonics.isSelected();
    settings.DISABLE_MNEMONICS = myComponent.myDisableMnemonics.isSelected();

    update |= settings.USE_SMALL_LABELS_ON_TABS != myComponent.myUseSmallLabelsOnTabs.isSelected();
    settings.USE_SMALL_LABELS_ON_TABS = myComponent.myUseSmallLabelsOnTabs.isSelected();

    update |= settings.WIDESCREEN_SUPPORT != myComponent.myWidescreenLayoutCheckBox.isSelected();
    settings.WIDESCREEN_SUPPORT = myComponent.myWidescreenLayoutCheckBox.isSelected();

    update |= settings.LEFT_HORIZONTAL_SPLIT != myComponent.myLeftLayoutCheckBox.isSelected();
    settings.LEFT_HORIZONTAL_SPLIT = myComponent.myLeftLayoutCheckBox.isSelected();

    update |= settings.RIGHT_HORIZONTAL_SPLIT != myComponent.myRightLayoutCheckBox.isSelected();
    settings.RIGHT_HORIZONTAL_SPLIT = myComponent.myRightLayoutCheckBox.isSelected();

    update |= settings.NAVIGATE_TO_PREVIEW != (myComponent.myNavigateToPreviewCheckBox.isVisible() && myComponent.myNavigateToPreviewCheckBox.isSelected());
    settings.NAVIGATE_TO_PREVIEW = myComponent.myNavigateToPreviewCheckBox.isSelected();

    update |= settings.DISABLE_MNEMONICS_IN_CONTROLS != myComponent.myDisableMnemonicInControlsCheckBox.isSelected();
    settings.DISABLE_MNEMONICS_IN_CONTROLS = myComponent.myDisableMnemonicInControlsCheckBox.isSelected();

    update |= settings.SHOW_ICONS_IN_QUICK_NAVIGATION != myComponent.myHideIconsInQuickNavigation.isSelected();
    settings.SHOW_ICONS_IN_QUICK_NAVIGATION = myComponent.myHideIconsInQuickNavigation.isSelected();

    if (!Comparing.equal(myComponent.myLafComboBox.getSelectedItem(), lafManager.getCurrentLookAndFeel())) {
      final UIManager.LookAndFeelInfo lafInfo = (UIManager.LookAndFeelInfo)myComponent.myLafComboBox.getSelectedItem();
      if (lafManager.checkLookAndFeel(lafInfo)) {
        update = shouldUpdateUI = true;
        final boolean wasDarcula = UIUtil.isUnderDarcula();
        lafManager.setCurrentLookAndFeel(lafInfo);
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            if (UIUtil.isUnderDarcula()) {
              DarculaInstaller.install();
            } else if (wasDarcula) {
              DarculaInstaller.uninstall();
            }
          }
        });
      }
    }


    if (shouldUpdateUI) {
      lafManager.updateUI();
    }

    if (WindowManagerEx.getInstanceEx().isAlphaModeSupported()) {
      int delay = -1;
      try {
        delay = Integer.parseInt(myComponent.myAlphaModeDelayTextField.getText());
      }
      catch (NumberFormatException ignored) {
      }
      float ratio = myComponent.myAlphaModeRatioSlider.getValue() / 100f;
      if (myComponent.myEnableAlphaModeCheckBox.isSelected() != settings.ENABLE_ALPHA_MODE ||
          delay != -1 && delay != settings.ALPHA_MODE_DELAY || ratio != settings.ALPHA_MODE_RATIO) {
        update = true;
        settings.ENABLE_ALPHA_MODE = myComponent.myEnableAlphaModeCheckBox.isSelected();
        settings.ALPHA_MODE_DELAY = delay;
        settings.ALPHA_MODE_RATIO = ratio;
      }
    }
    int tooltipDelay = Math.min(myComponent.myInitialTooltipDelaySlider.getValue(), 5000);
    if (tooltipDelay != Registry.intValue("ide.tooltip.initialDelay")) {
      update = true;
      Registry.get("ide.tooltip.initialDelay").setValue(tooltipDelay);
    }

    if (update) {
      settings.fireUISettingsChanged();
    }
    myComponent.updateCombo();

    EditorUtil.reinitSettings();
  }

  private static int getIntValue(JComboBox combo, int defaultValue) {
    String temp = (String)combo.getEditor().getItem();
    int value = -1;
    if (temp != null && temp.trim().length() > 0) {
      try {
        value = Integer.parseInt(temp);
      }
      catch (NumberFormatException ignore) {
      }
      if (value <= 0) {
        value = defaultValue;
      }
    }
    else {
      value = defaultValue;
    }
    return value;
  }

  public void reset() {
    initComponent();
    UISettings settings = UISettings.getInstance();

    myComponent.myFontCombo.setSelectedItem(settings.FONT_FACE);
    myComponent.myAntialiasingCheckBox.setSelected(settings.ANTIALIASING_IN_IDE);
    myComponent.myLCDRenderingScopeCombo.setSelectedItem(settings.LCD_RENDERING_SCOPE);
    myComponent.myFontSizeCombo.setSelectedItem(Integer.toString(settings.FONT_SIZE));
    myComponent.myPresentationModeFontSize.setSelectedItem(Integer.toString(settings.PRESENTATION_MODE_FONT_SIZE));
    myComponent.myAnimateWindowsCheckBox.setSelected(settings.ANIMATE_WINDOWS);
    myComponent.myWindowShortcutsCheckBox.setSelected(settings.SHOW_TOOL_WINDOW_NUMBERS);
    myComponent.myShowToolStripesCheckBox.setSelected(!settings.HIDE_TOOL_STRIPES);
    myComponent.myCbDisplayIconsInMenu.setSelected(settings.SHOW_ICONS_IN_MENUS);
    myComponent.myShowMemoryIndicatorCheckBox.setSelected(settings.SHOW_MEMORY_INDICATOR);
    myComponent.myAllowMergeButtons.setSelected(settings.ALLOW_MERGE_BUTTONS);
    myComponent.myCycleScrollingCheckBox.setSelected(settings.CYCLE_SCROLLING);

    myComponent.myHideIconsInQuickNavigation.setSelected(settings.SHOW_ICONS_IN_QUICK_NAVIGATION);
    myComponent.myMoveMouseOnDefaultButtonCheckBox.setSelected(settings.MOVE_MOUSE_ON_DEFAULT_BUTTON);
    myComponent.myHideNavigationPopupsCheckBox.setSelected(settings.HIDE_NAVIGATION_ON_FOCUS_LOSS);
    myComponent.myAltDNDCheckBox.setSelected(settings.DND_WITH_PRESSED_ALT_ONLY);
    myComponent.myLafComboBox.setSelectedItem(LafManager.getInstance().getCurrentLookAndFeel());
    myComponent.myOverrideLAFFonts.setSelected(settings.OVERRIDE_NONIDEA_LAF_FONTS);
    myComponent.myDisableMnemonics.setSelected(settings.DISABLE_MNEMONICS);
    myComponent.myUseSmallLabelsOnTabs.setSelected(settings.USE_SMALL_LABELS_ON_TABS);
    myComponent.myWidescreenLayoutCheckBox.setSelected(settings.WIDESCREEN_SUPPORT);
    myComponent.myLeftLayoutCheckBox.setSelected(settings.LEFT_HORIZONTAL_SPLIT);
    myComponent.myRightLayoutCheckBox.setSelected(settings.RIGHT_HORIZONTAL_SPLIT);
    myComponent.myNavigateToPreviewCheckBox.setSelected(settings.NAVIGATE_TO_PREVIEW);
    myComponent.myNavigateToPreviewCheckBox.setVisible(false);//disabled for a while
    myComponent.myDisableMnemonicInControlsCheckBox.setSelected(settings.DISABLE_MNEMONICS_IN_CONTROLS);

    boolean alphaModeEnabled = WindowManagerEx.getInstanceEx().isAlphaModeSupported();
    if (alphaModeEnabled) {
      myComponent.myEnableAlphaModeCheckBox.setSelected(settings.ENABLE_ALPHA_MODE);
    }
    else {
      myComponent.myEnableAlphaModeCheckBox.setSelected(false);
    }
    myComponent.myEnableAlphaModeCheckBox.setEnabled(alphaModeEnabled);
    myComponent.myAlphaModeDelayTextField.setText(Integer.toString(settings.ALPHA_MODE_DELAY));
    myComponent.myAlphaModeDelayTextField.setEnabled(alphaModeEnabled && settings.ENABLE_ALPHA_MODE);
    int ratio = (int)(settings.ALPHA_MODE_RATIO * 100f);
    myComponent.myAlphaModeRatioSlider.setValue(ratio);
    myComponent.myAlphaModeRatioSlider.setToolTipText(ratio + "%");
    myComponent.myAlphaModeRatioSlider.setEnabled(alphaModeEnabled && settings.ENABLE_ALPHA_MODE);
    myComponent.myInitialTooltipDelaySlider.setValue(Registry.intValue("ide.tooltip.initialDelay"));
    myComponent.updateCombo();
  }

  public boolean isModified() {
    initComponent();
    UISettings settings = UISettings.getInstance();

    boolean isModified = false;
    isModified |= !Comparing.equal(myComponent.myFontCombo.getSelectedItem(), settings.FONT_FACE);
    isModified |= !Comparing.equal(myComponent.myFontSizeCombo.getEditor().getItem(), Integer.toString(settings.FONT_SIZE));
    isModified |= myComponent.myAntialiasingCheckBox.isSelected() != settings.ANTIALIASING_IN_IDE;
    isModified |= !myComponent.myLCDRenderingScopeCombo.getSelectedItem().equals(settings.LCD_RENDERING_SCOPE);
    isModified |= myComponent.myAnimateWindowsCheckBox.isSelected() != settings.ANIMATE_WINDOWS;
    isModified |= myComponent.myWindowShortcutsCheckBox.isSelected() != settings.SHOW_TOOL_WINDOW_NUMBERS;
    isModified |= myComponent.myShowToolStripesCheckBox.isSelected() == settings.HIDE_TOOL_STRIPES;
    isModified |= myComponent.myCbDisplayIconsInMenu.isSelected() != settings.SHOW_ICONS_IN_MENUS;
    isModified |= myComponent.myShowMemoryIndicatorCheckBox.isSelected() != settings.SHOW_MEMORY_INDICATOR;
    isModified |= myComponent.myAllowMergeButtons.isSelected() != settings.ALLOW_MERGE_BUTTONS;
    isModified |= myComponent.myCycleScrollingCheckBox.isSelected() != settings.CYCLE_SCROLLING;

    isModified |= myComponent.myOverrideLAFFonts.isSelected() != settings.OVERRIDE_NONIDEA_LAF_FONTS;

    isModified |= myComponent.myDisableMnemonics.isSelected() != settings.DISABLE_MNEMONICS;
    isModified |= myComponent.myDisableMnemonicInControlsCheckBox.isSelected() != settings.DISABLE_MNEMONICS_IN_CONTROLS;

    isModified |= myComponent.myUseSmallLabelsOnTabs.isSelected() != settings.USE_SMALL_LABELS_ON_TABS;
    isModified |= myComponent.myWidescreenLayoutCheckBox.isSelected() != settings.WIDESCREEN_SUPPORT;
    isModified |= myComponent.myLeftLayoutCheckBox.isSelected() != settings.LEFT_HORIZONTAL_SPLIT;
    isModified |= myComponent.myRightLayoutCheckBox.isSelected() != settings.RIGHT_HORIZONTAL_SPLIT;
    isModified |= myComponent.myNavigateToPreviewCheckBox.isSelected() != settings.NAVIGATE_TO_PREVIEW;

    isModified |= myComponent.myHideIconsInQuickNavigation.isSelected() != settings.SHOW_ICONS_IN_QUICK_NAVIGATION;

    isModified |= !Comparing.equal(myComponent.myPresentationModeFontSize.getEditor().getItem(), Integer.toString(settings.PRESENTATION_MODE_FONT_SIZE));

    isModified |= myComponent.myMoveMouseOnDefaultButtonCheckBox.isSelected() != settings.MOVE_MOUSE_ON_DEFAULT_BUTTON;
    isModified |= myComponent.myHideNavigationPopupsCheckBox.isSelected() != settings.HIDE_NAVIGATION_ON_FOCUS_LOSS;
    isModified |= myComponent.myAltDNDCheckBox.isSelected() != settings.DND_WITH_PRESSED_ALT_ONLY;
    isModified |= !Comparing.equal(myComponent.myLafComboBox.getSelectedItem(), LafManager.getInstance().getCurrentLookAndFeel());
    if (WindowManagerEx.getInstanceEx().isAlphaModeSupported()) {
      isModified |= myComponent.myEnableAlphaModeCheckBox.isSelected() != settings.ENABLE_ALPHA_MODE;
      int delay = -1;
      try {
        delay = Integer.parseInt(myComponent.myAlphaModeDelayTextField.getText());
      }
      catch (NumberFormatException ignored) {
      }
      if (delay != -1) {
        isModified |= delay != settings.ALPHA_MODE_DELAY;
      }
      float ratio = myComponent.myAlphaModeRatioSlider.getValue() / 100f;
      isModified |= ratio != settings.ALPHA_MODE_RATIO;
    }
    int tooltipDelay = -1;
    tooltipDelay = myComponent.myInitialTooltipDelaySlider.getValue();
    isModified |=  tooltipDelay != Registry.intValue("ide.tooltip.initialDelay");

    return isModified;
  }

  public void disposeUIResources() {
    myComponent = null;
  }

  public String getHelpTopic() {
    return "preferences.lookFeel";
  }

  private static class MyComponent {
    private JPanel myPanel;
    private JComboBox myFontCombo;
    private JComboBox myFontSizeCombo;
    private JCheckBox myAnimateWindowsCheckBox;
    private JCheckBox myWindowShortcutsCheckBox;
    private JCheckBox myShowToolStripesCheckBox;
    private JCheckBox myShowMemoryIndicatorCheckBox;
    private JComboBox myLafComboBox;
    private JCheckBox myCycleScrollingCheckBox;
    private JBCheckBox myAntialiasingCheckBox;
    private ComboBox myLCDRenderingScopeCombo;

    private JCheckBox myMoveMouseOnDefaultButtonCheckBox;
    private JCheckBox myEnableAlphaModeCheckBox;
    private JTextField myAlphaModeDelayTextField;
    private JSlider myAlphaModeRatioSlider;
    private JLabel myFontSizeLabel;
    private JLabel myFontNameLabel;
    private JPanel myTransparencyPanel;
    private JCheckBox myOverrideLAFFonts;

    private JCheckBox myHideIconsInQuickNavigation;
    private JCheckBox myCbDisplayIconsInMenu;
    private JCheckBox myDisableMnemonics;
    private JCheckBox myDisableMnemonicInControlsCheckBox;
    private JCheckBox myHideNavigationPopupsCheckBox;
    private JCheckBox myAltDNDCheckBox;
    private JCheckBox myAllowMergeButtons;
    private JBCheckBox myUseSmallLabelsOnTabs;
    private JBCheckBox myWidescreenLayoutCheckBox;
    private JCheckBox myLeftLayoutCheckBox;
    private JCheckBox myRightLayoutCheckBox;
    private JSlider myInitialTooltipDelaySlider;
    private ComboBox myPresentationModeFontSize;
    private JCheckBox myNavigateToPreviewCheckBox;
    private JCheckBox myAllowStatusBar;
    private JCheckBox myAllowLineNumbers;
    private JCheckBox myAllowAnnotations;

    public MyComponent() {
      myOverrideLAFFonts.addActionListener( new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          updateCombo();
        }
      });
      if (!Registry.is("ide.transparency.mode.for.windows")) {
        myTransparencyPanel.getParent().remove(myTransparencyPanel);
      }
    }

    public void updateCombo() {
      boolean enableChooser = myOverrideLAFFonts.isSelected();

      myFontCombo.setEnabled(enableChooser);
      myFontSizeCombo.setEnabled(enableChooser);
      myFontNameLabel.setEnabled(enableChooser);
      myFontSizeLabel.setEnabled(enableChooser);
    }

    private void createUIComponents() {
      myFontSizeCombo = new ComboBox();
      myPresentationModeFontSize = new ComboBox();
    }
  }

  @NotNull
  public String getId() {
    //noinspection ConstantConditions
    return getHelpTopic();
  }

  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }
}
