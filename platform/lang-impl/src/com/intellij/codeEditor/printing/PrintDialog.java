/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.codeEditor.printing;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.MappingListCellRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;

class PrintDialog extends DialogWrapper {
  private JRadioButton myRbCurrentFile = null;
  private JRadioButton myRbSelectedText = null;
  private JRadioButton myRbCurrentPackage = null;
  private JCheckBox myCbIncludeSubpackages = null;

  private JComboBox myPaperSizeCombo = null;

  private JCheckBox myCbColorPrinting = null;
  private JCheckBox myCbSyntaxPrinting = null;
  private JCheckBox myCbPrintAsGraphics = null;

  private JRadioButton myRbPortrait = null;
  private JRadioButton myRbLandscape = null;

  private JComboBox myFontNameCombo = null;
  private JComboBox myFontSizeCombo = null;

  private JCheckBox myCbLineNumbers = null;

  private JRadioButton myRbNoWrap = null;
  private JRadioButton myRbWrapAtWordBreaks = null;

  private JTextField myTopMarginField = null;
  private JTextField myBottomMarginField = null;
  private JTextField myLeftMarginField = null;
  private JTextField myRightMarginField = null;

  private JCheckBox myCbDrawBorder = null;

  private JTextField myLineTextField1 = null;
  private JComboBox myLinePlacementCombo1 = null;
  private JComboBox myLineAlignmentCombo1 = null;
  private JTextField myLineTextField2 = null;
  private JComboBox myLinePlacementCombo2 = null;
  private JComboBox myLineAlignmentCombo2 = null;
  private JComboBox myFooterFontSizeCombo = null;
  private JComboBox myFooterFontNameCombo = null;
  private String myFileName = null;
  private String myDirectoryName = null;
  private final boolean isSelectedTextEnabled;

  private static final Map<Object, String> PLACEMENT_MAP = new HashMap<Object, String>();
  private static final Map<Object, String> ALIGNMENT_MAP = new HashMap<Object, String>();
  private final String mySelectedText;

  static {
    PLACEMENT_MAP.put(PrintSettings.HEADER, CodeEditorBundle.message("print.header.placement.header"));
    PLACEMENT_MAP.put(PrintSettings.FOOTER, CodeEditorBundle.message("print.header.placement.footer"));

    ALIGNMENT_MAP.put(PrintSettings.LEFT, CodeEditorBundle.message("print.header.alignment.left"));
    ALIGNMENT_MAP.put(PrintSettings.CENTER, CodeEditorBundle.message("print.header.alignment.center"));
    ALIGNMENT_MAP.put(PrintSettings.RIGHT, CodeEditorBundle.message("print.header.alignment.right"));
  }


  public PrintDialog(String fileName, String directoryName, String selectedText, Project project) {
    super(project, true);
    mySelectedText = selectedText;
    setOKButtonText(CodeEditorBundle.message("print.print.button"));
    myFileName = fileName;
    myDirectoryName = directoryName;
    this.isSelectedTextEnabled = selectedText != null;
    setTitle(CodeEditorBundle.message("print.title"));
    init();
  }


