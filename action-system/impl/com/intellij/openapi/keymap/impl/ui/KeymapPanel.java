package com.intellij.openapi.keymap.impl.ui;

import com.intellij.CommonBundle;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.impl.KeymapImpl;
import com.intellij.openapi.keymap.impl.KeymapManagerImpl;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packageDependencies.ui.TreeExpansionMonitor;
import com.intellij.ui.*;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class KeymapPanel extends JPanel {
  private QuickListsPanel myQuickListsPanel;
  private JComboBox myKeymapList;
  private JList myShortcutsList;

  private DefaultComboBoxModel myKeymapListModel = new DefaultComboBoxModel();

  private KeymapImpl mySelectedKeymap;

  private JButton myCopyButton;
  private JButton myDeleteButton;
  private JButton myResetToDefault;

  private JButton myAddKeyboardShortcutButton;
  private JButton myAddMouseShortcutButton;
  private JButton myRemoveShortcutButton;
  @NonNls private JEditorPane myDescription;
  private JLabel myBaseKeymapLabel;

  private JCheckBox myDisableMnemonicsCheckbox;
  private ActionsTree myActionsTree;
  private FilterComponent myFilterComponent;
  private JBPopup myPopup = null;
  private TreeExpansionMonitor myTreeExpansionMonitor;

  public KeymapPanel() {
    setLayout(new BorderLayout());
    myQuickListsPanel = new QuickListsPanel(this);
    TabbedPaneWrapper tabbedPane = new TabbedPaneWrapper();
    add(tabbedPane.getComponent(), BorderLayout.CENTER);
    JPanel keymapPanel = new JPanel(new BorderLayout());
    keymapPanel.setBorder(BorderFactory.createEmptyBorder(5, 2, 2, 2));
    keymapPanel.add(createKeymapListPanel(), BorderLayout.NORTH);
    keymapPanel.add(createKeymapSettingsPanel(), BorderLayout.CENTER);
    tabbedPane.addTab(KeyMapBundle.message("keymap.display.name"), keymapPanel);

    tabbedPane.addTab(KeyMapBundle.message("quick.lists.ide.border.factory.title"), myQuickListsPanel);
  }


  private JPanel createKeymapListPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());
    panel.add(new JLabel(KeyMapBundle.message("keymaps.border.factory.title")), new GridBagConstraints(0,0, 1, 1, 0,0,GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0,2,0,0), 0,0));

    myKeymapList = new JComboBox(myKeymapListModel);
    myKeymapList.setEditor(new MyEditor());
    myKeymapList.setRenderer(new MyKeymapRenderer());
    panel.add(myKeymapList, new GridBagConstraints(1,0,1,1,0,0,GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(0,0,0,0),0,0));

    panel.add(createKeymapButtonsPanel(), new GridBagConstraints(2,0,1,1,0,0,GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0,0,0,0),0,0));
    myKeymapList.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        processCurrentKeymapChanged();
      }
    });
    panel.add(createKeymapNamePanel(), new GridBagConstraints(3,0,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.NONE,new Insets(0,10,0,0),0,0));
    return panel;
  }

  void processCurrentKeymapChanged() {
    myCopyButton.setEnabled(false);
    myDeleteButton.setEnabled(false);
    myResetToDefault.setEnabled(false);
    myAddKeyboardShortcutButton.setEnabled(false);
    myAddMouseShortcutButton.setEnabled(false);
    myRemoveShortcutButton.setEnabled(false);

    KeymapImpl selectedKeymap = getSelectedKeymap();
    mySelectedKeymap = selectedKeymap;
    if (selectedKeymap == null) {
      myActionsTree.reset(new KeymapImpl(), myQuickListsPanel.getCurrentQuickListIds());
      return;
    }
    myKeymapList.setEditable(mySelectedKeymap.canModify());

    myCopyButton.setEnabled(true);
    myBaseKeymapLabel.setText("");
    Keymap parent = mySelectedKeymap.getParent();
    if (parent != null) {
      myBaseKeymapLabel.setText(KeyMapBundle.message("based.on.keymap.label", parent.getPresentableName()));
      if (mySelectedKeymap.getOwnActionIds().length > 0){
        myResetToDefault.setEnabled(true);
      }
    }
    myDisableMnemonicsCheckbox.setSelected(!mySelectedKeymap.areMnemonicsEnabled());
    myDisableMnemonicsCheckbox.setEnabled(mySelectedKeymap.canModify());
    if(mySelectedKeymap.canModify()) {
      myDeleteButton.setEnabled(true);
      myAddKeyboardShortcutButton.setEnabled(true);
      myAddMouseShortcutButton.setEnabled(true);
      myRemoveShortcutButton.setEnabled(true);
    }

    myActionsTree.reset(mySelectedKeymap, myQuickListsPanel.getCurrentQuickListIds());

    updateShortcutsList();
  }

  private KeymapImpl getSelectedKeymap() {
    return (KeymapImpl)myKeymapList.getSelectedItem();
  }

  List<Keymap> getAllKeymaps() {
    ListModel model = myKeymapList.getModel();
    List<Keymap> result = new ArrayList<Keymap>();
    for (int i = 0; i < model.getSize(); i++) {
      result.add((Keymap)model.getElementAt(i));
    }
    return result;
  }

  private JPanel createShortcutsPanel() {
    JPanel panel = new JPanel(new GridBagLayout());

    JLabel currentKeysLabel = new JLabel(KeyMapBundle.message("shortcuts.keymap.label"));
    panel.add(currentKeysLabel, new GridBagConstraints(1,0,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.NONE, new Insets(0, 0, 0, 8), 0, 0));

    myShortcutsList = new JList(new DefaultListModel());
    myShortcutsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myShortcutsList.setCellRenderer(new ShortcutListRenderer());
    currentKeysLabel.setLabelFor(myShortcutsList);
    JScrollPane scrollPane = new JScrollPane(myShortcutsList);
    scrollPane.setPreferredSize(new Dimension(160, 200));
    panel.add(scrollPane, new GridBagConstraints(1,1,1,1,1,1,GridBagConstraints.WEST,GridBagConstraints.BOTH, new Insets(0, 0, 0, 2), 0, 0));

    panel.add(
      createShortcutsButtonsPanel(),
      new GridBagConstraints(2,1,1,1,0,0,GridBagConstraints.NORTH,GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0)
    );

    myActionsTree.addTreeSelectionListener(
      new TreeSelectionListener() {
        public void valueChanged(TreeSelectionEvent e) {
          updateShortcutsList();
        }
      }
    );

    return panel;
  }

  private JPanel createKeymapButtonsPanel() {
    JPanel panel = new JPanel();
    panel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
    panel.setLayout(new GridBagLayout());
    myCopyButton = new JButton(KeyMapBundle.message("copy.keymap.button"));
    myCopyButton.setMargin(new Insets(2,2,2,2));
    final GridBagConstraints gc = new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 5, 0, 0), 0, 0);
    panel.add(myCopyButton, gc);
    myResetToDefault = new JButton(CommonBundle.message("button.reset"));
    myResetToDefault.setMargin(new Insets(2,2,2,2));
    panel.add(myResetToDefault, gc);
    myDeleteButton = new JButton(KeyMapBundle.message("delete.keymap.button"));
    myDeleteButton.setMargin(new Insets(2,2,2,2));
    gc.weightx = 1;
    panel.add(myDeleteButton, gc);

    myCopyButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          copyKeymap();
        }
      }
    );

    myResetToDefault.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        resetKeymap();
      }

    });

    myDeleteButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          deleteKeymap();
        }
      }
    );

    return panel;
  }

  private JPanel createKeymapSettingsPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());

    panel.add(createToolbarPanel(), new GridBagConstraints(0,0,1,1,1,0,GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0,0,0,0), 0,0));
    myActionsTree = new ActionsTree();
    JComponent component = myActionsTree.getComponent();
    component.setPreferredSize(new Dimension(100, 300));
    panel.add(component, new GridBagConstraints(0,1,1,1,1,1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0,0,0,0), 0,0));

    JPanel rightPanel = new JPanel(new GridLayout(2, 1, 0, 5));
    rightPanel.add(createDescriptionPanel());
    rightPanel.add(createShortcutsPanel());

    panel.add(rightPanel, new GridBagConstraints(1,1,1,1,0,1,GridBagConstraints.WEST, GridBagConstraints.BOTH,new Insets(0,5,0,0),0,0));

    myTreeExpansionMonitor = TreeExpansionMonitor.install(myActionsTree.getTree());
    return panel;
  }

  private JPanel createToolbarPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    DefaultActionGroup group = new DefaultActionGroup();
    final JComponent toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true).getComponent();
    final CommonActionsManager commonActionsManager = CommonActionsManager.getInstance();
    final TreeExpander treeExpander = new TreeExpander() {
      public void expandAll() {
        TreeUtil.expandAll(myActionsTree.getTree());
      }

      public boolean canExpand() {
        return true;
      }

      public void collapseAll() {
        TreeUtil.collapseAll(myActionsTree.getTree(), 0);
      }

      public boolean canCollapse() {
        return true;
      }
    };
    group.add(commonActionsManager.createExpandAllAction(treeExpander));
    group.add(commonActionsManager.createCollapseAllAction(treeExpander));

    panel.add(toolbar, new GridBagConstraints(0,0,1,1,1,0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(8,0,0,0), 0,0));
    group = new DefaultActionGroup();
    final JComponent searchToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true).getComponent();
    myFilterComponent = new FilterComponent("KEYMAP", 5){
      public void filter() {
        if (!myTreeExpansionMonitor.isFreeze()) myTreeExpansionMonitor.freeze();
        final String filter = getFilter();
        myActionsTree.filter(filter, myQuickListsPanel.getCurrentQuickListIds());
        final JTree tree = myActionsTree.getTree();
        TreeUtil.expandAll(tree);
        if (filter == null || filter.length() == 0){
          TreeUtil.collapseAll(tree, 0);
          myTreeExpansionMonitor.restore();
        }
      }
    };
    myFilterComponent.reset();

    panel.add(myFilterComponent, new GridBagConstraints(1, 0, 1, 1, 0, 0, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(8, 0, 0, 0), 0,0));

    group.add(new AnAction(KeyMapBundle.message("filter.shortcut.action.text"),
                           KeyMapBundle.message("filter.shortcut.action.text"),
                           IconLoader.getIcon("/ant/shortcutFilter.png")) {
      public void actionPerformed(AnActionEvent e) {
        if (myPopup == null || myPopup.getContent() == null){
          myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(createFilteringPanel(), null)
            .setRequestFocus(true)
            .setTitle(KeyMapBundle.message("filter.settings.popup.title"))
            .setMovable(true)
            .createPopup();
        }
        myPopup.showUnderneathOf(searchToolbar);
      }
    });
    group.add(new AnAction(KeyMapBundle.message("filter.clear.action.text"),
                           KeyMapBundle.message("filter.clear.action.text"), IconLoader.getIcon("/actions/gc.png")) {
      public void actionPerformed(AnActionEvent e) {
        myActionsTree.filter(null, myQuickListsPanel.getCurrentQuickListIds()); //clear filtering
        TreeUtil.collapseAll(myActionsTree.getTree(), 0);
        myTreeExpansionMonitor.restore();
      }
    });

    panel.add(searchToolbar, new GridBagConstraints(2, 0, 1, 1, 0, 0, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(8, 0, 0, 0), 0,0));
    return panel;
  }

  private JPanel createDescriptionPanel() {
    JPanel panel = new JPanel(new BorderLayout()){
      public Dimension getMinimumSize() {
        return new Dimension(300, -1);
      }
    };
    panel.add(new JLabel(KeyMapBundle.message("action.description.ide.border.factory.title")), BorderLayout.NORTH);
    myDescription = new JEditorPane(UIUtil.HTML_MIME, "<html><body></body></html>");
    myDescription.setEditable(false);
    panel.add(ScrollPaneFactory.createScrollPane(myDescription), BorderLayout.CENTER);
    return panel;
  }

  private JPanel createKeymapNamePanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    myBaseKeymapLabel = new JLabel(KeyMapBundle.message("parent.keymap.label"));
    Dimension preferredSize = myBaseKeymapLabel.getPreferredSize();
    myBaseKeymapLabel.setPreferredSize(new Dimension(preferredSize.width * 2, preferredSize.height));
    myBaseKeymapLabel.setMinimumSize(new Dimension(preferredSize.width * 2, preferredSize.height));
    panel.add(myBaseKeymapLabel, new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,new Insets(0, 16, 0, 0),0,0));

    myDisableMnemonicsCheckbox = new JCheckBox(KeyMapBundle.message("disable.mnemonic.in.menu.check.box"));
    myDisableMnemonicsCheckbox.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        if (mySelectedKeymap != null) {
          mySelectedKeymap.setDisableMnemonics(myDisableMnemonicsCheckbox.isSelected());
        }
      }
    });
    panel.add(myDisableMnemonicsCheckbox, new GridBagConstraints(1, 0, 1, 1, 0, 0,GridBagConstraints.WEST,GridBagConstraints.NONE,new Insets(0, 0, 0, 0),0,0));
    return panel;
  }


  private JPanel createFilteringPanel() {
    JPanel filterComponent = new JPanel(new GridBagLayout());
    filterComponent.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

    final ShortcutTextField firstShortcut = new ShortcutTextField();
    final ShortcutTextField secondShortcut = new ShortcutTextField();
    final JCheckBox enable2Shortcut = new JCheckBox(KeyMapBundle.message("filter.enable.second.stroke.checkbox"));
    firstShortcut.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        filterTreeByShortcut(firstShortcut, enable2Shortcut, secondShortcut);
      }
    });
    secondShortcut.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        filterTreeByShortcut(firstShortcut, enable2Shortcut, secondShortcut);
      }
    });
    final JLabel firstLabel = new JLabel(KeyMapBundle.message("filter.first.stroke.input"));
    final JLabel secondLabel = new JLabel(KeyMapBundle.message("filter.second.stroke.input"));
    filterComponent.add(firstLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(5,2,0,0),0,0));
    filterComponent.add(firstShortcut, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0,2,0,0),0,0));
    enable2Shortcut.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        secondShortcut.setEnabled(enable2Shortcut.isSelected());
        secondLabel.setEnabled(enable2Shortcut.isSelected());
        if (enable2Shortcut.isSelected()){
          secondShortcut.requestFocusInWindow();
        }
      }
    });
    filterComponent.add(enable2Shortcut, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0,0,0,0),0,0));
    filterComponent.add(secondLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0,2,0,0),0,0));
    filterComponent.add(secondShortcut, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0,2,0,0),0,0));
    enable2Shortcut.setSelected(false);
    secondLabel.setEnabled(false);
    secondShortcut.setEnabled(false);
    firstShortcut.requestFocusInWindow();
    return filterComponent;
  }

  private void filterTreeByShortcut(final ShortcutTextField firstShortcut,
                                    final JCheckBox enable2Shortcut,
                                    final ShortcutTextField secondShortcut) {
    final KeyStroke keyStroke = firstShortcut.getKeyStroke();
    if (keyStroke != null){
      if (!myTreeExpansionMonitor.isFreeze()) myTreeExpansionMonitor.freeze();
      myActionsTree.filterTree(new KeyboardShortcut(keyStroke, enable2Shortcut.isSelected() ? secondShortcut.getKeyStroke() : null), myQuickListsPanel.getCurrentQuickListIds());
      final JTree tree = myActionsTree.getTree();
      TreeUtil.expandAll(tree);
    }
  }

  public void showOption(String option){
    createFilteringPanel();
    myFilterComponent.setFilter(option);
    myActionsTree.filter(option, myQuickListsPanel.getCurrentQuickListIds());
  }

  private JPanel createShortcutsButtonsPanel() {
    JPanel panel = new JPanel(new GridLayout(3, 1, 0, 4));
    panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    myAddKeyboardShortcutButton = new JButton(KeyMapBundle.message("add.keyboard.shortcut.button"));
    panel.add(myAddKeyboardShortcutButton);

    myAddMouseShortcutButton=new JButton(KeyMapBundle.message("add.mouse.shortcut.button"));
    panel.add(myAddMouseShortcutButton);

    myRemoveShortcutButton = new JButton(KeyMapBundle.message("remove.shortcut.button"));
    panel.add(myRemoveShortcutButton);

    myAddKeyboardShortcutButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          addKeyboardShortcut();
        }
      }
    );

    myAddMouseShortcutButton.addActionListener(
      new ActionListener(){
        public void actionPerformed(ActionEvent e){
          addMouseShortcut();
        }
      }
    );

    myRemoveShortcutButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          removeShortcut();
        }
      }
    );

    return panel;
  }

  private void addKeyboardShortcut() {
    String actionId = myActionsTree.getSelectedActionId();
    if (actionId == null) {
      return;
    }

    Group group = myActionsTree.getMainGroup();
    KeyboardShortcutDialog dialog = new KeyboardShortcutDialog(this, actionId, group);

    Shortcut selected = (Shortcut)myShortcutsList.getSelectedValue();
    KeyboardShortcut selectedKeyboardShortcut = selected instanceof KeyboardShortcut ? (KeyboardShortcut)selected : null;

    dialog.setData(mySelectedKeymap, selectedKeyboardShortcut);
    dialog.show();
    if (!dialog.isOK()){
      return;
    }

    KeyboardShortcut keyboardShortcut = dialog.getKeyboardShortcut();

    if (keyboardShortcut == null) {
      return;
    }

    HashMap<String, ArrayList<KeyboardShortcut>> conflicts = mySelectedKeymap.getConflicts(actionId, keyboardShortcut);
    if(conflicts.size() > 0) {
      int result = Messages.showDialog(
        this,
        KeyMapBundle.message("conflict.shortcut.dialog.message"),
        KeyMapBundle.message("conflict.shortcut.dialog.title"),
        new String[]{KeyMapBundle.message("conflict.shortcut.dialog.remove.button"),
          KeyMapBundle.message("conflict.shortcut.dialog.leave.button"),
          KeyMapBundle.message("conflict.shortcut.dialog.cancel.button"),},
        0,
        Messages.getWarningIcon());

      if(result == 0) {
        for (String id : conflicts.keySet()) {
          ArrayList<KeyboardShortcut> list = conflicts.get(id);
          for (KeyboardShortcut shortcut : list) {
            mySelectedKeymap.removeShortcut(id, shortcut);
          }
        }
      }
      else if (result != 1) {
        return;
      }
    }

    // if shortcut is aleady registered to this action, just select it in the list

    Shortcut[] shortcuts = mySelectedKeymap.getShortcuts(actionId);
    for (int i = 0; i < shortcuts.length; i++) {
      Shortcut shortcut = shortcuts[i];
      if (shortcut.equals(keyboardShortcut)) {
        myShortcutsList.setSelectedIndex(i);
        return;
      }
    }

    mySelectedKeymap.addShortcut(actionId, keyboardShortcut);
    if (StringUtil.startsWithChar(actionId, '$')) {
      mySelectedKeymap.addShortcut(KeyMapBundle.message("editor.shortcut", actionId.substring(1)), keyboardShortcut);
    }
    updateShortcutsList();
    myShortcutsList.setSelectedIndex(myShortcutsList.getModel().getSize()-1);

    repaintLists();
    processCurrentKeymapChanged();
  }

  private void addMouseShortcut(){
    String actionId = myActionsTree.getSelectedActionId();
    if (actionId == null) {
      return;
    }

    Shortcut shortcut = (Shortcut)myShortcutsList.getSelectedValue();
    MouseShortcut mouseShortcut = shortcut instanceof MouseShortcut ? (MouseShortcut)shortcut : null;

    MouseShortcutDialog dialog = new MouseShortcutDialog(
      this,
      mouseShortcut,
      mySelectedKeymap,
      actionId,
      myActionsTree.getMainGroup()
    );
    dialog.show();
    if (!dialog.isOK()){
      return;
    }

    mouseShortcut = dialog.getMouseShortcut();

    if (mouseShortcut == null){
      return;
    }

    String[] actionIds = mySelectedKeymap.getActionIds(mouseShortcut);
    if(actionIds.length > 1 || (actionIds.length == 1 && !actionId.equals(actionIds[0]))) {
      int result = Messages.showDialog(
        this,
        KeyMapBundle.message("conflict.shortcut.dialog.message"),
        KeyMapBundle.message("conflict.shortcut.dialog.title"),
        new String[]{KeyMapBundle.message("conflict.shortcut.dialog.remove.button"),
          KeyMapBundle.message("conflict.shortcut.dialog.leave.button"),
          KeyMapBundle.message("conflict.shortcut.dialog.cancel.button"),},
        0,
        Messages.getWarningIcon());

      if(result == 0) {
        for (String id : actionIds) {
          mySelectedKeymap.removeShortcut(id, mouseShortcut);
        }
      }
      else if (result != 1) {
        return;
      }
    }

    // if shortcut is aleady registered to this action, just select it in the list

    Shortcut[] shortcuts = mySelectedKeymap.getShortcuts(actionId);
    for (int i = 0; i < shortcuts.length; i++) {
      if (shortcuts[i].equals(mouseShortcut)) {
        myShortcutsList.setSelectedIndex(i);
        return;
      }
    }

    mySelectedKeymap.addShortcut(actionId, mouseShortcut);
    if (StringUtil.startsWithChar(actionId, '$')) {
      mySelectedKeymap.addShortcut(KeyMapBundle.message("editor.shortcut", actionId.substring(1)), mouseShortcut);
    }
    updateShortcutsList();
    myShortcutsList.setSelectedIndex(myShortcutsList.getModel().getSize()-1);

    repaintLists();
    processCurrentKeymapChanged();
  }

  private void repaintLists() {
    myActionsTree.getComponent().repaint();
  }

  private void removeShortcut() {
    String actionId = myActionsTree.getSelectedActionId();
    if (actionId == null) {
      return;
    }
    Shortcut shortcut = (Shortcut)myShortcutsList.getSelectedValue();
    if(shortcut == null) {
      return;
    }
    int selectedIndex = myShortcutsList.getSelectedIndex();
    mySelectedKeymap.removeShortcut(actionId, shortcut);
    if (StringUtil.startsWithChar(actionId, '$')) {
      mySelectedKeymap.removeShortcut(KeyMapBundle.message("editor.shortcut", actionId.substring(1)), shortcut);
    }

    updateShortcutsList();

    int count = myShortcutsList.getModel().getSize();
    if(count > 0) {
      myShortcutsList.setSelectedIndex(Math.max(selectedIndex-1, 0));
    }
    else {
      myShortcutsList.clearSelection();
    }

    repaintLists();
    processCurrentKeymapChanged();
  }

  private void copyKeymap() {
    KeymapImpl keymap = getSelectedKeymap();
    if(keymap == null) {
      return;
    }
    KeymapImpl newKeymap = keymap.deriveKeymap();

    String newKeymapName = KeyMapBundle.message("new.keymap.name");
    if(!tryNewKeymapName(newKeymapName)) {
      for(int i=0; ; i++) {
        newKeymapName = KeyMapBundle.message("new.indexed.keymap.name", i);
        if(tryNewKeymapName(newKeymapName)) {
          break;
        }
      }
    }
    newKeymap.setName(newKeymapName);
    newKeymap.setCanModify(true);
    myKeymapListModel.addElement(newKeymap);
    myKeymapList.setSelectedItem(newKeymap);
    processCurrentKeymapChanged();
  }

  private boolean tryNewKeymapName(String name) {
    for(int i=0; i<myKeymapListModel.getSize(); i++) {
      Keymap k = (Keymap)myKeymapListModel.getElementAt(i);
      if(name.equals(k.getName())) {
        return false;
      }
    }

    return true;
  }

  private void deleteKeymap() {
    Keymap keymap = getSelectedKeymap();
    if(keymap == null) {
      return;
    }
    int result = Messages.showYesNoDialog(this, KeyMapBundle.message("delete.keymap.dialog.message"), KeyMapBundle.message("delete.keymap.dialog.title"), Messages.getWarningIcon());
    if (result != 0) {
      return;
    }
    myKeymapListModel.removeElement(myKeymapList.getSelectedItem());
    processCurrentKeymapChanged();
  }

  private void resetKeymap() {
    Keymap keymap = getSelectedKeymap();
    if(keymap == null) {
      return;
    }
    ((KeymapImpl)keymap).clearOwnActionsIds();
    processCurrentKeymapChanged();
  }

  private void updateShortcutsList() {
    DefaultListModel shortcutsModel = (DefaultListModel)myShortcutsList.getModel();
    shortcutsModel.clear();
    String actionId = myActionsTree.getSelectedActionId();
    myDescription.setText(" ");
    if (actionId != null && mySelectedKeymap != null) {
      QuickList list = myActionsTree.getSelectedQuickList();
      if (list != null) {
        String description = list.getDescription().trim();
        if (description.length() > 0) {
          myDescription.setText(prepareDescription(description));
        }
      } else {
        AnAction action = ActionManager.getInstance().getActionOrStub(actionId);
        if (action != null) {
          String description = action.getTemplatePresentation().getDescription();
          if (description != null && description.trim().length() > 0) {
            myDescription.setText(prepareDescription(description));
          }
        }
      }
      Shortcut[] shortcuts = mySelectedKeymap.getShortcuts(actionId);
      for (Shortcut shortcut : shortcuts) {
        shortcutsModel.addElement(shortcut);
      }
      if(shortcutsModel.size() > 0) {
        myShortcutsList.setSelectedIndex(0);
      }

      myAddKeyboardShortcutButton.setEnabled(mySelectedKeymap.canModify());
      myAddMouseShortcutButton.setEnabled(mySelectedKeymap.canModify());
      myRemoveShortcutButton.setEnabled(mySelectedKeymap.canModify() && shortcutsModel.size() > 0);
    }
    else {
      myAddKeyboardShortcutButton.setEnabled(false);
      myAddMouseShortcutButton.setEnabled(false);
      myRemoveShortcutButton.setEnabled(false);
    }
  }

  private @NonNls String prepareDescription(final String description) {
    return "<html><body>" + (myFilterComponent != null ? SearchUtil.markup(description, myFilterComponent.getFilter()) : description) + "</body></html>";
  }

  public void disposeUI() {
    if (myPopup != null && myPopup.isVisible()){
      myPopup.cancel();
    }
    if (myFilterComponent != null){
      myFilterComponent.dispose();
    }
  }

  private static final class MyKeymapRenderer extends ColoredListCellRenderer {
    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      Keymap keymap = (Keymap)value;
      String name = keymap.getPresentableName();
      if(name == null) {
        name = KeyMapBundle.message("keymap.noname.presentable.name");
      }
      append(name, selected ? SimpleTextAttributes.SELECTED_SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }

  private static final class ShortcutListRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      Shortcut shortcut = (Shortcut)value;
      setText(KeymapUtil.getShortcutText(shortcut));
      setIcon(KeymapUtil.getShortcutIcon(shortcut));
      return this;
    }
  }

  public void reset() {
    myKeymapListModel.removeAllElements();
    KeymapManagerEx keymapManager = KeymapManagerEx.getInstanceEx();
    Keymap[] keymaps = keymapManager.getAllKeymaps();
    for (Keymap keymap1 : keymaps) {
      KeymapImpl keymap = (KeymapImpl)keymap1;
      if (keymap.canModify()) {
        keymap = keymap.copy();
      }
      myKeymapListModel.addElement(keymap);
      if (Comparing.equal(keymapManager.getActiveKeymap(), keymap1)) {
        mySelectedKeymap = keymap;
      }
    }

    if(myKeymapListModel.getSize() == 0) {
      KeymapImpl keymap = new KeymapImpl();
      keymap.setName(KeyMapBundle.message("keymap.no.name"));
      myKeymapListModel.addElement(keymap);
    }

    myQuickListsPanel.reset();

    myKeymapList.setSelectedItem(mySelectedKeymap);
  }

  public void apply() throws ConfigurationException{
    ensureUniqueKeymapNames();
    final KeymapManagerImpl keymapManager = (KeymapManagerImpl)KeymapManager.getInstance();
    keymapManager.removeAllKeymapsExceptUnmodifiable();
    for(int i = 0; i < myKeymapListModel.getSize(); i++){
      final Keymap modelKeymap = (Keymap)myKeymapListModel.getElementAt(i);
      if(modelKeymap.canModify()) {
        final KeymapImpl keymapToAdd = ((KeymapImpl)modelKeymap).copy();
        keymapManager.addKeymap(keymapToAdd);
      }
    }
    keymapManager.setActiveKeymap(mySelectedKeymap);
    try {
      keymapManager.save();
    }
    catch (IOException e) {
      throw new ConfigurationException(e.getMessage());
    }

    myQuickListsPanel.apply();
  }

  private void ensureUniqueKeymapNames() throws ConfigurationException {
    final Set<String> keymapNames = new HashSet<String>();
    for(int i = 0; i < myKeymapListModel.getSize(); i++){
      final Keymap modelKeymap = (Keymap)myKeymapListModel.getElementAt(i);
      String name = modelKeymap.getName();
      if (keymapNames.contains(name)) {
        throw new ConfigurationException(KeyMapBundle.message("configuration.all.keymaps.should.have.unique.names.error.message"));
      }
      keymapNames.add(name);
    }
  }

  boolean isModified() {
    KeymapManagerEx keymapManager = KeymapManagerEx.getInstanceEx();
    if (!Comparing.equal(mySelectedKeymap, keymapManager.getActiveKeymap())) {
      return true;
    }
    Keymap[] managerKeymaps = keymapManager.getAllKeymaps();
    Keymap[] panelKeymaps = new Keymap[myKeymapListModel.getSize()];
    for(int i = 0; i < myKeymapListModel.getSize(); i++){
      panelKeymaps[i] = (Keymap)myKeymapListModel.getElementAt(i);
    }
    return !Comparing.equal(managerKeymaps, panelKeymaps) || myQuickListsPanel.isModified();
  }

  public void selectAction(String actionId) {
    myActionsTree.selectAction(actionId);
  }


  public static class ShortcutTextField extends JTextField {
    private KeyStroke myKeyStroke;

    public ShortcutTextField() {
      enableEvents(KeyEvent.KEY_EVENT_MASK);
      setFocusTraversalKeysEnabled(false);
    }

    protected void processKeyEvent(KeyEvent e) {
      if (e.getID() == KeyEvent.KEY_PRESSED) {
        int keyCode = e.getKeyCode();
        if (keyCode == KeyEvent.VK_SHIFT || keyCode == KeyEvent.VK_ALT || keyCode == KeyEvent.VK_CONTROL ||
            keyCode == KeyEvent.VK_ALT_GRAPH || keyCode == KeyEvent.VK_META) {
          return;
        }

        myKeyStroke = KeyStroke.getKeyStroke(keyCode, e.getModifiers());
        setText(KeymapUtil.getKeystrokeText(myKeyStroke));
      }
    }

    public KeyStroke getKeyStroke() {
      return myKeyStroke;
    }
  }

  private static class MyEditor implements ComboBoxEditor {
    private KeymapImpl myKeymap = null;
    private JTextField myTextField = new JTextField();

    public MyEditor() {
      myTextField.getDocument().addDocumentListener(new DocumentAdapter() {
        protected void textChanged(DocumentEvent e) {
          if (myKeymap != null){
            myKeymap.setName(myTextField.getText());
          }
        }
      });
      if (!LafManager.getInstance().isUnderAquaLookAndFeel()) {
        myTextField.setBorder(null);
      }
    }

    public Component getEditorComponent() {
      return myTextField;
    }

    public void setItem(Object anObject) {
      if (anObject instanceof KeymapImpl){
        myKeymap = (KeymapImpl)anObject;
        myTextField.setText(myKeymap.getPresentableName());
      }
    }

    public Object getItem() {
      return myKeymap;
    }

    public void selectAll() {
       myTextField.selectAll();
       myTextField.requestFocus();
    }

    public void addActionListener(ActionListener l) {}

    public void removeActionListener(ActionListener l) {}
  }
}