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

package com.intellij.execution.impl;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.UnknownRunConfiguration;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorListener;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.config.StorageAccessors;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.net.URL;
import java.util.*;

class RunConfigurable extends BaseConfigurable {
  private static final Icon ICON = IconLoader.getIcon("/general/configurableRunDebug.png");
  @NonNls private static final String GENERAL_ADD_ICON_PATH = "/general/add.png";
  private static final Icon ADD_ICON = IconLoader.getIcon(GENERAL_ADD_ICON_PATH);
  private static final Icon REMOVE_ICON = IconLoader.getIcon("/general/remove.png");
  private static final Icon COPY_ICON = IconLoader.getIcon("/actions/copy.png");
  private static final Icon SAVE_ICON = IconLoader.getIcon("/runConfigurations/saveTempConfig.png");
  @NonNls private static final String EDIT_DEFAULTS_ICON_PATH = "/general/ideOptions.png";
  private static final Icon EDIT_DEFAULTS_ICON = IconLoader.getIcon(EDIT_DEFAULTS_ICON_PATH);
  @NonNls private static final String DIVIDER_PROPORTION = "dividerProportion";

  private final Project myProject;
  private final RunDialog myRunDialog;
  @NonNls private final DefaultMutableTreeNode myRoot = new DefaultMutableTreeNode("Root");
  private final Tree myTree = new Tree(myRoot);
  private final JPanel myRightPanel = new JPanel(new BorderLayout());
  private JComponent myToolbarComponent;
  private final Splitter myPanel = new Splitter();
  private JPanel myWholePanel;
  private StorageAccessors myConfig;
  private SingleConfigurationConfigurable<RunConfiguration> mySelectedConfigurable = null;
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.impl.RunConfigurable");
  private final JTextField myRecentsLimit = new JTextField("5");

  public RunConfigurable(final Project project) {
    this(project, null);
  }

  public RunConfigurable(final Project project, final RunDialog runDialog) {
    myProject = project;
    myRunDialog = runDialog;
  }

  public String getDisplayName() {
    return ExecutionBundle.message("run.configurable.display.name");
  }

