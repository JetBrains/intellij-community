package com.intellij.openapi.keymap.impl.ui;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DataManager;
import com.intellij.ide.TreeExpander;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.actionSystem.ex.QuickListsManager;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.impl.KeymapImpl;
import com.intellij.openapi.keymap.impl.KeymapManagerImpl;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class KeymapPanel extends JPanel {

  private JList myKeymapList;
  private JList myQuickListsList;
  private JList myShortcutsList;

  private DefaultListModel myKeymapListModel = new DefaultListModel();
  private DefaultListModel myQuickListsModel = new DefaultListModel();

  private KeymapImpl mySelectedKeymap;
  private KeymapImpl myActiveKeymap;

  private JButton mySetActiveButton;
  private JButton myCopyButton;
  private JButton myDeleteButton;
  private JButton myAddKeyboardShortcutButton;
  private JButton myAddMouseShortcutButton;
  private JButton myRemoveShortcutButton;
  private JTextField myKeymapNameField;
  private JLabel myBaseKeymapLabel;
  private JLabel myDescriptionLabel;

  private JCheckBox myDisableMnemonicsCheckbox;
  private ActionsTree myActionsTree;
  private FilterComponent myFilterComponent;
  private JBPopup myPopup = null;
  private boolean myTextFilterUsed = true;

  private final DocumentListener myKeymapNameListener = new DocumentAdapter() {
    public void textChanged(DocumentEvent event) {
      mySelectedKeymap.setName(myKeymapNameField.getText());
      myKeymapList.repaint();
    }
  };

  public KeymapPanel() {
    setLayout(new BorderLayout());
    JPanel headerPanel = new JPanel(new GridLayout(1, 2));
    headerPanel.add(createKeymapListPanel());
    headerPanel.add(createQuickListsPanel());
    add(headerPanel, BorderLayout.NORTH);
    add(createKeymapSettingsPanel(), BorderLayout.CENTER);
  }

  private JPanel createQuickListsPanel() {
    JPanel panel = new JPanel();
    panel.setBorder(IdeBorderFactory.createTitledBorder(KeyMapBundle.message("quick.lists.ide.border.factory.title")));
    panel.setLayout(new BorderLayout());
    myQuickListsList = new JList(myQuickListsModel);
    myQuickListsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myQuickListsList.setCellRenderer(new MyQuickListCellRenderer());

    if (myQuickListsModel.size() > 0) {
      myQuickListsList.setSelectedIndex(0);
    }

    JScrollPane scrollPane = new JScrollPane(myQuickListsList);
    scrollPane.setPreferredSize(new Dimension(180, 100));
    panel.add(scrollPane, BorderLayout.CENTER);
    panel.add(createQuickListButtonsPanel(), BorderLayout.EAST);

    return panel;
  }

  private JPanel createKeymapListPanel() {
    JPanel panel1 = new JPanel();
    panel1.setBorder(IdeBorderFactory.createTitledBorder(KeyMapBundle.message("keymaps.border.factory.title")));
    JPanel panel = panel1;
    panel.setLayout(new BorderLayout());
    myKeymapList = new JList(myKeymapListModel);
    myKeymapList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myKeymapList.setCellRenderer(new MyKeymapRenderer());
    JScrollPane scrollPane = new JScrollPane(myKeymapList);
    scrollPane.setPreferredSize(new Dimension(180, 100));
    panel.add(scrollPane, BorderLayout.WEST);

    JPanel rightPanel = new JPanel();
    rightPanel.setLayout(new BorderLayout());

    JPanel buttonsPanel = new JPanel();
    buttonsPanel.setLayout(new BorderLayout());
    buttonsPanel.add(createKeymapButtonsPanel(), BorderLayout.NORTH);

    rightPanel.add(buttonsPanel, BorderLayout.WEST);
    panel.add(rightPanel, BorderLayout.CENTER);

    myKeymapList.addListSelectionListener(
      new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          processCurrentKeymapChanged();
        }
      }
    );

    return panel;
  }

  private void processCurrentKeymapChanged() {
    myCopyButton.setEnabled(false);
    myDeleteButton.setEnabled(false);
    mySetActiveButton.setEnabled(false);
    myKeymapNameField.getDocument().removeDocumentListener(myKeymapNameListener);
    myKeymapNameField.setText("");
    myKeymapNameField.setEnabled(false);
    myBaseKeymapLabel.setText("");
    myAddKeyboardShortcutButton.setEnabled(false);
    myAddMouseShortcutButton.setEnabled(false);
    myRemoveShortcutButton.setEnabled(false);

    KeymapImpl selectedKeymap = getSelectedKeymap();
    mySelectedKeymap = selectedKeymap;
    if(selectedKeymap == null) {
      myActionsTree.reset(new KeymapImpl(), getCurrentQuickListIds());
      return;
    }

    myCopyButton.setEnabled(true);
    myKeymapNameField.setText(mySelectedKeymap.getPresentableName());
    myKeymapNameField.getDocument().addDocumentListener(myKeymapNameListener);

    Keymap parent = mySelectedKeymap.getParent();
    if (parent != null) {
      myBaseKeymapLabel.setText(KeyMapBundle.message("based.on.keymap.label", parent.getPresentableName()));
    }
    myDisableMnemonicsCheckbox.setSelected(!mySelectedKeymap.areMnemonicsEnabled());
    myDisableMnemonicsCheckbox.setEnabled(mySelectedKeymap.canModify());
    if(mySelectedKeymap.canModify()) {
      myDeleteButton.setEnabled(true);
      myKeymapNameField.setEnabled(true);
      myAddKeyboardShortcutButton.setEnabled(true);
      myAddMouseShortcutButton.setEnabled(true);
      myRemoveShortcutButton.setEnabled(true);
    }
    mySetActiveButton.setEnabled(mySelectedKeymap != myActiveKeymap);

    myActionsTree.reset(mySelectedKeymap, getCurrentQuickListIds());

    updateShortcutsList();
  }

  private QuickList[] getCurrentQuickListIds() {
    int size = myQuickListsModel.size();
    QuickList[] lists = new QuickList[size];
    for (int i = 0; i < lists.length; i++) {
      lists[i] = (QuickList)myQuickListsModel.getElementAt(i);
    }
    return lists;
  }

  private KeymapImpl getSelectedKeymap() {
    return (KeymapImpl)myKeymapList.getSelectedValue();
  }

  private List<Keymap> getAllKeymaps() {
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
    panel.add(scrollPane, new GridBagConstraints(1,1,1,1,1,1,GridBagConstraints.WEST,GridBagConstraints.BOTH, new Insets(0, 0, 0, 8), 0, 0));

    panel.add(
      createShortcutsButtonsPanel(),
      new GridBagConstraints(2,1,1,1,0,0,GridBagConstraints.NORTH,GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0)
    );

    myActionsTree.addListSelectionListener(
      new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          updateShortcutsList();
        }
      }
    );

    return panel;
  }

  private JPanel createQuickListButtonsPanel() {
    JPanel panel = new JPanel(new VerticalFlowLayout());
    final JButton newList = new JButton(KeyMapBundle.message("add.keymap.label"));
    final JButton editList = new JButton(KeyMapBundle.message("edit.keymap.label"));
    final JButton removeList = new JButton(KeyMapBundle.message("remove.keymap.label"));

    newList.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        QuickList newGroup = new QuickList(KeyMapBundle.message("unnamed.list.display.name"), "", ArrayUtil.EMPTY_STRING_ARRAY, false);
        QuickList edited = editList(newGroup);
        if (edited != null) {
          myQuickListsModel.addElement(edited);
          myQuickListsList.setSelectedIndex(myQuickListsModel.getSize() - 1);
          processCurrentKeymapChanged();
        }
      }
    });

    editList.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        editSelectedQuickList();
      }
    });

    removeList.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        deleteSelectedQuickList();
      }
    });

    myQuickListsList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        boolean enabled = myQuickListsList.getSelectedIndex() >= 0;
        removeList.setEnabled(enabled);
        editList.setEnabled(enabled);
      }
    });

    panel.add(newList);
    panel.add(editList);
    panel.add(removeList);

    editList.setEnabled(false);
    removeList.setEnabled(false);

    return panel;
  }

  private void deleteSelectedQuickList() {
    int idx = myQuickListsList.getSelectedIndex();
    if (idx < 0) {
      return;
    }

    QuickList list = (QuickList)myQuickListsModel.remove(idx);
    list.unregisterAllShortcuts(getAllKeymaps());

    int size = myQuickListsModel.getSize();
    if (size > 0) {
      myQuickListsList.setSelectedIndex(Math.min(idx, size - 1));
    }

    processCurrentKeymapChanged();
  }

  private QuickList editList(QuickList list) {
    List<Keymap> allKeymaps = getAllKeymaps();
    Map<Keymap, ArrayList<Shortcut>> listShortcuts = list.getShortcutMap(allKeymaps);
    list.unregisterAllShortcuts(allKeymaps);

    Project project = (Project)DataManager.getInstance().getDataContext(this).getData(DataConstants.PROJECT);
    EditQuickListDialog dlg = new EditQuickListDialog(project, list, getCurrentQuickListIds());
    dlg.show();

    QuickList editedList = dlg.getList();
    editedList.registerShortcuts(listShortcuts, allKeymaps);

    return dlg.isOK() ? editedList : null;
  }

  private void editSelectedQuickList() {
    QuickList list = (QuickList)myQuickListsList.getSelectedValue();
    if (list == null) {
      return;
    }

    QuickList newList = editList(list);
    if (newList != null) {
      myQuickListsModel.set(myQuickListsList.getSelectedIndex(), newList);
      processCurrentKeymapChanged();
    }
  }

  private JPanel createKeymapButtonsPanel() {
    JPanel panel = new JPanel();
    panel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
    panel.setLayout(new GridLayout(3, 1, 8, 4));
    mySetActiveButton = new JButton(KeyMapBundle.message("set.active.keymap.button"));
    mySetActiveButton.setMargin(new Insets(2,2,2,2));
    panel.add(mySetActiveButton);
    myCopyButton = new JButton(KeyMapBundle.message("copy.keymap.button"));
    myCopyButton.setMargin(new Insets(2,2,2,2));
    panel.add(myCopyButton);
    myDeleteButton = new JButton(KeyMapBundle.message("delete.keymap.button"));
    myDeleteButton.setMargin(new Insets(2,2,2,2));
    panel.add(myDeleteButton);

    mySetActiveButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          setKeymapActive();
        }
      }
    );

    myCopyButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          copyKeymap();
        }
      }
    );

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
    JPanel panel1 = new JPanel();
    panel1.setBorder(IdeBorderFactory.createTitledBorder(KeyMapBundle.message("keymap.settings.ide.border.factory.title")));
    JPanel panel = panel1;
    panel.setLayout(new GridBagLayout());

    panel.add(createKeymapNamePanel(), new GridBagConstraints(0,0,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(5,0,0,0),0,0));