  @Override
  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(4,8,8,4));
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.gridy = 0;
    gbConstraints.gridx = 0;
    gbConstraints.gridwidth = 1;
    gbConstraints.gridheight = 1;
    gbConstraints.weightx = 1;

    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.insets = new Insets(0,0,0,0);

    myRbCurrentFile = new JRadioButton(CodeEditorBundle.message("print.file.name.radio", (myFileName != null ? myFileName : "")));
    panel.add(myRbCurrentFile, gbConstraints);

    myRbSelectedText = new JRadioButton(mySelectedText != null ? mySelectedText : CodeEditorBundle.message("print.selected.text.radio"));
    gbConstraints.gridy++;
    gbConstraints.insets = new Insets(0,0,0,0);
    panel.add(myRbSelectedText, gbConstraints);

    myRbCurrentPackage = new JRadioButton(
      CodeEditorBundle.message("print.all.files.in.directory.radio", (myDirectoryName != null ? myDirectoryName : "")));
    gbConstraints.gridy++;
    gbConstraints.insets = new Insets(0,0,0,0);
    panel.add(myRbCurrentPackage, gbConstraints);

    myCbIncludeSubpackages = new JCheckBox(CodeEditorBundle.message("print.include.subdirectories.checkbox"));
    gbConstraints.gridy++;
    gbConstraints.insets = new Insets(0,20,0,0);
    panel.add(myCbIncludeSubpackages, gbConstraints);

    ButtonGroup buttonGroup = new ButtonGroup();
    buttonGroup.add(myRbCurrentFile);
    buttonGroup.add(myRbSelectedText);
    buttonGroup.add(myRbCurrentPackage);

    ActionListener actionListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myCbIncludeSubpackages.setEnabled(myRbCurrentPackage.isSelected());
      }
    };

    myRbCurrentFile.addActionListener(actionListener);
    myRbSelectedText.addActionListener(actionListener);
    myRbCurrentPackage.addActionListener(actionListener);

    return panel;
  }

  @Override
  protected JComponent createCenterPanel() {
    TabbedPaneWrapper tabbedPaneWrapper = new TabbedPaneWrapper(myDisposable);
    tabbedPaneWrapper.addTab(CodeEditorBundle.message("print.settings.tab"), createPrintSettingsPanel());
    tabbedPaneWrapper.addTab(CodeEditorBundle.message("print.header.footer.tab"), createHeaderAndFooterPanel());
    tabbedPaneWrapper.addTab(CodeEditorBundle.message("print.advanced.tab"), createAdvancedPanel());
    return tabbedPaneWrapper.getComponent();
  }

  private JPanel createPrintSettingsPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    panel.setBorder(BorderFactory.createEmptyBorder(8,8,4,4));
    gbConstraints.gridy = 0;
    gbConstraints.gridx = 0;
    gbConstraints.gridwidth = 1;
    gbConstraints.gridheight = 1;
    gbConstraints.weightx = 1;
    gbConstraints.weighty = 0;
    gbConstraints.insets = new Insets(0, 8, 6, 4);
    gbConstraints.fill = GridBagConstraints.BOTH;

    JLabel paperSizeLabel = new MyLabel(CodeEditorBundle.message("print.settings.paper.size.label"));
    panel.add(paperSizeLabel, gbConstraints);
    myPaperSizeCombo = createPageSizesCombo();
    gbConstraints.gridx = 1;
    gbConstraints.gridwidth = 2;
    panel.add(myPaperSizeCombo, gbConstraints);

    JLabel fontLabel = new MyLabel(CodeEditorBundle.message("print.settings.font.label"));
    gbConstraints.gridx = 0;
    gbConstraints.gridwidth = 1;
    gbConstraints.gridy++;
    panel.add(fontLabel, gbConstraints);

    myFontNameCombo = createFontNamesComboBox();
    gbConstraints.gridx = 1;
    panel.add(myFontNameCombo, gbConstraints);

    myFontSizeCombo = createFontSizesComboBox();
    gbConstraints.gridx = 2;
    panel.add(myFontSizeCombo, gbConstraints);

    myCbLineNumbers = new JCheckBox(CodeEditorBundle.message("print.settings.show.line.numbers.checkbox"));
    gbConstraints.gridx = 0;
    gbConstraints.gridwidth = 3;
    gbConstraints.gridy++;
    panel.add(myCbLineNumbers, gbConstraints);

    myCbDrawBorder = new JCheckBox(CodeEditorBundle.message("print.settings.draw.border.checkbox"));
    gbConstraints.gridy++;
    panel.add(myCbDrawBorder, gbConstraints);

    gbConstraints.insets = new Insets(0, 0, 6, 4);
    gbConstraints.gridx = 0;
    gbConstraints.gridwidth = 3;
    gbConstraints.gridy++;
    panel.add(createStyleAndLayoutPanel(), gbConstraints);

    gbConstraints.gridy++;
    gbConstraints.weighty = 1;
    panel.add(new MyTailPanel(), gbConstraints);
    return panel;
  }

  private JPanel createAdvancedPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    panel.setBorder(BorderFactory.createEmptyBorder(8,8,4,4));
    gbConstraints.gridy = 0;
    gbConstraints.gridx = 0;
    gbConstraints.gridwidth = 1;
    gbConstraints.gridheight = 1;
    gbConstraints.weightx = 1;
    gbConstraints.weighty = 0;
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.insets = new Insets(0, 0, 6, 4);

    panel.add(createWrappingPanel(), gbConstraints);

    gbConstraints.gridy++;
    panel.add(createMarginsPanel(), gbConstraints);

    gbConstraints.gridy++;
    gbConstraints.weighty = 1;
    panel.add(new MyTailPanel(), gbConstraints);

    return panel;
  }

  private JPanel createStyleAndLayoutPanel() {
    JPanel panel = new JPanel(new GridLayout(1, 2));
    panel.add(createOrientationPanel());
    panel.add(createStylePanel());
    return panel;
  }

  private JPanel createOrientationPanel() {
    JPanel panel = new JPanel();
    panel.setBorder(IdeBorderFactory.createTitledBorder(CodeEditorBundle.message("print.orientation.group"), true));
    panel.setLayout(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.gridy = 0;
    gbConstraints.gridx = 0;
    gbConstraints.gridwidth = 1;
    gbConstraints.gridheight = 1;
    gbConstraints.weightx = 1;
    gbConstraints.fill = GridBagConstraints.BOTH;

    myRbPortrait = new JRadioButton(CodeEditorBundle.message("print.orientation.portrait.radio"));
    panel.add(myRbPortrait, gbConstraints);

    myRbLandscape = new JRadioButton(CodeEditorBundle.message("print.orientation.landscape.radio"));
    gbConstraints.gridy++;
    panel.add(myRbLandscape, gbConstraints);

    ButtonGroup buttonGroup = new ButtonGroup();
    buttonGroup.add(myRbPortrait);
    buttonGroup.add(myRbLandscape);

    return panel;
  }

  private JPanel createStylePanel() {
    JPanel panel = new JPanel();
    panel.setBorder(IdeBorderFactory.createTitledBorder(CodeEditorBundle.message("print.style.group"), true));
    panel.setLayout(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.gridy = 0;
    gbConstraints.gridx = 0;
    gbConstraints.gridwidth = 1;
    gbConstraints.gridheight = 1;
    gbConstraints.weightx = 1;
    gbConstraints.fill = GridBagConstraints.BOTH;

    myCbColorPrinting = new JCheckBox(CodeEditorBundle.message("print.style.color.printing.checkbox"));
    panel.add(myCbColorPrinting, gbConstraints);

    myCbSyntaxPrinting = new JCheckBox(CodeEditorBundle.message("print.style.syntax.printing.checkbox"));
    gbConstraints.gridy++;
    panel.add(myCbSyntaxPrinting, gbConstraints);

    myCbPrintAsGraphics = new JCheckBox(CodeEditorBundle.message("print.style.print.as.graphics.checkbox"));
    gbConstraints.gridy++;
    panel.add(myCbPrintAsGraphics, gbConstraints);

    return panel;
  }

  private JPanel createWrappingPanel() {
    JPanel panel = new JPanel();
    panel.setBorder(IdeBorderFactory.createTitledBorder(CodeEditorBundle.message("print.wrapping.group"), true));
    panel.setLayout(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.gridy = 0;
    gbConstraints.gridx = 0;
    gbConstraints.gridwidth = 1;
    gbConstraints.gridheight = 1;
    gbConstraints.weightx = 1;
    gbConstraints.fill = GridBagConstraints.BOTH;

    myRbNoWrap = new JRadioButton(CodeEditorBundle.message("print.wrapping.none.radio"));
    panel.add(myRbNoWrap, gbConstraints);

    myRbWrapAtWordBreaks = new JRadioButton(CodeEditorBundle.message("print.wrapping.word.breaks.radio"));
    gbConstraints.gridy++;
    panel.add(myRbWrapAtWordBreaks, gbConstraints);

    ButtonGroup buttonGroup = new ButtonGroup();
    buttonGroup.add(myRbNoWrap);
    buttonGroup.add(myRbWrapAtWordBreaks);

    return panel;
  }

  private JPanel createMarginsPanel() {
    JPanel panel = new JPanel();
    panel.setBorder(IdeBorderFactory.createTitledBorder(CodeEditorBundle.message("print.margins.group"), true));
    panel.setLayout(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.gridy = 0;
    gbConstraints.gridx = 0;
    gbConstraints.gridwidth = 1;
    gbConstraints.gridheight = 1;
    gbConstraints.weightx = 1;
    gbConstraints.fill = GridBagConstraints.BOTH;

    panel.add(new MyLabel(CodeEditorBundle.message("print.margins.top.label")), gbConstraints);
    myTopMarginField = new MyTextField(6);
    gbConstraints.weightx = 0;
    gbConstraints.gridx = 1;
    panel.add(myTopMarginField, gbConstraints);

    gbConstraints.weightx = 1;
    gbConstraints.gridx = 2;
    panel.add(new MyLabel(CodeEditorBundle.message("print.margins.bottom.label")), gbConstraints);
    myBottomMarginField = new MyTextField(6);
    gbConstraints.weightx = 0;
    gbConstraints.gridx = 3;
    panel.add(myBottomMarginField, gbConstraints);

    gbConstraints.weightx = 1;
    gbConstraints.gridx = 0;
    gbConstraints.gridy++;
    panel.add(new MyLabel(CodeEditorBundle.message("print.margins.left.label")), gbConstraints);
    myLeftMarginField = new MyTextField(6);
    gbConstraints.weightx = 0;
    gbConstraints.gridx = 1;
    panel.add(myLeftMarginField, gbConstraints);

    gbConstraints.weightx = 1;
    gbConstraints.gridx = 2;
    panel.add(new MyLabel(CodeEditorBundle.message("print.margins.right.label")), gbConstraints);
    myRightMarginField = new MyTextField(6);
    gbConstraints.weightx = 0;
    gbConstraints.gridx = 3;
    panel.add(myRightMarginField, gbConstraints);

    return panel;
  }

  private JPanel createHeaderAndFooterPanel() {
//    JPanel panel = createGroupPanel("Header");
    JPanel panel = new JPanel();
    panel.setBorder(BorderFactory.createEmptyBorder(8,8,4,4));
    panel.setLayout(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.gridy = 0;
    gbConstraints.gridx = 0;
    gbConstraints.gridwidth = 1;
    gbConstraints.gridheight = 1;
    gbConstraints.weightx = 1;
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.insets = new Insets(0, 0, 6, 4);

    gbConstraints.gridwidth = 3;
    myLineTextField1 = new MyTextField(30);
    myLinePlacementCombo1 = new JComboBox();
    myLineAlignmentCombo1 = new JComboBox();
    JPanel linePanel1 = createLinePanel(CodeEditorBundle.message("print.header.line.1.label"), myLineTextField1, myLinePlacementCombo1, myLineAlignmentCombo1);
    panel.add(linePanel1, gbConstraints);

    myLineTextField2 = new MyTextField(30);
    myLinePlacementCombo2 = new JComboBox();
    myLineAlignmentCombo2 = new JComboBox();
    JPanel linePanel2 = createLinePanel(CodeEditorBundle.message("print.header.line.2.label"), myLineTextField2, myLinePlacementCombo2, myLineAlignmentCombo2);
    gbConstraints.gridy++;
    panel.add(linePanel2, gbConstraints);

    gbConstraints.insets = new Insets(0, 8, 6, 4);
    gbConstraints.gridy++;
    gbConstraints.gridwidth = 1;
    gbConstraints.gridx = 0;
    panel.add(new MyLabel(CodeEditorBundle.message("print.header.font.label")), gbConstraints);
    myFooterFontNameCombo = createFontNamesComboBox();
    gbConstraints.gridx = 1;
    panel.add(myFooterFontNameCombo, gbConstraints);

    myFooterFontSizeCombo = createFontSizesComboBox();
    gbConstraints.gridx = 2;
    panel.add(myFooterFontSizeCombo, gbConstraints);

    return panel;
  }

  private static JPanel createLinePanel(String name, JTextField lineTextField, JComboBox linePlacementCombo, JComboBox lineAlignmentCombo) {
    JPanel panel = new JPanel();
    panel.setBorder(IdeBorderFactory.createTitledBorder(name, true));
    panel.setLayout(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.gridy = 0;
    gbConstraints.gridx = 0;
    gbConstraints.gridwidth = 1;
    gbConstraints.gridheight = 1;
    gbConstraints.weightx = 0;
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.insets = new Insets(0, 0, 6, 0);

    panel.add(new MyLabel(CodeEditorBundle.message("print.header.text.line.editbox")), gbConstraints);
    gbConstraints.gridx = 1;
    gbConstraints.gridwidth = 4;
    gbConstraints.weightx = 1;
    panel.add(lineTextField, gbConstraints);

    gbConstraints.gridwidth = 1;
    gbConstraints.gridy++;
    gbConstraints.gridx = 0;
    gbConstraints.weightx = 0;
    panel.add(new MyLabel(CodeEditorBundle.message("print.header.placement.combobox")), gbConstraints);
    linePlacementCombo.addItem(PrintSettings.HEADER);
    linePlacementCombo.addItem(PrintSettings.FOOTER);
    linePlacementCombo.setRenderer(new MappingListCellRenderer(linePlacementCombo.getRenderer(), PLACEMENT_MAP));
    gbConstraints.gridx = 1;
    gbConstraints.weightx = 0;
    panel.add(linePlacementCombo, gbConstraints);

    gbConstraints.gridx = 2;
    gbConstraints.weightx = 1;
    panel.add(new MyTailPanel(), gbConstraints);

    gbConstraints.gridx = 3;
    gbConstraints.weightx = 0;
    panel.add(new MyLabel(CodeEditorBundle.message("print.header.alignment.combobox")), gbConstraints);
    linePlacementCombo.setRenderer(new MappingListCellRenderer(linePlacementCombo.getRenderer(), ALIGNMENT_MAP));
    lineAlignmentCombo.addItem(PrintSettings.LEFT);
    lineAlignmentCombo.addItem(PrintSettings.CENTER);
    lineAlignmentCombo.addItem(PrintSettings.RIGHT);
    gbConstraints.gridx = 4;
    gbConstraints.weightx = 0;
    panel.add(lineAlignmentCombo, gbConstraints);

    return panel;
  }

  private static JComboBox createFontNamesComboBox() {
    JComboBox comboBox = new JComboBox();
    GraphicsEnvironment graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment();
    Font[] fonts = graphicsEnvironment.getAllFonts();
    for (Font font : fonts) {
      comboBox.addItem(font.getName());
    }
    return comboBox;
  }

  private static JComboBox createFontSizesComboBox() {
    JComboBox comboBox = new JComboBox();
    for(int i = 6; i < 40; i++) {
      comboBox.addItem(String.valueOf(i));
    }
    return comboBox;
  }

  private static JComboBox createPageSizesCombo() {
    JComboBox pageSizesCombo = new JComboBox();
    String[] names = PageSizes.getNames();
    for (String name : names) {
      pageSizesCombo.addItem(PageSizes.getItem(name));
    }
    return pageSizesCombo;
  }

  private static class MyTailPanel extends JPanel {
    public MyTailPanel(){
      setFocusable(false);
    }

    @Override
    public Dimension getMinimumSize() {
      return new Dimension(0,0);
    }
    @Override
    public Dimension getPreferredSize() {
      return new Dimension(0,0);
    }
  }

  public void reset() {
    PrintSettings printSettings = PrintSettings.getInstance();

    myRbSelectedText.setEnabled(isSelectedTextEnabled);
    myRbSelectedText.setSelected(isSelectedTextEnabled);
    myRbCurrentFile.setEnabled(myFileName != null);
    myRbCurrentFile.setSelected(myFileName != null && !isSelectedTextEnabled);
    myRbCurrentPackage.setEnabled(myDirectoryName != null);
    myRbCurrentPackage.setSelected(myDirectoryName != null && !isSelectedTextEnabled && myFileName == null);

    myCbIncludeSubpackages.setSelected(printSettings.isIncludeSubdirectories());
    myCbIncludeSubpackages.setEnabled(myRbCurrentPackage.isSelected());

    Object selectedPageSize = PageSizes.getItem(printSettings.PAPER_SIZE);
    if(selectedPageSize != null) {
      myPaperSizeCombo.setSelectedItem(selectedPageSize);
    }
    myCbColorPrinting.setSelected(printSettings.COLOR_PRINTING);
    myCbSyntaxPrinting.setSelected(printSettings.SYNTAX_PRINTING);
    myCbPrintAsGraphics.setSelected(printSettings.PRINT_AS_GRAPHICS);

    if(printSettings.PORTRAIT_LAYOUT) {
      myRbPortrait.setSelected(true);
    }
    else {
      myRbLandscape.setSelected(true);
    }
    myFontNameCombo.setSelectedItem(printSettings.FONT_NAME);
    myFontSizeCombo.setSelectedItem(String.valueOf(printSettings.FONT_SIZE));

    myCbLineNumbers.setSelected(printSettings.PRINT_LINE_NUMBERS);

    if(printSettings.WRAP) {
      myRbWrapAtWordBreaks.setSelected(true);
    }
    else {
      myRbNoWrap.setSelected(true);
    }

    myTopMarginField.setText(String.valueOf(printSettings.TOP_MARGIN));
    myBottomMarginField.setText(String.valueOf(printSettings.BOTTOM_MARGIN));
    myLeftMarginField.setText(String.valueOf(printSettings.LEFT_MARGIN));
    myRightMarginField.setText(String.valueOf(printSettings.RIGHT_MARGIN));

    myCbDrawBorder.setSelected(printSettings.DRAW_BORDER);


    myLineTextField1.setText(printSettings.FOOTER_HEADER_TEXT1);
    myLinePlacementCombo1.setSelectedItem(printSettings.FOOTER_HEADER_PLACEMENT1);
    myLineAlignmentCombo1.setSelectedItem(printSettings.FOOTER_HEADER_ALIGNMENT1);

    myLineTextField2.setText(printSettings.FOOTER_HEADER_TEXT2);
    myLinePlacementCombo2.setSelectedItem(printSettings.FOOTER_HEADER_PLACEMENT2);
    myLineAlignmentCombo2.setSelectedItem(printSettings.FOOTER_HEADER_ALIGNMENT2);

    myFooterFontSizeCombo.setSelectedItem(String.valueOf(printSettings.FOOTER_HEADER_FONT_SIZE));
    myFooterFontNameCombo.setSelectedItem(printSettings.FOOTER_HEADER_FONT_NAME);
  }

  public void apply() {
    PrintSettings printSettings = PrintSettings.getInstance();

    if (myRbCurrentFile.isSelected()){
      printSettings.setPrintScope(PrintSettings.PRINT_FILE);
    }
    else if (myRbSelectedText.isSelected()){
      printSettings.setPrintScope(PrintSettings.PRINT_SELECTED_TEXT);
    }
    else if (myRbCurrentPackage.isSelected()){
      printSettings.setPrintScope(PrintSettings.PRINT_DIRECTORY);
    }
    printSettings.setIncludeSubdirectories(myCbIncludeSubpackages.isSelected());

    printSettings.PAPER_SIZE = PageSizes.getName(myPaperSizeCombo.getSelectedItem());
    printSettings.COLOR_PRINTING = myCbColorPrinting.isSelected();
    printSettings.SYNTAX_PRINTING = myCbSyntaxPrinting.isSelected();
    printSettings.PRINT_AS_GRAPHICS = myCbPrintAsGraphics.isSelected();

    printSettings.PORTRAIT_LAYOUT = myRbPortrait.isSelected();

    printSettings.FONT_NAME = (String)myFontNameCombo.getSelectedItem();

    try {
      String fontSizeStr = (String)myFontSizeCombo.getSelectedItem();
      printSettings.FONT_SIZE = Integer.parseInt(fontSizeStr);
    }
    catch(NumberFormatException ignored) { }

    printSettings.PRINT_LINE_NUMBERS = myCbLineNumbers.isSelected();

    printSettings.WRAP = myRbWrapAtWordBreaks.isSelected();


    try {
      printSettings.TOP_MARGIN = Float.parseFloat(myTopMarginField.getText());
    }
    catch(NumberFormatException ignored) { }

    try {
      printSettings.BOTTOM_MARGIN = Float.parseFloat(myBottomMarginField.getText());
    }
    catch(NumberFormatException ignored) { }

    try {
      printSettings.LEFT_MARGIN = Float.parseFloat(myLeftMarginField.getText());
    }
    catch(NumberFormatException ignored) { }

    try {
      printSettings.RIGHT_MARGIN = Float.parseFloat(myRightMarginField.getText());
    }
    catch(NumberFormatException ignored) { }

    printSettings.DRAW_BORDER = myCbDrawBorder.isSelected();
    printSettings.FOOTER_HEADER_TEXT1 = myLineTextField1.getText();
    printSettings.FOOTER_HEADER_ALIGNMENT1 = (String)myLineAlignmentCombo1.getSelectedItem();
    printSettings.FOOTER_HEADER_PLACEMENT1 = (String)myLinePlacementCombo1.getSelectedItem();

    printSettings.FOOTER_HEADER_TEXT2 = myLineTextField2.getText();
    printSettings.FOOTER_HEADER_ALIGNMENT2 = (String)myLineAlignmentCombo2.getSelectedItem();
    printSettings.FOOTER_HEADER_PLACEMENT2 = (String)myLinePlacementCombo2.getSelectedItem();

    try {
      printSettings.FOOTER_HEADER_FONT_SIZE = Integer.parseInt((String)myFooterFontSizeCombo.getSelectedItem());
    }
    catch(NumberFormatException ignored) { }

    printSettings.FOOTER_HEADER_FONT_NAME = (String)myFooterFontNameCombo.getSelectedItem();

  }

  @Override
  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction(),getCancelAction(), new ApplyAction(), getHelpAction()};
  }

  @Override
  public void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.PRINT);
  }

  class ApplyAction extends AbstractAction{
    public ApplyAction(){
      putValue(Action.NAME, CodeEditorBundle.message("print.apply.button"));
    }

    @Override
    public void actionPerformed(ActionEvent e){
      apply();
    }
  }


  private static class MyTextField extends JTextField {
    public MyTextField(int size) {
     super(size);
    }
    @Override
    public Dimension getMinimumSize() {
      return super.getPreferredSize();
    }
  }

  private static class MyLabel extends JLabel {
    public MyLabel(String text) {
     super(text);
    }
    @Override
    public Dimension getMinimumSize() {
      return super.getPreferredSize();
    }
  }


}