  private void initTree() {
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    UIUtil.setLineStyleAngled(myTree);
    TreeToolTipHandler.install(myTree);
    TreeUtil.installActions(myTree);
    PopupHandler.installFollowingSelectionTreePopup(myTree, createActionsGroup(), ActionPlaces.UNKNOWN, ActionManager.getInstance());
    myTree.setCellRenderer(new ColoredTreeCellRenderer() {
      public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row,
                                        boolean hasFocus) {
        if (value instanceof DefaultMutableTreeNode) {
          final DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
          final Object userObject = node.getUserObject();
          if (userObject instanceof ConfigurationType) {
            final ConfigurationType configurationType = (ConfigurationType)userObject;
            append(configurationType.getDisplayName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
            setIcon(configurationType.getIcon());
          }
          else {
            final RunManager runManager = getRunManager();
            RunConfiguration configuration = null;
            String name = null;
            if (userObject instanceof SingleConfigurationConfigurable) {
              final SingleConfigurationConfigurable<?> settings = (SingleConfigurationConfigurable)userObject;
              RunnerAndConfigurationSettings snapshot;
              boolean valid = true;
              try {
                snapshot = settings.getSnapshot();
              }
              catch (ConfigurationException e) {
                valid = false;
                snapshot = settings.getSettings();
              }
              configuration = settings.getConfiguration();
              name = settings.getNameText();
              setIcon(ProgramRunnerUtil.getConfigurationIcon(snapshot, !valid, runManager.isTemporary(configuration)));
            }
            else if (userObject instanceof RunnerAndConfigurationSettingsImpl) {
              RunnerAndConfigurationSettings settings = (RunnerAndConfigurationSettings)userObject;
              setIcon(ProgramRunnerUtil.getConfigurationIcon(getProject(), settings));
              configuration = settings.getConfiguration();
              name = configuration.getName();
            }
            if (configuration != null) {
              append(name, runManager.isTemporary(configuration)
                           ? SimpleTextAttributes.GRAY_ATTRIBUTES
                           : SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }
          }
        }
      }
    });
    final RunManagerEx manager = getRunManager();
    final ConfigurationType[] factories = manager.getConfigurationFactories();
    for (ConfigurationType type : factories) {
      final RunnerAndConfigurationSettings[] configurations = manager.getConfigurationSettings(type);
      if (configurations != null && configurations.length > 0) {
        final DefaultMutableTreeNode typeNode = new DefaultMutableTreeNode(type);
        myRoot.add(typeNode);
        for (RunnerAndConfigurationSettings configuration : configurations) {
          typeNode.add(new DefaultMutableTreeNode(configuration));
        }
      }
    }

    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        final TreePath selectionPath = myTree.getSelectionPath();
        if (selectionPath != null) {
          DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
          final Object userObject = node.getUserObject();
          if (userObject instanceof RunnerAndConfigurationSettingsImpl) {
            final SingleConfigurationConfigurable<RunConfiguration> configurationConfigurable =
                SingleConfigurationConfigurable.editSettings((RunnerAndConfigurationSettings)userObject);
            installUpdateListeners(configurationConfigurable);
            node.setUserObject(configurationConfigurable);
            updateRightPanel(configurationConfigurable);
          }
          else if (userObject instanceof SingleConfigurationConfigurable) {
            updateRightPanel((SingleConfigurationConfigurable<RunConfiguration>)userObject);
          }
          else if (userObject instanceof ConfigurationType) {
            drawPressAddButtonMessage((ConfigurationType)userObject);
          }
        }
        updateDialog();
      }
    });
    myTree.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        clickDefaultButton();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED);
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        myTree.requestFocusInWindow();
        final RunnerAndConfigurationSettings settings = manager.getSelectedConfiguration();
        if (settings != null) {
          final Enumeration enumeration = myRoot.breadthFirstEnumeration();
          while (enumeration.hasMoreElements()) {
            final DefaultMutableTreeNode node = (DefaultMutableTreeNode)enumeration.nextElement();
            final Object userObject = node.getUserObject();
            if (userObject instanceof RunnerAndConfigurationSettingsImpl) {
              final RunnerAndConfigurationSettings runnerAndConfigurationSettings = (RunnerAndConfigurationSettings)userObject;
              final ConfigurationType configurationType = settings.getType();
              if (configurationType != null &&
                  Comparing.strEqual(runnerAndConfigurationSettings.getConfiguration().getType().getId(), configurationType.getId()) &&
                  Comparing.strEqual(runnerAndConfigurationSettings.getConfiguration().getName(), settings.getName())) {
                TreeUtil.selectInTree(node, true, myTree);
                return;
              }
            }
          }
        }
        else {
          mySelectedConfigurable = null;
        }
        TreeUtil.selectFirstNode(myTree);
        drawPressAddButtonMessage(null);
      }
    });
    sortTree(myRoot);
    ((DefaultTreeModel)myTree.getModel()).reload();
  }

  private void updateRightPanel(final SingleConfigurationConfigurable<RunConfiguration> userObject) {
    myRightPanel.removeAll();
    mySelectedConfigurable = userObject;
    myRightPanel.add(mySelectedConfigurable.createComponent(), BorderLayout.CENTER);
    setupDialogBounds();
  }


  public static void sortTree(DefaultMutableTreeNode root) {
    TreeUtil.sort(root, new Comparator() {
      public int compare(final Object o1, final Object o2) {
        final Object userObject1 = ((DefaultMutableTreeNode)o1).getUserObject();
        final Object userObject2 = ((DefaultMutableTreeNode)o2).getUserObject();
        if (userObject1 instanceof ConfigurationType && userObject2 instanceof ConfigurationType) {
          return ((ConfigurationType)userObject1).getDisplayName().compareTo(((ConfigurationType)userObject2).getDisplayName());
        }
        return 0;
      }
    });
  }

  private void update() {
    updateDialog();
    final TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath != null) {
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
      ((DefaultTreeModel)myTree.getModel()).reload(node);
    }
  }

  private void installUpdateListeners(final SingleConfigurationConfigurable<RunConfiguration> info) {
    info.getEditor().addSettingsEditorListener(new SettingsEditorListener<RunnerAndConfigurationSettings>() {
      public void stateChanged(final SettingsEditor<RunnerAndConfigurationSettings> editor) {
        update();
        setupDialogBounds();
      }
    });

    info.addNameListner(new DocumentListener() {
      public void insertUpdate(DocumentEvent e) {
        update();
      }

      public void removeUpdate(DocumentEvent e) {
        update();
      }

      public void changedUpdate(DocumentEvent e) {
        update();
      }
    });
  }

  private void drawPressAddButtonMessage(final ConfigurationType configurationType) {
    final JEditorPane browser = new JEditorPane();
    browser.setBorder(null);
    browser.setEditable(false);
    browser.setEditorKit(new HTMLEditorKit());
    browser.setBackground(myRightPanel.getBackground());
    final URL addUrl = getClass().getResource(GENERAL_ADD_ICON_PATH);
    final URL defaultsURL = getClass().getResource(EDIT_DEFAULTS_ICON_PATH);
    final Font font = UIUtil.getLabelFont();
    final String configurationTypeDescription = configurationType != null
                                                ? configurationType.getConfigurationTypeDescription()
                                                : ExecutionBundle.message("run.configuration.default.type.description");
    browser.setText(ExecutionBundle.message("empty.run.configuration.panel.text.label", font.getFontName(), addUrl, defaultsURL,
                                            configurationTypeDescription));
    browser.setPreferredSize(new Dimension(200, 50));
    browser.addHyperlinkListener(new HyperlinkListener() {
      public void hyperlinkUpdate(final HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          if (Comparing.strEqual(e.getDescription(), "add")) {
            new MyToolbarAddAction().actionPerformed(null);
          }
          else {
            editDefaultsConfiguration();
          }
        }
      }
    });
    myRightPanel.removeAll();
    myRightPanel.add(browser, BorderLayout.CENTER);
    myRightPanel.revalidate();
    myRightPanel.repaint();
  }

  private JPanel createLeftPanel() {
    final JPanel leftPanel = new JPanel(new BorderLayout());
    myToolbarComponent = ActionManager.getInstance().
        createActionToolbar(ActionPlaces.UNKNOWN,
                            createActionsGroup(),
                            true).getComponent();
    leftPanel.add(myToolbarComponent, BorderLayout.NORTH);
    initTree();
    final JScrollPane pane = ScrollPaneFactory.createScrollPane(myTree);
    pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    leftPanel.add(pane, BorderLayout.CENTER);
    final JButton editDefaultsButton = new JButton(ExecutionBundle.message("run.configuration.edit.default.configuration.settings.button"));
    editDefaultsButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        editDefaultsConfiguration();
      }
    });

    final JPanel bottomPanel = new JPanel(new BorderLayout());
    bottomPanel.add(editDefaultsButton, BorderLayout.NORTH);
    bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));

    Box box = new Box(BoxLayout.LINE_AXIS);
    box.setBorder(BorderFactory.createEmptyBorder(7, 5, 3, 0));
    box.add(new JLabel("Temporary configurations limit:"));
    Dimension size = new Dimension(25, myRecentsLimit.getPreferredSize().height);
    myRecentsLimit.setPreferredSize(size);
    myRecentsLimit.setMaximumSize(size);
    myRecentsLimit.setMinimumSize(size);
    box.add(myRecentsLimit);
    myRecentsLimit.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        setModified(true);
      }
    });
    box.add(Box.createHorizontalGlue());
    bottomPanel.add(box, BorderLayout.CENTER);

    leftPanel.add(bottomPanel, BorderLayout.SOUTH);
    return leftPanel;
  }

  private void editDefaultsConfiguration() {
    new SingleConfigurableEditor(getProject(),
                                 new DefaultConfigurationSettingsEditor(myProject,
                                                                        getSelectedConfigurationType())).show();
  }

  private DefaultActionGroup createActionsGroup() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new MyToolbarAddAction());
    group.add(new MyRemoveAction());
    group.add(new MyCopyAction());
    group.add(new MySaveAction());
    group.add(new AnAction(ExecutionBundle.message("run.configuration.edit.default.configuration.settings.button"),
                           ExecutionBundle.message("run.configuration.edit.default.configuration.settings.button"), EDIT_DEFAULTS_ICON) {
      public void actionPerformed(final AnActionEvent e) {
        editDefaultsConfiguration();
      }
    });
    group.add(new MyMoveAction(ExecutionBundle.message("move.up.action.name"), null, IconLoader.getIcon("/actions/moveUp.png"), -1));
    group.add(new MyMoveAction(ExecutionBundle.message("move.down.action.name"), null, IconLoader.getIcon("/actions/moveDown.png"), 1));
    return group;
  }

  @Nullable
  private ConfigurationType getSelectedConfigurationType() {
    final TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath == null) return null;
    final DefaultMutableTreeNode configurationTypeNode = getSelectedConfigurationTypeNode();
    return configurationTypeNode != null ? (ConfigurationType)configurationTypeNode.getUserObject() : null;
  }

  public JComponent createComponent() {
    myWholePanel = new JPanel(new BorderLayout());
    myPanel.setHonorComponentsMinimumSize(true);
    myConfig = StorageAccessors.createGlobal("runConfigurationTab");
    myPanel.setShowDividerControls(true);
    myPanel.setProportion(myConfig.getFloat(DIVIDER_PROPORTION, 0.2f));
    myPanel.setHonorComponentsMinimumSize(true);
    myPanel.setFirstComponent(createLeftPanel());
    myPanel.setSecondComponent(myRightPanel);
    myWholePanel.add(myPanel, BorderLayout.CENTER);

    updateDialog();

    Dimension d = myWholePanel.getPreferredSize();
    d.width = Math.max(d.width, 800);
    d.height = Math.max(d.height, 600);
    myWholePanel.setPreferredSize(d);

    return myWholePanel;
  }

  public Icon getIcon() {
    return ICON;
  }

  public void reset() {
    final RunManagerEx manager = getRunManager();
    final RunManagerConfig config = manager.getConfig();
    myRecentsLimit.setText(Integer.toString(config.getRecentsLimit()));
    setModified(false);
  }

  private Project getProject() {
    return myProject;
  }

  public void apply() throws ConfigurationException {
    final RunManagerImpl manager = getRunManager();
    final ConfigurationType[] configurationTypes = manager.getConfigurationFactories();
    for (ConfigurationType configurationType : configurationTypes) {
      applyByType(configurationType);
    }

    if (mySelectedConfigurable != null) {
      manager.setSelectedConfiguration(mySelectedConfigurable.getSettings());
    }
    else {
      manager.setSelectedConfiguration(null);
    }

    String recentsLimit = myRecentsLimit.getText();
    try {
      int i = Integer.parseInt(recentsLimit);
      int oldLimit = manager.getConfig().getRecentsLimit();
      if (oldLimit != i) {
        manager.getConfig().setRecentsLimit(i);
        manager.checkRecentsLimit();
      }
    }
    catch (NumberFormatException e) {
      // ignore
    }

    setModified(false);
  }

  private void applyByType(ConfigurationType type) throws ConfigurationException {
    DefaultMutableTreeNode typeNode = getConfigurationTypeNode(type);
    final RunManagerImpl manager = getRunManager();
    final ArrayList<RunConfigurationBean> stableConfigurations = new ArrayList<RunConfigurationBean>();
    if (typeNode != null) {
      for (int i = 0; i < typeNode.getChildCount(); i++) {
        final Object userObject = ((DefaultMutableTreeNode)typeNode.getChildAt(i)).getUserObject();
        if (userObject instanceof SingleConfigurationConfigurable) {
          final SingleConfigurationConfigurable configurable = (SingleConfigurationConfigurable)userObject;
          final RunnerAndConfigurationSettings settings = (RunnerAndConfigurationSettings)configurable.getSettings();
          if (manager.isTemporary(settings)) {
            applyConfiguration(typeNode, configurable);
          }
          stableConfigurations.add(new RunConfigurationBean(configurable));
        }
        else if (userObject instanceof RunnerAndConfigurationSettingsImpl) {
          RunnerAndConfigurationSettings settings = (RunnerAndConfigurationSettings)userObject;
            stableConfigurations.add(new RunConfigurationBean(settings,
                                                              manager.isConfigurationShared(settings),
                                                              manager.getBeforeRunTasks(settings.getConfiguration())));
        }
      }
    }
    // try to apply all
    for (RunConfigurationBean bean : stableConfigurations) {
      final SingleConfigurationConfigurable configurable = bean.getConfigurable();
      if (configurable != null) {
        applyConfiguration(typeNode, configurable);
      }
    }

    // if apply succeeded, update the list of configurations in RunManager
    manager.removeConfigurations(type);
    for (final RunConfigurationBean stableConfiguration : stableConfigurations) {
      manager.addConfiguration(stableConfiguration.getSettings(),
                               stableConfiguration.isShared(),
                               stableConfiguration.getStepsBeforeLaunch());
    }
  }

  @Nullable
  private DefaultMutableTreeNode getConfigurationTypeNode(final ConfigurationType type) {
    for (int i = 0; i < myRoot.getChildCount(); i++) {
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)myRoot.getChildAt(i);
      if (node.getUserObject() == type) return node;
    }
    return null;
  }

  private void applyConfiguration(DefaultMutableTreeNode typeNode, SingleConfigurationConfigurable configurable)
      throws ConfigurationException {
    try {
      if (configurable != null) configurable.apply();
    }
    catch (ConfigurationException e) {
      for (int i = 0; i < typeNode.getChildCount(); i++) {
        final DefaultMutableTreeNode node = (DefaultMutableTreeNode)typeNode.getChildAt(i);
        if (Comparing.equal(configurable, node.getUserObject())) {
          TreeUtil.selectNode(myTree, node);
          break;
        }
      }
      throw e;
    }
  }

  public boolean isModified() {
    if (super.isModified()) return true;
    final RunManagerImpl runManager = getRunManager();
    final RunnerAndConfigurationSettings settings = runManager.getSelectedConfiguration();
    if (mySelectedConfigurable == null) {
      return settings != null;
    }
    if (settings == null ||
        !Comparing.strEqual(mySelectedConfigurable.getConfiguration().getType().getId(), settings.getType().getId()) ||
        (Comparing.strEqual(mySelectedConfigurable.getConfiguration().getType().getId(), settings.getType().getId()) &&
         !Comparing.strEqual(mySelectedConfigurable.getNameText(), settings.getConfiguration().getName()))) {
      return true;
    }
    final RunConfiguration[] allConfigurations = runManager.getAllConfigurations();
    final Set<RunConfiguration> currentConfigurations = new HashSet<RunConfiguration>();
    for (int i = 0; i < myRoot.getChildCount(); i++) {
      DefaultMutableTreeNode typeNode = (DefaultMutableTreeNode)myRoot.getChildAt(i);
      final RunnerAndConfigurationSettings[] configurationSettings =
          runManager.getConfigurationSettings((ConfigurationType)typeNode.getUserObject());
      if (configurationSettings.length != typeNode.getChildCount()) return true;
      for (int j = 0; j < typeNode.getChildCount(); j++) {
        final Object userObject = ((DefaultMutableTreeNode)typeNode.getChildAt(j)).getUserObject();
        if (userObject instanceof SingleConfigurationConfigurable) {
          SingleConfigurationConfigurable configurable = (SingleConfigurationConfigurable)userObject;
          if (!Comparing.strEqual(configurationSettings[j].getConfiguration().getName(), configurable.getConfiguration().getName())) {
            return true;
          }
          if (configurable.isModified()) return true;
          currentConfigurations.add(configurable.getConfiguration());
        }
        else if (userObject instanceof RunnerAndConfigurationSettingsImpl) {
          currentConfigurations.add(((RunnerAndConfigurationSettings)userObject).getConfiguration());
        }
      }
    }
    for (RunConfiguration configuration : allConfigurations) {
      if (!currentConfigurations.contains(configuration)) return true;
    }
    return false;
  }

  public void disposeUIResources() {
    TreeUtil.traverseDepth(myRoot, new TreeUtil.Traverse() {
      public boolean accept(Object node) {
        if (node instanceof DefaultMutableTreeNode) {
          final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)node;
          final Object userObject = treeNode.getUserObject();
          if (userObject instanceof SingleConfigurationConfigurable) {
            ((SingleConfigurationConfigurable)userObject).disposeUIResources();
          }
        }
        return true;
      }
    });
    myRightPanel.removeAll();
    myConfig.setFloat(DIVIDER_PROPORTION, myPanel.getProportion());
    myPanel.dispose();
  }

  private void updateDialog() {
    if (myRunDialog == null) return;
    final StringBuilder buffer = new StringBuilder();
    Executor executor = myRunDialog.getExecutor();
    buffer.append(executor.getId());
    final SingleConfigurationConfigurable<RunConfiguration> configuration = getSelectedConfiguration();
    if (configuration != null) {
      buffer.append(" - ");
      buffer.append(configuration.getNameText());
    }
    myRunDialog.setOKActionEnabled(canRunConfiguration(configuration, executor));
    myRunDialog.setTitle(buffer.toString());
  }

  private void setupDialogBounds() {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        UIUtil.setupEnclosingDialogBounds(myWholePanel);
      }
    });
  }

  @Nullable
  private SingleConfigurationConfigurable<RunConfiguration> getSelectedConfiguration() {
    final TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath != null) {
      final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
      final Object userObject = treeNode.getUserObject();
      if (userObject instanceof SingleConfigurationConfigurable) {
        return (SingleConfigurationConfigurable<RunConfiguration>)userObject;
      }
    }
    return null;
  }

  private static boolean canRunConfiguration(@Nullable SingleConfigurationConfigurable<RunConfiguration> configuration, final @NotNull Executor executor) {
    try {
      return configuration != null && RunManagerImpl.canRunConfiguration(configuration.getSnapshot(), executor);
    }
    catch (ConfigurationException e) {
      return false;
    }
  }

  private RunManagerImpl getRunManager() {
    return RunManagerImpl.getInstanceImpl(myProject);
  }

  public String getHelpTopic() {
    final ConfigurationType type = getSelectedConfigurationType();
    if (type != null) {
      return "reference.dialogs.rundebug." + type.getId();
    }
    return "reference.dialogs.rundebug";
  }

  private void clickDefaultButton() {
    if (myRunDialog != null) myRunDialog.clickDefaultButton();
  }

  private DefaultMutableTreeNode getSelectedConfigurationTypeNode() {
    final DefaultMutableTreeNode node = (DefaultMutableTreeNode)myTree.getSelectionPath().getLastPathComponent();
    final Object userObject = node.getUserObject();
    if (userObject instanceof ConfigurationType) {
      return node;
    }
    else {
      return (DefaultMutableTreeNode)node.getParent();
    }
  }

  private static String createUniqueName(DefaultMutableTreeNode typeNode) {
    String str = ExecutionBundle.message("run.configuration.unnamed.name.prefix");
    final ArrayList<String> currentNames = new ArrayList<String>();
    for (int i = 0; i < typeNode.getChildCount(); i++) {
      final Object userObject = ((DefaultMutableTreeNode)typeNode.getChildAt(i)).getUserObject();
      if (userObject instanceof SingleConfigurationConfigurable) {
        currentNames.add(((SingleConfigurationConfigurable)userObject).getNameText());
      }
      else if (userObject instanceof RunnerAndConfigurationSettingsImpl) {
        currentNames.add(((RunnerAndConfigurationSettings)userObject).getName());
      }
    }
    if (!currentNames.contains(str)) return str;
    int i = 1;
    while (true) {
      if (!currentNames.contains(str + i)) return str + i;
      i++;
    }
  }

  private SingleConfigurationConfigurable<RunConfiguration> createNewConfiguration(final RunnerAndConfigurationSettings settings, final DefaultMutableTreeNode node) {
    final SingleConfigurationConfigurable<RunConfiguration> configurationConfigurable =
        SingleConfigurationConfigurable.editSettings(settings);
    installUpdateListeners(configurationConfigurable);
    DefaultMutableTreeNode nodeToAdd = new DefaultMutableTreeNode(configurationConfigurable);
    node.add(nodeToAdd);
    ((DefaultTreeModel)myTree.getModel()).reload(node);
    TreeUtil.selectNode(myTree, nodeToAdd);
    return configurationConfigurable;
  }

  private void createNewConfiguration(final ConfigurationFactory factory) {
    DefaultMutableTreeNode node = null;
    for (int i = 0; i < myRoot.getChildCount(); i++) {
      final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)myRoot.getChildAt(i);
      if (treeNode.getUserObject() == factory.getType()) {
        node = treeNode;
        break;
      }
    }
    if (node == null) {
      node = new DefaultMutableTreeNode(factory.getType());
      myRoot.add(node);
      sortTree(myRoot);
      ((DefaultTreeModel)myTree.getModel()).reload();
    }
    final RunnerAndConfigurationSettings settings = getRunManager().createConfiguration(createUniqueName(node), factory);
    createNewConfiguration(settings, node);
  }

  private class MyToolbarAddAction extends AnAction {
    public MyToolbarAddAction() {
      super(ExecutionBundle.message("add.new.run.configuration.acrtion.name"),
            ExecutionBundle.message("add.new.run.configuration.acrtion.name"), ADD_ICON);
      registerCustomShortcutSet(CommonShortcuts.INSERT, myTree);
    }

    public void actionPerformed(AnActionEvent e) {
      final JBPopupFactory popupFactory = JBPopupFactory.getInstance();
      final ConfigurationType[] configurationTypes = getRunManager().getConfigurationFactories(false);
      Arrays.sort(configurationTypes, new Comparator<ConfigurationType>() {
        public int compare(final ConfigurationType type1, final ConfigurationType type2) {
          return type1.getDisplayName().compareTo(type2.getDisplayName());
        }
      });
      final ListPopup popup =
          popupFactory.createListPopup(new BaseListPopupStep<ConfigurationType>(
              ExecutionBundle.message("add.new.run.configuration.acrtion.name"), configurationTypes) {

            @NotNull
            public String getTextFor(final ConfigurationType type) {
              return type.getDisplayName();
            }

            @Override
            public boolean isSpeedSearchEnabled() {
              return true;
            }

            @Override
            public boolean canBeHidden(ConfigurationType value) {
              return true;
            }

            public Icon getIconFor(final ConfigurationType type) {
              return type.getIcon();
            }

            public PopupStep onChosen(final ConfigurationType type, final boolean finalChoice) {
              if (hasSubstep(type)) {
                return getSupStep(type);
              }
              final ConfigurationFactory[] factories = type.getConfigurationFactories();
              if (factories.length > 0) {
                createNewConfiguration(factories[0]);
              }
              return FINAL_CHOICE;
            }

            public int getDefaultOptionIndex() {
              final TreePath selectionPath = myTree.getSelectionPath();
              if (selectionPath != null) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
                final Object userObject = node.getUserObject();
                ConfigurationType type = null;
                if (userObject instanceof SingleConfigurationConfigurable) {
                  final SingleConfigurationConfigurable configurable = (SingleConfigurationConfigurable)userObject;
                  type = configurable.getConfiguration().getType();
                }
                else if (userObject instanceof ConfigurationType) {
                  type = (ConfigurationType)userObject;
                }
                return ArrayUtil.find(configurationTypes, type);
              }
              return super.getDefaultOptionIndex();
            }

            private ListPopupStep getSupStep(final ConfigurationType type) {
              final ConfigurationFactory[] factories = type.getConfigurationFactories();
              Arrays.sort(factories, new Comparator<ConfigurationFactory>() {
                public int compare(final ConfigurationFactory factory1, final ConfigurationFactory factory2) {
                  return factory1.getName().compareTo(factory2.getName());
                }
              });
              return new BaseListPopupStep<ConfigurationFactory>(
                  ExecutionBundle.message("add.new.run.configuration.action.name", type.getDisplayName()), factories) {

                @NotNull
                public String getTextFor(final ConfigurationFactory value) {
                  return value.getName();
                }

                public Icon getIconFor(final ConfigurationFactory factory) {
                  return factory.getIcon();
                }

                public PopupStep onChosen(final ConfigurationFactory factory, final boolean finalChoice) {
                  createNewConfiguration(factory);
                  return FINAL_CHOICE;
                }

              };
            }

            public boolean hasSubstep(final ConfigurationType type) {
              return type.getConfigurationFactories().length > 1;
            }

          });      
      //new TreeSpeedSearch(myTree);
      popup.showUnderneathOf(myToolbarComponent);
    }
  }


  private class MyRemoveAction extends AnAction {

    public MyRemoveAction() {
      super(ExecutionBundle.message("remove.run.configuration.action.name"),
            ExecutionBundle.message("remove.run.configuration.action.name"), REMOVE_ICON);
      registerCustomShortcutSet(CommonShortcuts.DELETE, myTree);
    }

    public void actionPerformed(AnActionEvent e) {
      TreePath[] selections = myTree.getSelectionPaths();
      myTree.clearSelection();

      int nodeIndexToSelect = -1;
      DefaultMutableTreeNode parentToSelect = null;

      Set<DefaultMutableTreeNode> changedParents = new HashSet<DefaultMutableTreeNode>();
      boolean wasRootChanged = false;

      for (TreePath each : selections) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)each.getLastPathComponent();
        if (node.getUserObject() instanceof ConfigurationType) continue;

        if (node.getUserObject() instanceof SingleConfigurationConfigurable) {
          ((SingleConfigurationConfigurable)node.getUserObject()).disposeUIResources();
        }

        DefaultMutableTreeNode parent = (DefaultMutableTreeNode)node.getParent();
        nodeIndexToSelect = parent.getIndex(node);
        parentToSelect = parent;
        parent.remove(node);
        changedParents.add(parent);

        if (parent.getChildCount() == 0) {
          changedParents.remove(parent);
          wasRootChanged = true;

          nodeIndexToSelect = myRoot.getIndex(parent);
          parentToSelect = myRoot;
          myRoot.remove(parent);
        }
      }

      if (wasRootChanged) {
        ((DefaultTreeModel)myTree.getModel()).reload();
      } else {
        for (DefaultMutableTreeNode each : changedParents) {
          ((DefaultTreeModel)myTree.getModel()).reload(each);
          myTree.expandPath(new TreePath(each));
        }
      }

      mySelectedConfigurable = null;
      if (myRoot.getChildCount() == 0) {
        drawPressAddButtonMessage(null);
      }
      else {
        TreeNode nodeToSelect = nodeIndexToSelect < parentToSelect.getChildCount()
                                ? parentToSelect.getChildAt(nodeIndexToSelect)
                                : parentToSelect.getChildAt(nodeIndexToSelect - 1);
        TreeUtil.selectInTree((DefaultMutableTreeNode)nodeToSelect, true, myTree);
      }
    }


    public void update(AnActionEvent e) {
      boolean enabled = false;
      TreePath[] selections = myTree.getSelectionPaths();
      if (selections != null) {
        for (TreePath each : selections) {
          DefaultMutableTreeNode node = (DefaultMutableTreeNode)each.getLastPathComponent();
          if (!(node.getUserObject() instanceof ConfigurationType)) {
            enabled = true;
            break;
          }
        }
      }
      e.getPresentation().setEnabled(enabled);
    }
  }

  private class MyCopyAction extends AnAction {
    public MyCopyAction() {
      super(ExecutionBundle.message("copy.configuration.action.name"),
            ExecutionBundle.message("copy.configuration.action.name"),
            COPY_ICON);

      final AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_DUPLICATE);
      registerCustomShortcutSet(action.getShortcutSet(), myTree);
    }


    public void actionPerformed(AnActionEvent e) {
      final SingleConfigurationConfigurable<RunConfiguration> configuration = getSelectedConfiguration();
      LOG.assertTrue(configuration != null);
      try {
        final DefaultMutableTreeNode typeNode = getSelectedConfigurationTypeNode();
        final RunnerAndConfigurationSettings settings = configuration.getSnapshot();
        final String copyName = createUniqueName(typeNode);
        settings.setName(copyName);
        final SingleConfigurationConfigurable<RunConfiguration> configurable = createNewConfiguration(settings, typeNode);
        IdeFocusManager.getInstance(myProject).requestFocus(configurable.getNameTextField(), true);
        configurable.getNameTextField().setSelectionStart(0);
        configurable.getNameTextField().setSelectionEnd(copyName.length());
      }
      catch (ConfigurationException e1) {
        Messages.showErrorDialog(myToolbarComponent, e1.getMessage(), e1.getTitle());
      }
    }

    public void update(AnActionEvent e) {
      final SingleConfigurationConfigurable<RunConfiguration> configuration = getSelectedConfiguration();
      e.getPresentation().setEnabled(configuration != null && !(configuration.getConfiguration() instanceof UnknownRunConfiguration));
    }
  }

  private class MySaveAction extends AnAction {

    public MySaveAction() {
      super(ExecutionBundle.message("action.name.save.configuration"), null, SAVE_ICON);
    }

    public void actionPerformed(final AnActionEvent e) {
      final SingleConfigurationConfigurable<RunConfiguration> configurationConfigurable = getSelectedConfiguration();
      LOG.assertTrue(configurationConfigurable != null);
      try {
        configurationConfigurable.apply();
      }
      catch (ConfigurationException e1) {
        //do nothing
      }
      final RunnerAndConfigurationSettings originalConfiguration = configurationConfigurable.getSettings();
      if (getRunManager().isTemporary(originalConfiguration)) {
        getRunManager().makeStable(originalConfiguration.getConfiguration());
      }
      myTree.repaint();
    }

    public void update(final AnActionEvent e) {
      final SingleConfigurationConfigurable<RunConfiguration> configuration = getSelectedConfiguration();
      final Presentation presentation = e.getPresentation();
      final boolean enabled = configuration != null && getRunManager().isTemporary(configuration.getSettings());
      presentation.setEnabled(enabled);
      presentation.setVisible(enabled);
    }
  }

  private class MyMoveAction extends AnAction {
    private final int myDirection;

    protected MyMoveAction(String text, String description, Icon icon, int direction) {
      super(text, description, icon);
      myDirection = direction;
    }

    public void actionPerformed(final AnActionEvent e) {
      TreeUtil.moveSelectedRow(myTree, myDirection);
    }

    public void update(final AnActionEvent e) {
      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(false);
      final TreePath selectionPath = myTree.getSelectionPath();
      if (selectionPath != null) {
        final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
        if (treeNode.getUserObject() instanceof SingleConfigurationConfigurable) {
          if (myDirection < 0) {
            presentation.setEnabled(treeNode.getPreviousSibling() != null);
          }
          else {
            presentation.setEnabled(treeNode.getNextSibling() != null);
          }
        }
      }
    }
  }

  private static class RunConfigurationBean {
    private final RunnerAndConfigurationSettings mySettings;
    private final boolean myShared;
    private final Map<Key<? extends BeforeRunTask>, BeforeRunTask> myStepsBeforeLaunch;
    private final SingleConfigurationConfigurable myConfigurable;

    public RunConfigurationBean(final RunnerAndConfigurationSettings settings,
                                final boolean shared,
                                final Map<Key<? extends BeforeRunTask>, BeforeRunTask> stepsBeforeLaunch) {
      mySettings = settings;
      myShared = shared;
      myStepsBeforeLaunch = Collections.unmodifiableMap(stepsBeforeLaunch);
      myConfigurable = null;
    }

    public RunConfigurationBean(final SingleConfigurationConfigurable configurable) {
      myConfigurable = configurable;
      mySettings = (RunnerAndConfigurationSettings)myConfigurable.getSettings();
      final ConfigurationSettingsEditorWrapper editorWrapper = (ConfigurationSettingsEditorWrapper)myConfigurable.getEditor();
      myShared = editorWrapper.isStoreProjectConfiguration();
      myStepsBeforeLaunch = editorWrapper.getStepsBeforeLaunch();
    }

    public RunnerAndConfigurationSettings getSettings() {
      return mySettings;
    }

    public boolean isShared() {
      return myShared;
    }

    public Map<Key<? extends BeforeRunTask>, BeforeRunTask> getStepsBeforeLaunch() {
      return myStepsBeforeLaunch;
    }

    public SingleConfigurationConfigurable getConfigurable() {
      return myConfigurable;
    }
  }
}
