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
package com.intellij.ide.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.QuickChangeLookAndFeel;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceKt;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager;
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.FontComboBox;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
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

    myComponent.myAntialiasingInIDE.setSelectedItem(settings.getIdeAAType());
    myComponent.myAntialiasingInEditor.setSelectedItem(settings.getEditorAAType());
    myComponent.myAntialiasingInIDE.setRenderer(new AAListCellRenderer(false));
    myComponent.myAntialiasingInEditor.setRenderer(new AAListCellRenderer(true));

    @SuppressWarnings("UseOfObsoleteCollectionType") Dictionary<Integer, JComponent> delayDictionary = new Hashtable<>();
    delayDictionary.put(0, new JLabel("0"));
    delayDictionary.put(1200, new JLabel("1200"));
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

    myComponent.myEnableAlphaModeCheckBox.addActionListener(__ -> {
      boolean state = myComponent.myEnableAlphaModeCheckBox.isSelected();
      myComponent.myAlphaModeDelayTextField.setEnabled(state);
      myComponent.myAlphaModeRatioSlider.setEnabled(state);
    });

    myComponent.myAlphaModeRatioSlider.setSize(100, 50);
    @SuppressWarnings("UseOfObsoleteCollectionType")
    Dictionary<Integer, JComponent> dictionary = new Hashtable<>();
    dictionary.put(0, new JLabel("0%"));
    dictionary.put(50, new JLabel("50%"));
    dictionary.put(100, new JLabel("100%"));
    myComponent.myAlphaModeRatioSlider.setLabelTable(dictionary);
    UIUtil.setSliderIsFilled(myComponent.myAlphaModeRatioSlider, Boolean.TRUE);
    myComponent.myAlphaModeRatioSlider.setPaintLabels(true);
    myComponent.myAlphaModeRatioSlider.setPaintTicks(true);
    myComponent.myAlphaModeRatioSlider.setPaintTrack(true);
    myComponent.myAlphaModeRatioSlider.setMajorTickSpacing(50);
    myComponent.myAlphaModeRatioSlider.setMinorTickSpacing(10);
    myComponent.myAlphaModeRatioSlider.addChangeListener(
      __ -> myComponent.myAlphaModeRatioSlider.setToolTipText(myComponent.myAlphaModeRatioSlider.getValue() + "%"));

    myComponent.myTransparencyPanel.setVisible(WindowManagerEx.getInstanceEx().isAlphaModeSupported());

    myComponent.myBackgroundImageButton.setEnabled(ProjectManager.getInstance().getOpenProjects().length > 0);
    myComponent.myBackgroundImageButton.addActionListener(ActionUtil.createActionListener(
      "Images.SetBackgroundImage", myComponent.myPanel, ActionPlaces.UNKNOWN));

    return myComponent.myPanel;
  }

  @Override
  public void apply() {
    initComponent();
    UISettings settings = UISettings.getInstance();
    int _fontSize = getIntValue(myComponent.myFontSizeCombo, settings.getFontSize());
    int _presentationFontSize = getIntValue(myComponent.myPresentationModeFontSize, settings.getPresentationModeFontSize());
    boolean update = false;
    boolean shouldUpdateUI = false;
    String _fontFace = myComponent.myFontCombo.getFontName();
    LafManager lafManager = LafManager.getInstance();
    if (_fontSize != settings.getFontSize() || !Comparing.equal(settings.getFontFace(), _fontFace)) {
      settings.setFontSize(_fontSize);
      settings.setFontFace(_fontFace);
      shouldUpdateUI = true;
      update = true;
    }

    if (_presentationFontSize != settings.getPresentationModeFontSize()) {
      settings.setPresentationModeFontSize(_presentationFontSize);
      shouldUpdateUI = true;
    }

    if (myComponent.myAntialiasingInIDE.getSelectedItem() != settings.getIdeAAType()) {
      settings.setIdeAAType((AntialiasingType)myComponent.myAntialiasingInIDE.getSelectedItem());
      for (Window w : Window.getWindows()) {
        for (JComponent c : UIUtil.uiTraverser(w).filter(JComponent.class)) {
          GraphicsUtil.setAntialiasingType(c, AntialiasingType.getAAHintForSwingComponent());
        }
      }
      shouldUpdateUI = true;
    }

    if (myComponent.myAntialiasingInEditor.getSelectedItem() != settings.getEditorAAType()) {
      settings.setEditorAAType((AntialiasingType)myComponent.myAntialiasingInEditor.getSelectedItem());
      shouldUpdateUI = true;
    }

    settings.setAnimateWindows(myComponent.myAnimateWindowsCheckBox.isSelected());
    update |= settings.getShowToolWindowsNumbers() != myComponent.myWindowShortcutsCheckBox.isSelected();
    settings.setShowToolWindowsNumbers(myComponent.myWindowShortcutsCheckBox.isSelected());
    update |= settings.getHideToolStripes() == myComponent.myShowToolStripesCheckBox.isSelected();
    settings.setHideToolStripes(!myComponent.myShowToolStripesCheckBox.isSelected());
    update |= settings.getShowIconsInMenus() != myComponent.myCbDisplayIconsInMenu.isSelected();
    settings.setShowIconsInMenus(myComponent.myCbDisplayIconsInMenu.isSelected());
    update |= settings.getShowMemoryIndicator() != myComponent.myShowMemoryIndicatorCheckBox.isSelected();
    settings.setShowMemoryIndicator(myComponent.myShowMemoryIndicatorCheckBox.isSelected());
    update |= settings.getAllowMergeButtons() != myComponent.myAllowMergeButtons.isSelected();
    settings.setAllowMergeButtons(myComponent.myAllowMergeButtons.isSelected());
    update |= settings.getCycleScrolling() != myComponent.myCycleScrollingCheckBox.isSelected();
    settings.setCycleScrolling(myComponent.myCycleScrollingCheckBox.isSelected());
    if (settings.getOverrideLafFonts() != myComponent.myOverrideLAFFonts.isSelected()) {
      shouldUpdateUI = true;
      update = true;
    }
    settings.setOverrideLafFonts(myComponent.myOverrideLAFFonts.isSelected());
    settings.setMoveMouseOnDefaultButton(myComponent.myMoveMouseOnDefaultButtonCheckBox.isSelected());
    settings.setHideNavigationOnFocusLoss(myComponent.myHideNavigationPopupsCheckBox.isSelected());
    settings.setDndWithPressedAltOnly(myComponent.myAltDNDCheckBox.isSelected());

    update |= settings.getDisableMnemonics() != myComponent.myDisableMnemonics.isSelected();
    settings.setDisableMnemonics(myComponent.myDisableMnemonics.isSelected());

    update |= settings.getUseSmallLabelsOnTabs() != myComponent.myUseSmallLabelsOnTabs.isSelected();
    settings.setUseSmallLabelsOnTabs(myComponent.myUseSmallLabelsOnTabs.isSelected());

    update |= settings.getWideScreenSupport() != myComponent.myWidescreenLayoutCheckBox.isSelected();
    settings.setWideScreenSupport(myComponent.myWidescreenLayoutCheckBox.isSelected());

    update |= settings.getLeftHorizontalSplit() != myComponent.myLeftLayoutCheckBox.isSelected();
    settings.setLeftHorizontalSplit(myComponent.myLeftLayoutCheckBox.isSelected());

    update |= settings.getRightHorizontalSplit() != myComponent.myRightLayoutCheckBox.isSelected();
    settings.setRightHorizontalSplit(myComponent.myRightLayoutCheckBox.isSelected());

    update |= settings.getSmoothScrolling() != myComponent.mySmoothScrollingCheckBox.isSelected();
    settings.setSmoothScrolling(myComponent.mySmoothScrollingCheckBox.isSelected());

    update |= settings.getNavigateToPreview() != (myComponent.myNavigateToPreviewCheckBox.isVisible() && myComponent.myNavigateToPreviewCheckBox.isSelected());
    settings.setNavigateToPreview(myComponent.myNavigateToPreviewCheckBox.isSelected());

    ColorBlindness blindness = myComponent.myColorBlindnessPanel.getColorBlindness();
    boolean updateEditorScheme = false;
    if (settings.getColorBlindness() != blindness) {
      settings.setColorBlindness(blindness);
      update = true;
      ServiceKt.getStateStore(ApplicationManager.getApplication()).reloadState(DefaultColorSchemesManager.class);
      updateEditorScheme = true;
    }

    update |= settings.getDisableMnemonicsInControls() != myComponent.myDisableMnemonicInControlsCheckBox.isSelected();
    settings.setDisableMnemonicsInControls(myComponent.myDisableMnemonicInControlsCheckBox.isSelected());

    update |= settings.getShowIconInQuickNavigation() != myComponent.myHideIconsInQuickNavigation.isSelected();
    settings.setShowIconInQuickNavigation(myComponent.myHideIconsInQuickNavigation.isSelected());

    if (!Comparing.equal(myComponent.myLafComboBox.getSelectedItem(), lafManager.getCurrentLookAndFeel())) {
      UIManager.LookAndFeelInfo lafInfo = (UIManager.LookAndFeelInfo)myComponent.myLafComboBox.getSelectedItem();
      update = true;
      shouldUpdateUI = false;
      QuickChangeLookAndFeel.switchLafAndUpdateUI(lafManager, lafInfo, true);
    }

    if (shouldUpdateUI) {
      lafManager.updateUI();
      shouldUpdateUI = false;
    }
    // reset to default when unchecked
    if (!myComponent.myOverrideLAFFonts.isSelected()) {
      assert !shouldUpdateUI;
      int defSize = JBUI.Fonts.label().getSize();
      settings.setFontSize(defSize);
      myComponent.myFontSizeCombo.getModel().setSelectedItem(String.valueOf(defSize));
      String defName = JBUI.Fonts.label().getFontName();
      settings.setFontFace(defName);
      myComponent.myFontCombo.setFontName(defName);
    }

    if (WindowManagerEx.getInstanceEx().isAlphaModeSupported()) {
      int delay = -1;
      try {
        delay = Integer.parseInt(myComponent.myAlphaModeDelayTextField.getText());
      }
      catch (NumberFormatException ignored) {
      }
      float ratio = myComponent.myAlphaModeRatioSlider.getValue() / 100f;
      if (myComponent.myEnableAlphaModeCheckBox.isSelected() != settings.getEnableAlphaMode() ||
          delay != -1 && delay != settings.getAlphaModeDelay() || ratio != settings.getAlphaModeRatio()) {
        update = true;
        settings.setEnableAlphaMode(myComponent.myEnableAlphaModeCheckBox.isSelected());
        settings.setAlphaModeDelay(delay);
        settings.setAlphaModeRatio(ratio);
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

    if (updateEditorScheme) {
      ((EditorColorsManagerImpl)EditorColorsManager.getInstance()).schemeChangedOrSwitched(null);
    }
    else {
      EditorFactory.getInstance().refreshAllEditors();
    }
  }

  private static int getIntValue(JComboBox combo, int defaultValue) {
    String temp = (String)combo.getEditor().getItem();
    int value = -1;
    if (temp != null && !temp.trim().isEmpty()) {
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

    if (settings.getOverrideLafFonts()) {
      myComponent.myFontCombo.setFontName(settings.getFontFace());
    } else {
      myComponent.myFontCombo.setFontName(UIUtil.getLabelFont().getFamily());
    }
    // todo migrate
    //myComponent.myAntialiasingCheckBox.setSelected(settings.ANTIALIASING_IN_IDE);
    //myComponent.myLCDRenderingScopeCombo.setSelectedItem(settings.LCD_RENDERING_SCOPE);

    myComponent.myAntialiasingInIDE.setSelectedItem(settings.getIdeAAType());
    myComponent.myAntialiasingInEditor.setSelectedItem(settings.getEditorAAType());

    myComponent.myFontSizeCombo.setSelectedItem(Integer.toString(settings.getFontSize()));
    myComponent.myPresentationModeFontSize.setSelectedItem(Integer.toString(settings.getPresentationModeFontSize()));
    myComponent.myAnimateWindowsCheckBox.setSelected(settings.getAnimateWindows());
    myComponent.myWindowShortcutsCheckBox.setSelected(settings.getShowToolWindowsNumbers());
    myComponent.myShowToolStripesCheckBox.setSelected(!settings.getHideToolStripes());
    myComponent.myCbDisplayIconsInMenu.setSelected(settings.getShowIconsInMenus());
    myComponent.myShowMemoryIndicatorCheckBox.setSelected(settings.getShowMemoryIndicator());
    myComponent.myAllowMergeButtons.setSelected(settings.getAllowMergeButtons());
    myComponent.myCycleScrollingCheckBox.setSelected(settings.getCycleScrolling());

    myComponent.myHideIconsInQuickNavigation.setSelected(settings.getShowIconInQuickNavigation());
    myComponent.myMoveMouseOnDefaultButtonCheckBox.setSelected(settings.getMoveMouseOnDefaultButton());
    myComponent.myHideNavigationPopupsCheckBox.setSelected(settings.getHideNavigationOnFocusLoss());
    myComponent.myAltDNDCheckBox.setSelected(settings.getDndWithPressedAltOnly());
    myComponent.myLafComboBox.setSelectedItem(LafManager.getInstance().getCurrentLookAndFeel());
    myComponent.myOverrideLAFFonts.setSelected(settings.getOverrideLafFonts());
    myComponent.myDisableMnemonics.setSelected(settings.getDisableMnemonics());
    myComponent.myUseSmallLabelsOnTabs.setSelected(settings.getUseSmallLabelsOnTabs());
    myComponent.myWidescreenLayoutCheckBox.setSelected(settings.getWideScreenSupport());
    myComponent.myLeftLayoutCheckBox.setSelected(settings.getLeftHorizontalSplit());
    myComponent.myRightLayoutCheckBox.setSelected(settings.getRightHorizontalSplit());
    myComponent.mySmoothScrollingCheckBox.setSelected(settings.getSmoothScrolling());
    myComponent.myNavigateToPreviewCheckBox.setSelected(settings.getNavigateToPreview());
    myComponent.myNavigateToPreviewCheckBox.setVisible(false);//disabled for a while
    myComponent.myColorBlindnessPanel.setColorBlindness(settings.getColorBlindness());
    myComponent.myDisableMnemonicInControlsCheckBox.setSelected(settings.getDisableMnemonicsInControls());

    boolean alphaModeEnabled = WindowManagerEx.getInstanceEx().isAlphaModeSupported();
    if (alphaModeEnabled) {
      myComponent.myEnableAlphaModeCheckBox.setSelected(settings.getEnableAlphaMode());
    }
    else {
      myComponent.myEnableAlphaModeCheckBox.setSelected(false);
    }
    myComponent.myEnableAlphaModeCheckBox.setEnabled(alphaModeEnabled);
    myComponent.myAlphaModeDelayTextField.setText(Integer.toString(settings.getAlphaModeDelay()));
    myComponent.myAlphaModeDelayTextField.setEnabled(alphaModeEnabled && settings.getEnableAlphaMode());
    int ratio = (int)(settings.getAlphaModeRatio() * 100f);
    myComponent.myAlphaModeRatioSlider.setValue(ratio);
    myComponent.myAlphaModeRatioSlider.setToolTipText(ratio + "%");
    myComponent.myAlphaModeRatioSlider.setEnabled(alphaModeEnabled && settings.getEnableAlphaMode());
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
    isModified |= !Comparing.equal(myComponent.myFontCombo.getFontName(), settings.getFontFace()) && myComponent.myOverrideLAFFonts.isSelected();
    isModified |= !Comparing.equal(myComponent.myFontSizeCombo.getEditor().getItem(), Integer.toString(settings.getFontSize()));

    isModified |= myComponent.myAntialiasingInIDE.getSelectedItem() != settings.getIdeAAType();
    isModified |= myComponent.myAntialiasingInEditor.getSelectedItem() != settings.getEditorAAType();

    isModified |= myComponent.myAnimateWindowsCheckBox.isSelected() != settings.getAnimateWindows();
    isModified |= myComponent.myWindowShortcutsCheckBox.isSelected() != settings.getShowToolWindowsNumbers();
    isModified |= myComponent.myShowToolStripesCheckBox.isSelected() == settings.getHideToolStripes();
    isModified |= myComponent.myCbDisplayIconsInMenu.isSelected() != settings.getShowIconsInMenus();
    isModified |= myComponent.myShowMemoryIndicatorCheckBox.isSelected() != settings.getShowMemoryIndicator();
    isModified |= myComponent.myAllowMergeButtons.isSelected() != settings.getAllowMergeButtons();
    isModified |= myComponent.myCycleScrollingCheckBox.isSelected() != settings.getCycleScrolling();

    isModified |= myComponent.myOverrideLAFFonts.isSelected() != settings.getOverrideLafFonts();

    isModified |= myComponent.myDisableMnemonics.isSelected() != settings.getDisableMnemonics();
    isModified |= myComponent.myDisableMnemonicInControlsCheckBox.isSelected() != settings.getDisableMnemonicsInControls();

    isModified |= myComponent.myUseSmallLabelsOnTabs.isSelected() != settings.getUseSmallLabelsOnTabs();
    isModified |= myComponent.myWidescreenLayoutCheckBox.isSelected() != settings.getWideScreenSupport();
    isModified |= myComponent.myLeftLayoutCheckBox.isSelected() != settings.getLeftHorizontalSplit();
    isModified |= myComponent.myRightLayoutCheckBox.isSelected() != settings.getRightHorizontalSplit();
    isModified |= myComponent.mySmoothScrollingCheckBox.isSelected() != settings.getSmoothScrolling();
    isModified |= myComponent.myNavigateToPreviewCheckBox.isSelected() != settings.getNavigateToPreview();
    isModified |= myComponent.myColorBlindnessPanel.getColorBlindness() != settings.getColorBlindness();

    isModified |= myComponent.myHideIconsInQuickNavigation.isSelected() != settings.getShowIconInQuickNavigation();

    isModified |= !Comparing.equal(myComponent.myPresentationModeFontSize.getEditor().getItem(), Integer.toString(settings.getPresentationModeFontSize()));

    isModified |= myComponent.myMoveMouseOnDefaultButtonCheckBox.isSelected() != settings.getMoveMouseOnDefaultButton();
    isModified |= myComponent.myHideNavigationPopupsCheckBox.isSelected() != settings.getHideNavigationOnFocusLoss();
    isModified |= myComponent.myAltDNDCheckBox.isSelected() != settings.getDndWithPressedAltOnly();
    isModified |= !Comparing.equal(myComponent.myLafComboBox.getSelectedItem(), LafManager.getInstance().getCurrentLookAndFeel());
    if (WindowManagerEx.getInstanceEx().isAlphaModeSupported()) {
      isModified |= myComponent.myEnableAlphaModeCheckBox.isSelected() != settings.getEnableAlphaMode();
      int delay = -1;
      try {
        delay = Integer.parseInt(myComponent.myAlphaModeDelayTextField.getText());
      }
      catch (NumberFormatException ignored) {
      }
      if (delay != -1) {
        isModified |= delay != settings.getAlphaModeDelay();
      }
      float ratio = myComponent.myAlphaModeRatioSlider.getValue() / 100f;
      isModified |= ratio != settings.getAlphaModeRatio();
    }
    int tooltipDelay = myComponent.myInitialTooltipDelaySlider.getValue();
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
    private JCheckBox mySmoothScrollingCheckBox;
    private JCheckBox myNavigateToPreviewCheckBox;
    private ColorBlindnessPanel myColorBlindnessPanel;
    private JComboBox myAntialiasingInIDE;
    private JComboBox myAntialiasingInEditor;
    private JButton myBackgroundImageButton;

    public MyComponent() {
      myOverrideLAFFonts.addActionListener(__ -> updateCombo());
      if (!Registry.is("ide.transparency.mode.for.windows")) {
        myTransparencyPanel.getParent().remove(myTransparencyPanel);
      }
    }

    void updateCombo() {
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
    private static final Object SUBPIXEL_HINT = GraphicsUtil.createAATextInfo(RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
    private static final Object GREYSCALE_HINT = GraphicsUtil.createAATextInfo(RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

    private final boolean useEditorAASettings;

    AAListCellRenderer(boolean useEditorAASettings) {
      this.useEditorAASettings = useEditorAASettings;
    }

    @Override
    public void customize(JList list, AntialiasingType value, int index, boolean selected, boolean hasFocus) {
      if (value == AntialiasingType.SUBPIXEL) {
        GraphicsUtil.generatePropertiesForAntialiasing(SUBPIXEL_HINT, this::setClientProperty);
      }
      else if (value == AntialiasingType.GREYSCALE) {
        GraphicsUtil.generatePropertiesForAntialiasing(GREYSCALE_HINT, this::setClientProperty);
      }
      else if (value == AntialiasingType.OFF) {
        GraphicsUtil.generatePropertiesForAntialiasing(null, this::setClientProperty);
      }

      if (useEditorAASettings) {
        EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
        setFont(new Font(scheme.getEditorFontName(), Font.PLAIN, scheme.getEditorFontSize()));
      }

      setText(String.valueOf(value));
    }
  }
}