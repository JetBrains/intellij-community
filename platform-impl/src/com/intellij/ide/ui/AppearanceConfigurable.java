package com.intellij.ide.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.SortableConfigurable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

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
public class AppearanceConfigurable extends BaseConfigurable implements SearchableConfigurable, SortableConfigurable {
  private MyComponent myComponent;

  public String getDisplayName() {
    return IdeBundle.message("title.appearance");
  }

  public JComponent createComponent() {
    myComponent = new MyComponent();
    DefaultComboBoxModel aModel = new DefaultComboBoxModel(UIUtil.getValidFontNames(false));
    myComponent.myFontCombo.setModel(aModel);

    myComponent.myFontSizeCombo.setModel(new DefaultComboBoxModel(UIUtil.getStandardFontSizes()));

    myComponent.myFontSizeCombo.setEditable(true);
//    myComponent.myLafComboBox=new JComboBox(LafManager.getInstance().getInstalledLookAndFeels());
    myComponent.myLafComboBox.setModel(new DefaultComboBoxModel(LafManager.getInstance().getInstalledLookAndFeels()));
    myComponent.myLafComboBox.setRenderer(new MyLafComboBoxRenderer());


    myComponent.myEnableAlphaModeCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        boolean state = myComponent.myEnableAlphaModeCheckBox.isSelected();
        myComponent.myAlphaModeDelayTextField.setEnabled(state);
        myComponent.myAlphaModeRatioSlider.setEnabled(state);
      }
    });


    myComponent.myAlphaModeRatioSlider.setSize(100, 50);
    Dictionary<Integer, JLabel> dictionary = new Hashtable<Integer, JLabel>();
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

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableAppearance.png");
  }

  public void apply() {
    UISettings settings = UISettings.getInstance();
    String temp = (String)myComponent.myFontSizeCombo.getEditor().getItem();
    int _fontSize = -1;
    if (temp != null && temp.trim().length() > 0) {
      try {
        _fontSize = Integer.parseInt(temp);
      }
      catch (NumberFormatException ex) {
      }
      if (_fontSize <= 0) {
        _fontSize = settings.FONT_SIZE;
      }
    }
    else {
      _fontSize = settings.FONT_SIZE;
    }
    boolean shouldUpdateUI = false;
    String _fontFace = (String)myComponent.myFontCombo.getSelectedItem();
    LafManager lafManager = LafManager.getInstance();
    if (_fontSize != settings.FONT_SIZE || !settings.FONT_FACE.equals(_fontFace)) {
      settings.FONT_SIZE = _fontSize;
      settings.FONT_FACE = _fontFace;
      shouldUpdateUI = true;
    }
    settings.ANIMATE_WINDOWS = myComponent.myAnimateWindowsCheckBox.isSelected();
    boolean update = settings.SHOW_WINDOW_SHORTCUTS != myComponent.myWindowShortcutsCheckBox.isSelected();
    settings.SHOW_WINDOW_SHORTCUTS = myComponent.myWindowShortcutsCheckBox.isSelected();
    update |= settings.HIDE_TOOL_STRIPES != !myComponent.myShowToolStripesCheckBox.isSelected();
    settings.HIDE_TOOL_STRIPES = !myComponent.myShowToolStripesCheckBox.isSelected();
    update |= settings.SHOW_ICONS_IN_MENUS != myComponent.myCbDisplayIconsInMenu.isSelected();
    settings.SHOW_ICONS_IN_MENUS = myComponent.myCbDisplayIconsInMenu.isSelected();
    update |= settings.ALWAYS_SHOW_WINDOW_BUTTONS != myComponent.myAlwaysShowWindowButtonsCheckBox.isSelected();
    settings.ALWAYS_SHOW_WINDOW_BUTTONS = myComponent.myAlwaysShowWindowButtonsCheckBox.isSelected();
    update |= settings.SHOW_MEMORY_INDICATOR != myComponent.myShowMemoryIndicatorCheckBox.isSelected();
    settings.SHOW_MEMORY_INDICATOR = myComponent.myShowMemoryIndicatorCheckBox.isSelected();
    update |= settings.CYCLE_SCROLLING != myComponent.myCycleScrollingCheckBox.isSelected();
    settings.CYCLE_SCROLLING = myComponent.myCycleScrollingCheckBox.isSelected();
    if (settings.OVERRIDE_NONIDEA_LAF_FONTS != myComponent.myOverrideLAFFonts.isSelected()) {
      shouldUpdateUI = true;
    }
    settings.OVERRIDE_NONIDEA_LAF_FONTS = myComponent.myOverrideLAFFonts.isSelected();


    settings.MOVE_MOUSE_ON_DEFAULT_BUTTON = myComponent.myMoveMouseOnDefaultButtonCheckBox.isSelected();


    update |= settings.SHOW_ICONS_IN_QUICK_NAVIGATION != myComponent.myHideIconsInQuickNavigation.isSelected();
    settings.SHOW_ICONS_IN_QUICK_NAVIGATION = myComponent.myHideIconsInQuickNavigation.isSelected();

    boolean shouldRepaintUI = false;
    if (settings.ANTIALIASING_IN_EDITOR != myComponent.myAntialiasingInEditorCheckBox.isSelected()) {
      settings.ANTIALIASING_IN_EDITOR = myComponent.myAntialiasingInEditorCheckBox.isSelected();
      update = shouldRepaintUI = true;
    }

    if (!Comparing.equal(myComponent.myLafComboBox.getSelectedItem(), lafManager.getCurrentLookAndFeel())) {
      update = shouldUpdateUI = true;
      lafManager.setCurrentLookAndFeel((UIManager.LookAndFeelInfo)myComponent.myLafComboBox.getSelectedItem());
    }

    if (shouldUpdateUI) {
      lafManager.updateUI();
    }

    if (shouldRepaintUI) {
      lafManager.repaintUI();
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

    if (update) {
      settings.fireUISettingsChanged();
    }
    myComponent.updateCombo();

  }

  public void reset() {
    UISettings settings = UISettings.getInstance();

    myComponent.myFontCombo.setSelectedItem(settings.FONT_FACE);
    myComponent.myFontSizeCombo.setSelectedItem(Integer.toString(settings.FONT_SIZE));
    myComponent.myAnimateWindowsCheckBox.setSelected(settings.ANIMATE_WINDOWS);
    myComponent.myWindowShortcutsCheckBox.setSelected(settings.SHOW_WINDOW_SHORTCUTS);
    myComponent.myShowToolStripesCheckBox.setSelected(!settings.HIDE_TOOL_STRIPES);
    myComponent.myAlwaysShowWindowButtonsCheckBox.setSelected(settings.ALWAYS_SHOW_WINDOW_BUTTONS);
    myComponent.myCbDisplayIconsInMenu.setSelected(settings.SHOW_ICONS_IN_MENUS);
    myComponent.myShowMemoryIndicatorCheckBox.setSelected(settings.SHOW_MEMORY_INDICATOR);
    myComponent.myCycleScrollingCheckBox.setSelected(settings.CYCLE_SCROLLING);

    myComponent.myHideIconsInQuickNavigation.setSelected(settings.SHOW_ICONS_IN_QUICK_NAVIGATION);
    myComponent.myAntialiasingInEditorCheckBox.setSelected(settings.ANTIALIASING_IN_EDITOR);
    myComponent.myMoveMouseOnDefaultButtonCheckBox.setSelected(settings.MOVE_MOUSE_ON_DEFAULT_BUTTON);
    myComponent.myLafComboBox.setSelectedItem(LafManager.getInstance().getCurrentLookAndFeel());
    myComponent.myOverrideLAFFonts.setSelected(settings.OVERRIDE_NONIDEA_LAF_FONTS);

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
    myComponent.updateCombo();

  }

  public boolean isModified() {
    UISettings settings = UISettings.getInstance();

    boolean isModified = false;
    isModified |= !Comparing.equal(myComponent.myFontCombo.getSelectedItem(), settings.FONT_FACE);
    isModified |= !Comparing.equal(myComponent.myFontSizeCombo.getEditor().getItem(), Integer.toString(settings.FONT_SIZE));
    isModified |= myComponent.myAnimateWindowsCheckBox.isSelected() != settings.ANIMATE_WINDOWS;
    isModified |= myComponent.myWindowShortcutsCheckBox.isSelected() != settings.SHOW_WINDOW_SHORTCUTS;
    isModified |= myComponent.myShowToolStripesCheckBox.isSelected() == settings.HIDE_TOOL_STRIPES;
    isModified |= myComponent.myAlwaysShowWindowButtonsCheckBox.isSelected() != settings.ALWAYS_SHOW_WINDOW_BUTTONS;
    isModified |= myComponent.myCbDisplayIconsInMenu.isSelected() != settings.SHOW_ICONS_IN_MENUS;
    isModified |= myComponent.myShowMemoryIndicatorCheckBox.isSelected() != settings.SHOW_MEMORY_INDICATOR;
    isModified |= myComponent.myCycleScrollingCheckBox.isSelected() != settings.CYCLE_SCROLLING;

    isModified |= myComponent.myOverrideLAFFonts.isSelected() != settings.OVERRIDE_NONIDEA_LAF_FONTS;


    isModified |= myComponent.myHideIconsInQuickNavigation.isSelected() != settings.SHOW_ICONS_IN_QUICK_NAVIGATION;

    isModified |= myComponent.myAntialiasingInEditorCheckBox.isSelected() != settings.ANTIALIASING_IN_EDITOR;
    isModified |= myComponent.myMoveMouseOnDefaultButtonCheckBox.isSelected() != settings.MOVE_MOUSE_ON_DEFAULT_BUTTON;
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

    return isModified;
  }

  private static boolean isModified(JTextField textField, int value) {
    try {
      int fieldValue = Integer.parseInt(textField.getText().trim());
      return fieldValue != value;
    }
    catch (NumberFormatException e) {
      return false;
    }
  }

  private static boolean isModified(JToggleButton checkBox, boolean value) {
    return checkBox.isSelected() != value;
  }

  public void disposeUIResources() {
//    if (myComponent == null)
    myComponent = null;
  }

  public String getHelpTopic() {
    return "preferences.lookFeel";
  }

  public int getSortWeight() {
    return 1;
  }

  private static final class MyLafComboBoxRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      UIManager.LookAndFeelInfo laf = (UIManager.LookAndFeelInfo)value;
      return super.getListCellRendererComponent(list, laf.getName(), index, isSelected, cellHasFocus);
    }
  }

  private static class MyComponent {
    JPanel myPanel;
    private JComboBox myFontCombo;
    private JComboBox myFontSizeCombo;
    private JCheckBox myAnimateWindowsCheckBox;
    private JCheckBox myWindowShortcutsCheckBox;
    private JCheckBox myShowToolStripesCheckBox;
    private JCheckBox myAlwaysShowWindowButtonsCheckBox;
    private JCheckBox myShowMemoryIndicatorCheckBox;
    private JComboBox myLafComboBox;
    private JCheckBox myCycleScrollingCheckBox;

    private JCheckBox myAntialiasingInEditorCheckBox;
    private JCheckBox myMoveMouseOnDefaultButtonCheckBox;
    private JCheckBox myEnableAlphaModeCheckBox;
    private JTextField myAlphaModeDelayTextField;
    private JSlider myAlphaModeRatioSlider;
    private JLabel myFontSizeLabel;
    private JLabel myFontNameLabel;
    private JPanel myTransparencyPanel;
    private JCheckBox myOverrideLAFFonts;
    private JLabel myIDEALafFont;


    private JCheckBox myHideIconsInQuickNavigation;
    private JCheckBox myCbDisplayIconsInMenu;


    public MyComponent() {
      ActionListener updater = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          updateCombo();
        }
      };
      myLafComboBox.addActionListener(updater);
      myOverrideLAFFonts.addActionListener(updater);
      myIDEALafFont.setPreferredSize(new Dimension(myIDEALafFont.getPreferredSize().width, myOverrideLAFFonts.getPreferredSize().height));
      myCbDisplayIconsInMenu.setVisible(SystemInfo.isMac);
    }

    public void updateCombo() {
      UIManager.LookAndFeelInfo selectedLAF = (UIManager.LookAndFeelInfo)myLafComboBox.getSelectedItem();
      //noinspection HardCodedStringLiteral
      boolean isIdeaLAFSelected = selectedLAF != null && selectedLAF.getName().startsWith("IDEA");

      myIDEALafFont.setVisible(isIdeaLAFSelected);
      myOverrideLAFFonts.setVisible(!isIdeaLAFSelected);
      boolean enableChooser = isIdeaLAFSelected || myOverrideLAFFonts.isSelected();

      myFontCombo.setEnabled(enableChooser);
      myFontSizeCombo.setEnabled(enableChooser);
      myFontNameLabel.setEnabled(enableChooser);
      myFontSizeLabel.setEnabled(enableChooser);
    }
  }

  public String getId() {
    return getHelpTopic();
  }

  public boolean clearSearch() {
    return false;
  }

  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }
}