/*
    JLabel actionsLabel = new JLabel("Actions:");
    actionsLabel.setDisplayedMnemonic('t');
    actionsLabel.setHorizontalAlignment(JLabel.LEFT);
    actionsLabel.setHorizontalTextPosition(JLabel.LEFT);
    panel.add(actionsLabel, new GridBagConstraints(0,2,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(5,0,0,0),0,0));
*/

    myActionsTree = new ActionsTree();
    JComponent component = myActionsTree.getComponent();
//    actionsLabel.setLabelFor(component);
    component.setPreferredSize(new Dimension(100, 300));

    panel.add(component, new GridBagConstraints(0,3,1,1,1,1,GridBagConstraints.WEST,GridBagConstraints.BOTH,new Insets(5,0,0,0),0,0));

    panel.add(createShortcutsPanel(), new GridBagConstraints(0,4,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(5,0,0,0),0,0));

    panel.add(createDescriptionPanel(), new GridBagConstraints(0,5,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(5,0,0,0),0,0));

    return panel;
  }

  private JPanel createDescriptionPanel() {
    JPanel panel1 = new JPanel();
    panel1.setBorder(IdeBorderFactory.createTitledBorder(KeyMapBundle.message("action.description.ide.border.factory.title")));
    JPanel panel = panel1;
    panel.setLayout(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 1;
    gbConstraints.weighty = 1;
    myDescriptionLabel = new JLabel(" ");
    panel.add(myDescriptionLabel, gbConstraints);
    return panel;
  }

  private JPanel createKeymapNamePanel() {
    JPanel panel = new JPanel(new GridBagLayout());

    panel.add(new JLabel(KeyMapBundle.message("key.map.name.label")), new GridBagConstraints(0,0,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(0, 0, 0, 8),0,0));

    myKeymapNameField = new JTextField();
    Dimension dimension = new Dimension(150, myKeymapNameField.getPreferredSize().height);
    myKeymapNameField.setPreferredSize(dimension);
    myKeymapNameField.setMinimumSize(dimension);
    panel.add(myKeymapNameField, new GridBagConstraints(1,0,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.NONE,new Insets(0, 8, 0, 0),0,0));

    myBaseKeymapLabel = new JLabel(KeyMapBundle.message("parent.keymap.label"));
    Dimension preferredSize = myBaseKeymapLabel.getPreferredSize();
    myBaseKeymapLabel.setPreferredSize(new Dimension(preferredSize.width*2,preferredSize.height));
    panel.add(myBaseKeymapLabel, new GridBagConstraints(2,0,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(0, 16, 0, 8),0,0));

    myDisableMnemonicsCheckbox = new JCheckBox(KeyMapBundle.message("disable.mnemonic.in.menu.check.box"));
    myDisableMnemonicsCheckbox.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        if (mySelectedKeymap != null) {
          mySelectedKeymap.setDisableMnemonics(myDisableMnemonicsCheckbox.isSelected());
        }
      }
    });
    panel.add(myDisableMnemonicsCheckbox, new GridBagConstraints(3,0,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.NONE,new Insets(0, 0, 0, 0),0,0));



    /* Icon collapseIcon = IconLoader.getIcon("/actions/collapsePanel.png");
    Icon expandIcon = IconLoader.getIcon("/actions/expandPanel.png");
   final CollapsiblePanel filteringComponent = new CollapsiblePanel(createFilteringPanel(), true, false, collapseIcon, expandIcon, null);
    filteringComponent.setBorder(IdeBorderFactory.createTitledBorder("Filter"));
    filteringComponent.setMaximumSize(new Dimension(50, -1));
    filteringComponent.addCollapsingListener(new CollapsingListener() {
      public void onCollapsingChanged(CollapsiblePanel panel, boolean newValue) {
        if (filteringComponent.isCollapsed()){
          myActionsTree.filter(null, getCurrentQuickListIds()); //clear filtering
        }
      }
    });
    panel.add(filteringComponent, new GridBagConstraints(0,1,4,1,1,1, GridBagConstraints.EAST, GridBagConstraints.VERTICAL, new Insets(8,8,0,10), 0,0));
*/

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
    group.add(commonActionsManager.createCollapseAllAction(treeExpander));
    group.add(commonActionsManager.createExpandAllAction(treeExpander));
    group.add(new AnAction(InspectionsBundle.message("inspection.tools.action.filter"), InspectionsBundle.message("inspection.tools.action.filter"), IconLoader.getIcon("/ant/filter.png")) {
      public void actionPerformed(AnActionEvent e) {
        if (myPopup == null || myPopup.getContent() == null){
          myPopup = JBPopupFactory.getInstance().createComponentPopup(createFilteringPanel(), null, true);
        }
        myPopup.showUnderneathOf(toolbar);
      }
    });
    group.add(new AnAction(KeyMapBundle.message("filter.clear.action.text"),
                           KeyMapBundle.message("filter.clear.action.text"), IconLoader.getIcon("/actions/gc.png")) {
      public void actionPerformed(AnActionEvent e) {
        myActionsTree.filter(null, getCurrentQuickListIds()); //clear filtering
      }
    });
    panel.add(toolbar, new GridBagConstraints(0,1,4,1,1,1, GridBagConstraints.EAST, GridBagConstraints.VERTICAL, new Insets(8,8,0,10), 0,0));

    return panel;
  }


  private JPanel createFilteringPanel() {
    JPanel filterComponent = new JPanel(new GridBagLayout());
    filterComponent.setBorder(BorderFactory.createEmptyBorder(0, 2, 2, 2));
    final TitlePanel captionPanel = new TitlePanel();
    captionPanel.setText(KeyMapBundle.message("filter.settings.popup.title"));
    filterComponent.add(captionPanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1, 0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(0, -2, 8, -2), 0,0));

    final JRadioButton textFilter = new JRadioButton(KeyMapBundle.message("filter.text.title"));
    filterComponent.add(textFilter, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0,0,0,0),0,0));

    myFilterComponent = new FilterComponent("KEYMAP", 5, false, false){
      protected void filter() {
        myActionsTree.filter(getFilter(), getCurrentQuickListIds());
      }
    };
    myFilterComponent.reset();
    filterComponent.add(myFilterComponent, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0,0,0,0),0,0));

    JRadioButton shortcutFilter = new JRadioButton(KeyMapBundle.message("filter.shortcut.title"));
    filterComponent.add(shortcutFilter, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0,0,0,0),0,0));

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
    filterComponent.add(firstLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(5,20,0,0),0,0));
    filterComponent.add(firstShortcut, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0,20,0,0),0,0));
    enable2Shortcut.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        secondShortcut.setEnabled(enable2Shortcut.isSelected());
        if (enable2Shortcut.isSelected()){
          secondShortcut.requestFocusInWindow();
        }
      }
    });
    filterComponent.add(enable2Shortcut, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0,15,0,0),0,0));
    final JLabel secondLabel = new JLabel(KeyMapBundle.message("filter.second.stroke.input"));
    filterComponent.add(secondLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0,20,0,0),0,0));
    filterComponent.add(secondShortcut, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0,20,0,0),0,0));
    enable2Shortcut.setSelected(false);
    secondShortcut.setEnabled(false);
    ActionListener enabledListener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        enableFilterComponents(textFilter.isSelected(), firstShortcut, secondShortcut, enable2Shortcut, secondLabel, firstLabel);
      }
    };
    ButtonGroup group = new ButtonGroup();
    group.add(textFilter);
    group.add(shortcutFilter);
    textFilter.setSelected(myTextFilterUsed);
    shortcutFilter.setSelected(!myTextFilterUsed);
    textFilter.addActionListener(enabledListener);
    shortcutFilter.addActionListener(enabledListener);
    enableFilterComponents(myTextFilterUsed, firstShortcut, secondShortcut, enable2Shortcut, secondLabel, firstLabel);
    return filterComponent;
  }

  private void enableFilterComponents(final boolean textFilterSelected,
                                      final ShortcutTextField firstShortcut,
                                      final ShortcutTextField secondShortcut,
                                      final JCheckBox enable2Shortcut,
                                      final JLabel secondLabel,
                                      final JLabel firstLabel) {
    myTextFilterUsed = textFilterSelected;
    GuiUtils.enableChildren(myFilterComponent, textFilterSelected);
    firstShortcut.setEnabled(!textFilterSelected);
    secondShortcut.setEnabled(!textFilterSelected && enable2Shortcut.isSelected());
    enable2Shortcut.setEnabled(!textFilterSelected);
    secondLabel.setEnabled(!textFilterSelected && enable2Shortcut.isSelected());
    firstLabel.setEnabled(!textFilterSelected);
    if (textFilterSelected) {
      myFilterComponent.requestFocusInWindow();
    } else {
      firstShortcut.requestFocusInWindow();
    }
  }

  private void filterTreeByShortcut(final ShortcutTextField firstShortcut,
                                    final JCheckBox enable2Shortcut,
                                    final ShortcutTextField secondShortcut) {
    final KeyStroke keyStroke = firstShortcut.getKeyStroke();
    if (keyStroke != null){
      myActionsTree.filterTree(new KeyboardShortcut(keyStroke, enable2Shortcut.isSelected() ? secondShortcut.getKeyStroke() : null), getCurrentQuickListIds());
    }
  }

  public void showOption(String option){
    myActionsTree.filter(option, getCurrentQuickListIds());
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
        for(Iterator<String> actionIds = conflicts.keySet().iterator(); actionIds.hasNext(); ) {
          String id = actionIds.next();
          ArrayList<KeyboardShortcut> list = conflicts.get(id);
          for (Iterator<KeyboardShortcut> iterator = list.iterator(); iterator.hasNext();) {
            KeyboardShortcut shortcut = iterator.next();
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
        for(int i = 0; i < actionIds.length; i++) {
          String id = actionIds[i];
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
  }

  private void repaintLists() {
    myActionsTree.getComponent().repaint();
    myKeymapList.repaint();
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
  }

  private void setKeymapActive() {
    KeymapImpl keymap = getSelectedKeymap();
    if(keymap != null) {
      myActiveKeymap = keymap;
    }
    myKeymapList.repaint();
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
    myKeymapList.setSelectedValue(newKeymap, true);
    processCurrentKeymapChanged();

    int result = Messages.showYesNoDialog(this, KeyMapBundle.message("is.new.keymap.active.dialog.message"), KeyMapBundle.message("is.new.keymap.active.dialog.title"), Messages.getQuestionIcon());
    if(result == 0) {
      myActiveKeymap = newKeymap;
      myKeymapList.repaint();
    }

    if (myKeymapNameField.isEnabled()) {
      myKeymapNameField.setSelectionStart(0);
      myKeymapNameField.setSelectionEnd(myKeymapNameField.getText().length());
      myKeymapNameField.requestFocus();

    }
  }

  private boolean tryNewKeymapName(String name) {
    for(int i=0; i<myKeymapListModel.size(); i++) {
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
    ListUtil.removeSelectedItems(myKeymapList);
    int count = myKeymapListModel.getSize();
    if(count >= 0) {
      if (myActiveKeymap == keymap) {
        myActiveKeymap = (KeymapImpl)myKeymapListModel.getElementAt(0);
      }
    }
    else {
      myActiveKeymap = null;
    }
    processCurrentKeymapChanged();
    myKeymapList.repaint();
  }


  private void updateShortcutsList() {
    DefaultListModel shortcutsModel = (DefaultListModel)myShortcutsList.getModel();
    shortcutsModel.clear();
    String actionId = myActionsTree.getSelectedActionId();
    myDescriptionLabel.setText(" ");
    if (actionId != null && mySelectedKeymap != null) {
      AnAction action = ActionManager.getInstance().getAction(actionId);
      if (action != null) {
        String description = action.getTemplatePresentation().getDescription();
        if (description != null && description.trim().length() > 0) {
          myDescriptionLabel.setText(description);
        }
      }
      else {
        QuickList list = myActionsTree.getSelectedQuickList();
        if (list != null) {
          String description = list.getDescription().trim();
          if (description.length() > 0) {
            myDescriptionLabel.setText(description);
          }
        }
      }

      Shortcut[] shortcuts = mySelectedKeymap.getShortcuts(actionId);
      for(int i = 0; i < shortcuts.length; i++){
        shortcutsModel.addElement(shortcuts[i]);
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

  private final class MyKeymapRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean selected, boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, selected, cellHasFocus);
      Keymap keymap = (Keymap)value;

      // Set color and font.

      Font font = getFont();
      if(keymap == myActiveKeymap) {
        font = font.deriveFont(Font.BOLD);
      }
      setFont(font);
      if(selected){
        setForeground(UIUtil.getListSelectionForeground());
      }else{
        if(keymap.canModify()){
          setForeground(UIUtil.getListForeground());
        }else{
          setForeground(Color.GRAY);
        }
      }

      // Set text.

      String name = keymap.getPresentableName();
      if(name == null) {
        name = KeyMapBundle.message("keymap.noname.presentable.name");
      }
      if(keymap == myActiveKeymap) {
        name = KeyMapBundle.message("keymap.active.presentable.name", name);
      }
      setText(name);
      return this;
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
    for(int i = 0; i < keymaps.length; i++){
      KeymapImpl keymap = (KeymapImpl)keymaps[i];
      if(keymap.canModify()) {
        keymap = keymap.copy();
      }
      myKeymapListModel.addElement(keymap);
      if(keymapManager.getActiveKeymap() == keymaps[i]) {
        myActiveKeymap = keymap;
      }
    }

    if(myKeymapListModel.getSize() == 0) {
      KeymapImpl keymap = new KeymapImpl();
      keymap.setName(KeyMapBundle.message("keymap.no.name"));
      myKeymapListModel.addElement(keymap);
    }


    myQuickListsModel.removeAllElements();
    QuickList[] allQuickLists = QuickListsManager.getInstance().getAllQuickLists();
    for (int i = 0; i < allQuickLists.length; i++) {
      QuickList list = allQuickLists[i];
      myQuickListsModel.addElement(list);
    }

    mySelectedKeymap = (KeymapImpl)myKeymapListModel.elementAt(0);
    myKeymapList.setSelectedValue(myActiveKeymap, true);
  }

  public void apply() throws ConfigurationException{
    ensureUniqueKeymapNames();

    final KeymapManagerImpl keymapManager = (KeymapManagerImpl)KeymapManager.getInstance();
    keymapManager.removeAllKeymapsExceptUnmodifiable();
    Keymap activeKeyMapToSet = myActiveKeymap;
    for(int i = 0; i < myKeymapListModel.getSize(); i++){
      final Keymap modelKeymap = (Keymap)myKeymapListModel.elementAt(i);
      if(modelKeymap.canModify()) {
        final KeymapImpl keymapToAdd = ((KeymapImpl)modelKeymap).copy();
        if (modelKeymap == myActiveKeymap) {
          activeKeyMapToSet = keymapToAdd;
        }
        keymapManager.addKeymap(keymapToAdd);
      }
    }
    keymapManager.setActiveKeymap(activeKeyMapToSet);
    try {
      keymapManager.save();
    }
    catch (IOException e) {
      throw new ConfigurationException(e.getMessage());
    }

    QuickListsManager.getInstance().removeAllQuickLists();
    int size = myQuickListsModel.getSize();
    for (int i = 0; i < size; i++) {
      QuickList list = (QuickList)myQuickListsModel.getElementAt(i);
      QuickListsManager.getInstance().registerQuickList(list, false);
    }

    QuickListsManager.getInstance().registerActions();
  }

  private void ensureUniqueKeymapNames() throws ConfigurationException {
    final Set<String> keymapNames = new HashSet<String>();
    for(int i = 0; i < myKeymapListModel.getSize(); i++){
      final Keymap modelKeymap = (Keymap)myKeymapListModel.elementAt(i);
      String name = modelKeymap.getName();
      if (keymapNames.contains(name)) {
        throw new ConfigurationException(KeyMapBundle.message("configuration.all.keymaps.should.have.unique.names.error.message"));
      }
      keymapNames.add(name);
    }
  }

  boolean isModified() {
    KeymapManagerEx keymapManager = KeymapManagerEx.getInstanceEx();
    if (!Comparing.equal(myActiveKeymap, keymapManager.getActiveKeymap())) {
      return true;
    }
    Keymap[] managerKeymaps = keymapManager.getAllKeymaps();
    Keymap[] panelKeymaps = new Keymap[myKeymapListModel.getSize()];
    for(int i = 0; i < myKeymapListModel.getSize(); i++){
      panelKeymaps[i] = (Keymap)myKeymapListModel.elementAt(i);
    }

    if (!Comparing.equal(managerKeymaps, panelKeymaps)) {
      return true;
    }
    QuickList[] storedLists = QuickListsManager.getInstance().getAllQuickLists();

    QuickList[] modelLists = new QuickList[myQuickListsModel.getSize()];
    for (int i = 0; i < modelLists.length; i++) {
      modelLists[i] = (QuickList)myQuickListsModel.getElementAt(i);
    }

    return !Comparing.equal(storedLists, modelLists);
  }

  public Dimension getPreferredSize() {
    //TODO[anton]: it's a hack!!!
    Dimension preferredSize = super.getPreferredSize();
    if (preferredSize.height > 600) {
      preferredSize.height = 600;
    }
    return preferredSize;
  }

  public void selectAction(String actionId) {
    myActionsTree.selectAction(actionId);
  }

  private static class MyQuickListCellRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(JList list,
                                                  Object value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      QuickList quickList = (QuickList)value;
      setText(quickList.getDisplayName());
      return this;
    }
  }

  public class ShortcutTextField extends JTextField {
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
}