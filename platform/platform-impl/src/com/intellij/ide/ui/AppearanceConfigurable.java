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
package com.intellij.ide.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.laf.darcula.DarculaInstaller;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceKt;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager;
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.FontComboBox;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Dictionary;
import java.util.Hashtable;

/**
 * @author Eugene Belyaev
 */
public class AppearanceConfigurable extends BaseConfigurable implements SearchableConfigurable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.ui.AppearanceConfigurable");

  private MyComponent myComponent;

  public AppearanceConfigurable() {
    myComponent = new MyComponent();
  }

  private void initComponent() {
    if (myComponent == null)  {
      myComponent = new MyComponent();
    }
  }

  @Override
  public String getDisplayName() {
    return IdeBundle.message("title.appearance");
  }

  @Override
  @NotNull
  public String getId() {
    //noinspection ConstantConditions
    return getHelpTopic();
  }

  @SuppressWarnings("unchecked")
  @Override
  public JComponent createComponent() {
    UISettings settings = UISettings.getInstance();

    initComponent();

    myComponent.myFontSizeCombo.setModel(new DefaultComboBoxModel(UIUtil.getStandardFontSizes()));
    myComponent.myPresentationModeFontSize.setModel(new DefaultComboBoxModel(UIUtil.getStandardFontSizes()));
    myComponent.myFontSizeCombo.setEditable(true);
    myComponent.myPresentationModeFontSize.setEditable(true);

    myComponent.myLafComboBox.setModel(new DefaultComboBoxModel(LafManager.getInstance().getInstalledLookAndFeels()));
    myComponent.myLafComboBox.setRenderer(new LafComboBoxRenderer());

    myComponent.myAntialiasingInIDE.setModel(new DefaultComboBoxModel(AntialiasingType.values()));
    myComponent.myAntialiasingInEditor.setModel(new DefaultComboBoxModel(AntialiasingType.values()));

    myComponent.myAntialiasingInIDE.setSelectedItem(settings.IDE_AA_TYPE);
    myComponent.myAntialiasingInEditor.setSelectedItem(settings.EDITOR_AA_TYPE);
    myComponent.myAntialiasingInIDE.setRenderer(new AAListCellRenderer(false));
    myComponent.myAntialiasingInEditor.setRenderer(new AAListCellRenderer(true));

    @SuppressWarnings("UseOfObsoleteCollectionType") Dictionary<Integer, JComponent> delayDictionary = new Hashtable<>();
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
      @Override
      public void actionPerformed(ActionEvent e) {
        boolean state = myComponent.myEnableAlphaModeCheckBox.isSelected();
        myComponent.myAlphaModeDelayTextField.setEnabled(state);
        myComponent.myAlphaModeRatioSlider.setEnabled(state);
      }
    });

    myComponent.myAlphaModeRatioSlider.setSize(100, 50);
    @SuppressWarnings({"UseOfObsoleteCollectionType"})
    Dictionary<Integer, JComponent> dictionary = new Hashtable<>();
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
      @Override
      public void stateChanged(ChangeEvent e) {
        myComponent.myAlphaModeRatioSlider.setToolTipText(myComponent.myAlphaModeRatioSlider.getValue() + "%");
      }
    });

    myComponent.myTransparencyPanel.setVisible(WindowManagerEx.getInstanceEx().isAlphaModeSupported());

    return myComponent.myPanel;
  }

  @Override
  public void apply() {
    initComponent();
    UISettings settings = UISettings.getInstance();
    int _fontSize = getIntValue(myComponent.myFontSizeCombo, settings.FONT_SIZE);
    int _presentationFontSize = getIntValue(myComponent.myPresentationModeFontSize, settings.PRESENTATION_MODE_FONT_SIZE);
    boolean shouldUpdateUI = false;
    String _fontFace = myComponent.myFontCombo.getFontName();
    LafManager lafManager = LafManager.getInstance();
    if (_fontSize != settings.FONT_SIZE || !Comparing.equal(settings.FONT_FACE, _fontFace)) {
      settings.FONT_SIZE = _fontSize;
      settings.FONT_FACE = _fontFace;
      shouldUpdateUI = true;
    }

    if (_presentationFontSize != settings.PRESENTATION_MODE_FONT_SIZE) {
      settings.PRESENTATION_MODE_FONT_SIZE = _presentationFontSize;
      shouldUpdateUI = true;
    }

    if (myComponent.myAntialiasingInIDE.getSelectedItem() != settings.IDE_AA_TYPE) {
      settings.IDE_AA_TYPE = (AntialiasingType)myComponent.myAntialiasingInIDE.getSelectedItem();
      for (Window w : Window.getWindows()) {
        for (JComponent c : UIUtil.uiTraverser(w).filter(JComponent.class)) {
          c.putClientProperty(SwingUtilities2.AA_TEXT_PROPERTY_KEY, AntialiasingType.getAAHintForSwingComponent());
        }
      }
      shouldUpdateUI = true;
    }

    if (myComponent.myAntialiasingInEditor.getSelectedItem() != settings.EDITOR_AA_TYPE) {
      settings.EDITOR_AA_TYPE = (AntialiasingType)myComponent.myAntialiasingInEditor.getSelectedItem();
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

    ColorBlindness blindness = myComponent.myColorBlindnessPanel.getColorBlindness();
    boolean updateEditorScheme = false;
    if (settings.COLOR_BLINDNESS != blindness) {
      settings.COLOR_BLINDNESS = blindness;
      update = true;
      ServiceKt.getStateStore(ApplicationManager.getApplication()).reloadState(DefaultColorSchemesManager.class);
      updateEditorScheme = true;
    }

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
        SwingUtilities.invokeLater(() -> {
          if (UIUtil.isUnderDarcula()) {
            DarculaInstaller.install();
          } else if (wasDarcula) {
            DarculaInstaller.uninstall();
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

    if (updateEditorScheme) {
      EditorColorsManagerImpl.schemeChangedOrSwitched();
    }
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

  @Override
  public void reset() {
    initComponent();
    UISettings settings = UISettings.getInstance();

    myComponent.myFontCombo.setFontName(settings.FONT_FACE);

    // todo migrate
    //myComponent.myAntialiasingCheckBox.setSelected(settings.ANTIALIASING_IN_IDE);
    //myComponent.myLCDRenderingScopeCombo.setSelectedItem(settings.LCD_RENDERING_SCOPE);

    myComponent.myAntialiasingInIDE.setSelectedItem(settings.IDE_AA_TYPE);
    myComponent.myAntialiasingInEditor.setSelectedItem(settings.EDITOR_AA_TYPE);

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
    myComponent.myColorBlindnessPanel.setColorBlindness(settings.COLOR_BLINDNESS);
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

  public static String antialiasingTypeInEditorAsString (boolean antialiased, LCDRenderingScope scope) {
    if (!antialiased) return "No antialiasing";
    switch (scope) {
      case IDE:
        return "Subpixel";
      case OFF:
      case EXCLUDING_EDITOR:
        return "Greyscale";
    }
    LOG.info("Wrong antialiasing state");
    return "No antialiasing";
  }

  @Override
  public boolean isModified() {
    initComponent();
    UISettings settings = UISettings.getInstance();

    boolean isModified = false;
    isModified |= !Comparing.equal(myComponent.myFontCombo.getFontName(), settings.FONT_FACE);
    isModified |= !Comparing.equal(myComponent.myFontSizeCombo.getEditor().getItem(), Integer.toString(settings.FONT_SIZE));

    isModified |= myComponent.myAntialiasingInIDE.getSelectedItem() != settings.IDE_AA_TYPE;
    isModified |= myComponent.myAntialiasingInEditor.getSelectedItem() != settings.EDITOR_AA_TYPE;

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
    isModified |= myComponent.myColorBlindnessPanel.getColorBlindness() != settings.COLOR_BLINDNESS;

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

  @Override
  public void disposeUIResources() {
    myComponent = null;
  }

  @Override
  public String getHelpTopic() {
    return "preferences.lookFeel";
  }

  private static class MyComponent {
    private JPanel myPanel;
    private FontComboBox myFontCombo;
    private JComboBox myFontSizeCombo;
    private JCheckBox myAnimateWindowsCheckBox;
    private JCheckBox myWindowShortcutsCheckBox;
    private JCheckBox myShowToolStripesCheckBox;
    private JCheckBox myShowMemoryIndicatorCheckBox;
    private JComboBox myLafComboBox;
    private JCheckBox myCycleScrollingCheckBox;

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
    private ColorBlindnessPanel myColorBlindnessPanel;
    private JComboBox myAntialiasingInIDE;
    private JComboBox myAntialiasingInEditor;

    public MyComponent() {
      myOverrideLAFFonts.addActionListener( new ActionListener() {
        @Override
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

  private static class AAListCellRenderer extends ListCellRendererWrapper<AntialiasingType> {
    private static final SwingUtilities2.AATextInfo SUBPIXEL_HINT = new SwingUtilities2.AATextInfo(
      RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB, UIUtil.getLcdContrastValue());
    private static final SwingUtilities2.AATextInfo GREYSCALE_HINT = new SwingUtilities2.AATextInfo(
      RenderingHints.VALUE_TEXT_ANTIALIAS_ON, UIUtil.getLcdContrastValue());

    private final boolean useEditorAASettings;

    public AAListCellRenderer(boolean useEditorAASettings) {
      super();
      this.useEditorAASettings = useEditorAASettings;
    }

    @Override
    public void customize(JList list, AntialiasingType value, int index, boolean selected, boolean hasFocus) {
      if (value == AntialiasingType.SUBPIXEL) {
        setClientProperty(SwingUtilities2.AA_TEXT_PROPERTY_KEY, SUBPIXEL_HINT);
      }
      else if (value == AntialiasingType.GREYSCALE) {
        setClientProperty(SwingUtilities2.AA_TEXT_PROPERTY_KEY, GREYSCALE_HINT);
      }
      else if (value == AntialiasingType.OFF) {
        setClientProperty(SwingUtilities2.AA_TEXT_PROPERTY_KEY, null);
      }

      if (useEditorAASettings) {
        EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
        setFont(new Font(scheme.getEditorFontName(), Font.PLAIN, scheme.getEditorFontSize()));
      }

      setText(String.valueOf(value));
    }
  }
}