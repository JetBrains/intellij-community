/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.application.options.codeStyle;

import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.*;

/**
 * @author max
 */
public abstract class OptionTreeWithPreviewPanel extends MultilanguageCodeStyleAbstractPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.application.options.CodeStyleSpacesPanel");
  private JTree myOptionsTree;
  private final ArrayList<BooleanOptionKey> myKeys = new ArrayList<BooleanOptionKey>();
  protected final JPanel myPanel = new JPanel(new GridBagLayout());

  private boolean myShowAllStandardOptions = false;
  private Set<String> myAllowedOptions = new HashSet<String>();
  private MultiMap<String, Trinity<Class<? extends CustomCodeStyleSettings>, String, String>> myCustomOptions
    = new MultiMap<String, Trinity<Class<? extends CustomCodeStyleSettings>, String, String>>();
  private boolean isFirstUpdate = true;
  private final Map<String, String> myRenamedFields = new THashMap<String, String>();


  public OptionTreeWithPreviewPanel(CodeStyleSettings settings) {
    super(settings);
  }

  @Override
  protected void init() {
    super.init();

    initTables();

    myOptionsTree = createOptionsTree();
    myOptionsTree.setCellRenderer(new MyTreeCellRenderer());
    myPanel.add(ScrollPaneFactory.createScrollPane(myOptionsTree),
                new GridBagConstraints(0, 0, 1, 1, 0, 1, GridBagConstraints.WEST, GridBagConstraints.BOTH,
                                       new Insets(7, 7, 3, 4), 0, 0));

    JPanel previewPanel = createPreviewPanel();

    myPanel.add(previewPanel,
                new GridBagConstraints(1, 0, 1, 1, 1, 1, GridBagConstraints.WEST, GridBagConstraints.BOTH,
                                       new Insets(0, 0, 0, 4), 0, 0));

    installPreviewPanel(previewPanel);
    addPanelToWatch(myPanel);

    isFirstUpdate = false;
  }

  @Override
  protected void onLanguageChange(Language language) {
    updateOptionsTree();
  }

  public void showAllStandardOptions() {
    myShowAllStandardOptions = true;
    updateOptions(true);
  }

  public void showStandardOptions(String... optionNames) {
    if (isFirstUpdate) {
      Collections.addAll(myAllowedOptions, optionNames);
    }
    updateOptions(false, optionNames);
  }

  public void showCustomOption(Class<? extends CustomCodeStyleSettings> settingsClass,
                               String fieldName,
                               String title,
                               String groupName, Object... options) {
    if (isFirstUpdate) {
      myCustomOptions.putValue(groupName, Trinity.<Class<? extends CustomCodeStyleSettings>, String, String>create(settingsClass, fieldName, title));
    }
    enableOption(fieldName);
  }

  @Override
  public void renameStandardOption(String fieldName, String newTitle) {
    if (isFirstUpdate) {
      myRenamedFields.put(fieldName, newTitle);
    }
  }

  protected void updateOptions(boolean showAllStandardOptions, String... allowedOptions) {
    for (BooleanOptionKey key : myKeys) {
      String fieldName = key.field.getName();
      if (key instanceof CustomBooleanOptionKey) {
        key.setEnabled(false);
      }
      else if (showAllStandardOptions) {
        key.setEnabled(true);
      }
      else {
        key.setEnabled(false);
        for (String optionName : allowedOptions) {
          if (fieldName.equals(optionName)) {
            key.setEnabled(true);
            break;
          }
        }
      }
    }
  }

  protected void enableOption(String optionName) {
    for (BooleanOptionKey key : myKeys) {
      if (key.field.getName().equals(optionName)) {
        key.setEnabled(true);
      }
    }
  }

  protected void updateOptionsTree() {
    resetImpl(getSettings());
    myOptionsTree.repaint();
  }

  protected JTree createOptionsTree() {
    DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();
    String groupName = "";
    DefaultMutableTreeNode groupNode = null;
    for (BooleanOptionKey key : myKeys) {
      String newGroupName = key.groupName;
      if (!newGroupName.equals(groupName) || groupNode == null) {
        groupName = newGroupName;
        groupNode = new DefaultMutableTreeNode(newGroupName);
        rootNode.add(groupNode);
      }
      groupNode.add(new MyToggleTreeNode(key, key.title));
    }

    DefaultTreeModel model = new DefaultTreeModel(rootNode);

    final Tree optionsTree = new Tree(model);
    TreeUtil.installActions(optionsTree);
    optionsTree.setRootVisible(false);
    UIUtil.setLineStyleAngled(optionsTree);
    optionsTree.setShowsRootHandles(true);


    optionsTree.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (!optionsTree.isEnabled()) return;
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
          TreePath treePath = optionsTree.getLeadSelectionPath();
          selectCheckbox(treePath);
          e.consume();
        }
      }
    });

    optionsTree.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (!optionsTree.isEnabled()) return;
        TreePath treePath = optionsTree.getPathForLocation(e.getX(), e.getY());
        selectCheckbox(treePath);
      }
    });

    int row = 0;
    while (row < optionsTree.getRowCount()) {
      optionsTree.expandRow(row);
      row++;
    }

    return optionsTree;
  }

  private void selectCheckbox(TreePath treePath) {
    if (treePath == null) {
      return;
    }
    Object o = treePath.getLastPathComponent();
    if (o instanceof MyToggleTreeNode) {
      MyToggleTreeNode node = (MyToggleTreeNode)o;
      if (!node.isEnabled()) return;
      node.setSelected(!node.isSelected());
      int row = myOptionsTree.getRowForPath(treePath);
      myOptionsTree.repaint(myOptionsTree.getRowBounds(row));
      //updatePreview();
      somethingChanged();
    }
  }

  protected abstract void initTables();

  protected int getRightMargin() {
    return -1;
  }

  protected void resetImpl(final CodeStyleSettings settings) {
    TreeModel treeModel = myOptionsTree.getModel();
    TreeNode root = (TreeNode)treeModel.getRoot();
    resetNode(root, settings);
  }

  private void resetNode(TreeNode node, final CodeStyleSettings settings) {
    if (node instanceof MyToggleTreeNode) {
      resetMyTreeNode((MyToggleTreeNode)node, settings);
      return;
    }
    for (int j = 0; j < node.getChildCount(); j++) {
      TreeNode child = node.getChildAt(j);
      resetNode(child, settings);
    }
  }

  private void resetMyTreeNode(MyToggleTreeNode childNode, final CodeStyleSettings settings) {
    try {
      BooleanOptionKey key = (BooleanOptionKey)childNode.getKey();
      childNode.setSelected(key.getValue(settings));
      childNode.setEnabled(key.isEnabled());
    }
    catch (IllegalArgumentException e) {
      LOG.error(e);
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }
  }

  public void apply(CodeStyleSettings settings) {
    TreeModel treeModel = myOptionsTree.getModel();
    TreeNode root = (TreeNode)treeModel.getRoot();
    applyNode(root, settings);
  }

  private static void applyNode(TreeNode node, final CodeStyleSettings settings) {
    if (node instanceof MyToggleTreeNode) {
      applyToggleNode((MyToggleTreeNode)node, settings);
      return;
    }
    for (int j = 0; j < node.getChildCount(); j++) {
      TreeNode child = node.getChildAt(j);
      applyNode(child, settings);
    }
  }

  private static void applyToggleNode(MyToggleTreeNode childNode, final CodeStyleSettings settings) {
    BooleanOptionKey key = (BooleanOptionKey)childNode.getKey();
    key.setValue(settings, childNode.isSelected() ? Boolean.TRUE : Boolean.FALSE);
  }

  public boolean isModified(CodeStyleSettings settings) {
    TreeModel treeModel = myOptionsTree.getModel();
    TreeNode root = (TreeNode)treeModel.getRoot();
    if (isModified(root, settings)) {
      return true;
    }
    return false;

  }

  private static boolean isModified(TreeNode node, final CodeStyleSettings settings) {
    if (node instanceof MyToggleTreeNode) {
      if (isToggleNodeModified((MyToggleTreeNode)node, settings)) {
        return true;
      }
    }
    for (int j = 0; j < node.getChildCount(); j++) {
      TreeNode child = node.getChildAt(j);
      if (isModified(child, settings)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isToggleNodeModified(MyToggleTreeNode childNode, final CodeStyleSettings settings) {
    try {
      BooleanOptionKey key = (BooleanOptionKey)childNode.getKey();
      return childNode.isSelected() != key.getValue(settings);
    }
    catch (IllegalArgumentException e) {
      LOG.error(e);
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }
    return false;
  }

  protected void initBooleanField(@NonNls String fieldName, String title, String groupName) {
    if (myShowAllStandardOptions || myAllowedOptions.contains(fieldName)) {
      doInitBooleanField(fieldName, title, groupName);
    }
  }

  private void doInitBooleanField(@NonNls String fieldName, String title, String groupName) {
    try {
      Class styleSettingsClass = CodeStyleSettings.class;
      Field field = styleSettingsClass.getField(fieldName);

      BooleanOptionKey key = new BooleanOptionKey(groupName, getRenamedTitle(fieldName, title), field);
      myKeys.add(key);
    }
    catch (NoSuchFieldException e) {
      LOG.error(e);
    }
    catch (SecurityException e) {
      LOG.error(e);
    }
  }

  private String getRenamedTitle(String fieldName, String defaultTitle) {
    String renamed = myRenamedFields.get(fieldName);
    return renamed == null ? defaultTitle : renamed;
  }

  protected void initCustomOptions(String groupName) {
    for (Trinity<Class<? extends CustomCodeStyleSettings>, String, String> option : myCustomOptions.get(groupName)) {
      doInitCustomBooleanField(option.first, option.second, option.third, groupName);
    }
  }

  private <T extends CustomCodeStyleSettings> void doInitCustomBooleanField(@NotNull Class<T> customClass,
                                                                            String fieldName,
                                                                            String title,
                                                                            String groupName) {
    try {
      Field field = customClass.getField(fieldName);

      myKeys.add(new CustomBooleanOptionKey(groupName, getRenamedTitle(fieldName, title), customClass, field));
    }
    catch (NoSuchFieldException e) {
      LOG.error(e);
    }
    catch (SecurityException e) {
      LOG.error(e);
    }
  }


  protected void prepareForReformat(final PsiFile psiFile) {
    //psiFile.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, LanguageLevel.HIGHEST);
  }

  protected class MyTreeCellRenderer implements TreeCellRenderer {
    private final MyLabelPanel myLabel;
    private final JCheckBox myCheckBox;

    public MyTreeCellRenderer() {
      myLabel = new MyLabelPanel();
      myCheckBox = new JCheckBox();
      myCheckBox.setMargin(new Insets(0, 0, 0, 0));
    }

    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean isSelected, boolean expanded,
                                                  boolean leaf, int row, boolean hasFocus) {

      if (value instanceof MyToggleTreeNode) {
        MyToggleTreeNode treeNode = (MyToggleTreeNode)value;
        JToggleButton button = myCheckBox;
        button.setText(treeNode.getText());
        button.setSelected(treeNode.isSelected);
        if (isSelected) {
          button.setForeground(UIUtil.getTreeSelectionForeground());
          button.setBackground(UIUtil.getTreeSelectionBackground());
        }
        else {
          button.setForeground(UIUtil.getTreeTextForeground());
          button.setBackground(UIUtil.getTreeTextBackground());
        }

        button.setEnabled(tree.isEnabled() && treeNode.isEnabled());

        return button;
      }
      else {
        Font font = tree.getFont();
        Font boldFont = new Font(font.getName(), Font.BOLD, font.getSize());
        myLabel.setFont(boldFont);
        myLabel.setText(value.toString());

        if (isSelected) {
          myLabel.setForeground(UIUtil.getTreeSelectionForeground());
          myLabel.setBackground(UIUtil.getTreeSelectionBackground());
        }
        else {
          myLabel.setForeground(UIUtil.getTreeTextForeground());
          myLabel.setBackground(UIUtil.getTreeTextBackground());
        }

        myLabel.setEnabled(tree.isEnabled());

        return myLabel;
      }
    }
  }

  private static class MyLabelPanel extends JPanel {
    private String myText = "";
    private final boolean hasFocus = false;

    public MyLabelPanel() {
    }

    public void setText(String text) {
      myText = text;
      if (myText == null) {
        myText = "";
      }
    }

    protected void paintComponent(Graphics g) {
      g.setFont(getMyFont());
      FontMetrics fontMetrics = getFontMetrics(getMyFont());
      int h = fontMetrics.getHeight();
      int w = fontMetrics.charsWidth(myText.toCharArray(), 0, myText.length());
      g.setColor(getBackground());
      g.fillRect(0, 1, w + 2, h);
      if (hasFocus) {
        g.setColor(UIUtil.getTreeTextBackground());
        g.drawRect(0, 1, w + 2, h);
      }
      g.setColor(getForeground());
      g.drawString(myText, 2, h - fontMetrics.getDescent() + 1);
    }

    private Font getMyFont() {
      Font font = UIUtil.getTreeFont();
      return new Font(font.getName(), Font.BOLD, font.getSize());
    }

    public Dimension getPreferredSize() {
      FontMetrics fontMetrics = getFontMetrics(getMyFont());
      if (fontMetrics == null) {
        return new Dimension(0, 0);
      }
      int h = fontMetrics.getHeight();
      int w = fontMetrics.charsWidth(myText.toCharArray(), 0, myText.length());
      return new Dimension(w + 4, h + 2);
    }
  }

  private class BooleanOptionKey {
    final String groupName;
    String title;
    final Field field;
    private boolean enabled = true;

    public BooleanOptionKey(String groupName, String title, Field field) {
      this.groupName = groupName;
      this.title = title;
      this.field = field;
    }

    public void setValue(CodeStyleSettings  settings, Boolean aBoolean) {
      try {
        CommonCodeStyleSettings commonSettings = settings.getCommonSettings(getSelectedLanguage());
        field.set(commonSettings, aBoolean);
      }
      catch (IllegalAccessException e) {
        LOG.error(e);
      }
    }

    public boolean getValue(CodeStyleSettings settings) throws IllegalAccessException {
      CommonCodeStyleSettings commonSettings = settings.getCommonSettings(getSelectedLanguage());
      return field.getBoolean(commonSettings);
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public boolean isEnabled() {
      return this.enabled;
    }
  }

  private class CustomBooleanOptionKey<T extends CustomCodeStyleSettings> extends BooleanOptionKey {
    private final Class<T> mySettingsClass;

    public CustomBooleanOptionKey(String groupName, String title, Class<T> settingsClass, Field field) {
      super(groupName, title, field);
      mySettingsClass = settingsClass;
    }

    @Override
    public void setValue(CodeStyleSettings settings, Boolean aBoolean) {
      final CustomCodeStyleSettings customSettings = settings.getCustomSettings(mySettingsClass);
      try {
        field.set(customSettings, aBoolean);
      }
      catch (IllegalAccessException e) {
        LOG.error(e);
      }
    }

    @Override
    public boolean getValue(CodeStyleSettings settings) throws IllegalAccessException {
      final CustomCodeStyleSettings customSettings = settings.getCustomSettings(mySettingsClass);
      return field.getBoolean(customSettings);
    }
  }

  private static class MyToggleTreeNode extends DefaultMutableTreeNode {
    private final Object myKey;
    private final String myText;
    private boolean isSelected;
    private boolean isEnabled = true;

    public MyToggleTreeNode(Object key, String text) {
      myKey = key;
      myText = text;
    }

    public Object getKey() {
      return myKey;
    }

    public String getText() {
      return myText;
    }

    public void setSelected(boolean val) {
      isSelected = val;
    }

    public boolean isSelected() {
      return isSelected;
    }

    public void setEnabled(boolean val) {
      isEnabled = val;
    }

    public boolean isEnabled() {
      return isEnabled;
    }
  }

  public JComponent getPanel() {
    return myPanel;
  }

}
