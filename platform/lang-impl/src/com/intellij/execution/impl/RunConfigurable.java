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
import com.intellij.execution.configuration.ConfigurationFactoryEx;
import com.intellij.execution.configurations.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.config.StorageAccessors;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class RunConfigurable extends BaseConfigurable {
  private static final Icon ICON = IconLoader.getIcon("/general/configurableRunDebug.png");
  @NonNls private static final String GENERAL_ADD_ICON_PATH = "/general/add.png";
  private static final Icon ADD_ICON = IconLoader.getIcon(GENERAL_ADD_ICON_PATH);
  private static final Icon REMOVE_ICON = IconLoader.getIcon("/general/remove.png");
  private static final Icon SAVE_ICON = IconLoader.getIcon("/runConfigurations/saveTempConfig.png");
  @NonNls private static final String EDIT_DEFAULTS_ICON_PATH = "/general/ideOptions.png";
  private static final Icon EDIT_DEFAULTS_ICON = IconLoader.getIcon(EDIT_DEFAULTS_ICON_PATH);
  @NonNls private static final String DIVIDER_PROPORTION = "dividerProportion";

  private volatile boolean isDisposed = false;

  private final Project myProject;
  private RunDialogBase myRunDialog;
  @NonNls private final DefaultMutableTreeNode myRoot = new DefaultMutableTreeNode("Root");
  private final Tree myTree = new Tree(myRoot);
  private final JPanel myRightPanel = new JPanel(new BorderLayout());
  private JComponent myToolbarComponent;
  private final Splitter mySplitter = new Splitter(false);
  private JPanel myWholePanel;
  private final StorageAccessors myProperties = StorageAccessors.createGlobal("RunConfigurable");
  private Configurable mySelectedConfigurable = null;
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.impl.RunConfigurable");
  private final JTextField myRecentsLimit = new JTextField("5");
  private Map<ConfigurationFactory, Configurable> myStoredComponents = new HashMap<ConfigurationFactory, Configurable>();

  public RunConfigurable(final Project project) {
    this(project, null);
  }

  public RunConfigurable(final Project project, final RunDialogBase runDialog) {
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
    TreeUtil.installActions(myTree);
    new TreeSpeedSearch(myTree, new Convertor<TreePath, String>() {
      @Override
      public String convert(TreePath o) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)o.getLastPathComponent();
        final Object userObject = node.getUserObject();
        if (userObject instanceof RunnerAndConfigurationSettingsImpl) {
          return ((RunnerAndConfigurationSettingsImpl)userObject).getName();
        }
        else if (userObject instanceof SingleConfigurationConfigurable) {
          return ((SingleConfigurationConfigurable)userObject).getNameText();
        }
        else {
          if (userObject instanceof ConfigurationType) {
            return ((ConfigurationType)userObject).getDisplayName();
          }
          else if (userObject instanceof String) {
            return (String)userObject;
          }
        }
        return o.toString();
      }
    });
    PopupHandler.installFollowingSelectionTreePopup(myTree, createActionsGroup(), ActionPlaces.UNKNOWN, ActionManager.getInstance());
    myTree.setCellRenderer(new ColoredTreeCellRenderer() {
      public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row,
                                        boolean hasFocus) {
        if (value instanceof DefaultMutableTreeNode) {
          final DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
          final DefaultMutableTreeNode parent = (DefaultMutableTreeNode)node.getParent();
          final Object userObject = node.getUserObject();
          if (userObject instanceof ConfigurationType) {
            final ConfigurationType configurationType = (ConfigurationType)userObject;
            append(configurationType.getDisplayName(), parent.isRoot() ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
            setIcon(configurationType.getIcon());
          }
          else if (userObject instanceof String) {
            append((String) userObject, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
            setIcon(EDIT_DEFAULTS_ICON);
          }
          else if (userObject instanceof ConfigurationFactory) {
            append(((ConfigurationFactory)userObject).getName());
            setIcon(((ConfigurationFactory)userObject).getIcon());
          }
          else {
            final RunManager runManager = getRunManager();
            RunConfiguration configuration = null;
            String name = null;
            if (userObject instanceof SingleConfigurationConfigurable) {
              final SingleConfigurationConfigurable<?> settings = (SingleConfigurationConfigurable)userObject;
              RunnerAndConfigurationSettings snapshot;
              try {
                snapshot = settings.getSnapshot();
              }
              catch (ConfigurationException e) {
                snapshot = settings.getSettings();
              }
              configuration = settings.getConfiguration();
              name = settings.getNameText();
              setIcon(ProgramRunnerUtil.getConfigurationIcon(snapshot, !settings.isValid(), runManager.isTemporary(configuration)));
            }
            else if (userObject instanceof RunnerAndConfigurationSettingsImpl) {
              RunnerAndConfigurationSettings settings = (RunnerAndConfigurationSettings)userObject;
              setIcon(RunManagerEx.getInstanceEx(myProject).getConfigurationIcon(settings));
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

    // add defaults
    final DefaultMutableTreeNode defaults = new DefaultMutableTreeNode("Defaults");
    final ConfigurationType[] configurationTypes = RunManagerImpl.getInstanceImpl(myProject).getConfigurationFactories();
    for (final ConfigurationType type : configurationTypes) {
      if (!(type instanceof UnknownConfigurationType)) {
        ConfigurationFactory[] configurationFactories = type.getConfigurationFactories();
        DefaultMutableTreeNode typeNode = new DefaultMutableTreeNode(type);
        defaults.add(typeNode);
        if (configurationFactories.length != 1) {
          for (ConfigurationFactory factory : configurationFactories) {
            typeNode.add(new DefaultMutableTreeNode(factory));
          }
        }
      }
    }
    if (defaults.getChildCount() > 0) myRoot.add(defaults);

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
          else {
            if (userObject instanceof ConfigurationType || userObject instanceof String) {
              final DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
              if (parent.isRoot()) {
                drawPressAddButtonMessage(userObject instanceof String ? null : (ConfigurationType)userObject);
              } else {
                final ConfigurationType type = (ConfigurationType)userObject;
                ConfigurationFactory[] factories = type.getConfigurationFactories();
                if (factories.length == 1) {
                  final ConfigurationFactory factory = factories[0];
                  showTemplateConfigurabel(factory);
                }
                else {
                  drawPressAddButtonMessage((ConfigurationType)userObject);
                }
              }
            }
            else if (userObject instanceof ConfigurationFactory) {
              showTemplateConfigurabel((ConfigurationFactory)userObject);
            }
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
        if (isDisposed) return;

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

  private void showTemplateConfigurabel(ConfigurationFactory factory) {
    Configurable configurable = myStoredComponents.get(factory);
    if (configurable == null){
      configurable = new TemplateConfigurable(RunManagerImpl.getInstanceImpl(myProject).getConfigurationTemplate(factory));
      myStoredComponents.put(factory, configurable);
      configurable.reset();
    }
    updateRightPanel(configurable);
  }

  public void setRunDialog(final RunDialogBase runDialog) {
    myRunDialog = runDialog;
  }

  private void updateRightPanel(final Configurable configurable) {
    myRightPanel.removeAll();
    mySelectedConfigurable = configurable;

    final JBScrollPane scrollPane = new JBScrollPane(configurable.createComponent());
    scrollPane.setBorder(null);
    myRightPanel.add(scrollPane, BorderLayout.CENTER);

    if (configurable instanceof SingleConfigurationConfigurable) {
      RunManagerEx.getInstanceEx(myProject)
        .invalidateConfigurationIcon((RunnerAndConfigurationSettings)((SingleConfigurationConfigurable)configurable).getSettings());
    }

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
        else if (userObject1 instanceof String && userObject2 instanceof ConfigurationType) {
          return 1;
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
    final boolean[] changed = new boolean[]{false};
    info.getEditor().addSettingsEditorListener(new SettingsEditorListener<RunnerAndConfigurationSettings>() {
      public void stateChanged(final SettingsEditor<RunnerAndConfigurationSettings> editor) {
        update();
        final RunConfiguration configuration = info.getConfiguration();
        if (configuration instanceof LocatableConfiguration) {
          final LocatableConfiguration runtimeConfiguration = (LocatableConfiguration)configuration;
          if (runtimeConfiguration.isGeneratedName() && !changed[0]) {
            try {
              final LocatableConfiguration snapshot = (LocatableConfiguration)editor.getSnapshot().getConfiguration();
              final String generatedName = snapshot instanceof RuntimeConfiguration? ((RuntimeConfiguration)snapshot).getGeneratedName() : snapshot.suggestedName();
              if (generatedName != null && generatedName.length() > 0) {
                info.setNameText(generatedName);
                changed[0] = false;
              }
            }
            catch (ConfigurationException ignore) {
            }
          }
        }
        setupDialogBounds();
      }
    });

    info.addNameListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        changed[0] = true;
        update();
      }
    });
  }

  private void drawPressAddButtonMessage(final ConfigurationType configurationType) {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    panel.setBorder(new EmptyBorder(30, 0, 0, 0));
    panel.add(new JLabel("Press the"));

    final JEditorPane browser = new JEditorPane();
    browser.setEditable(false);
    browser.setEditorKit(new HTMLEditorKit());
    browser.setBackground(myRightPanel.getBackground());
    final URL addUrl = getClass().getResource(GENERAL_ADD_ICON_PATH);
    final String configurationTypeDescription = configurationType != null
                                                ? configurationType.getConfigurationTypeDescription()
                                                : ExecutionBundle.message("run.configuration.default.type.description");
    browser.setText(ExecutionBundle.message("empty.run.configuration.panel.text.label2", addUrl, configurationType != null ? configurationType.getId() : ""));
    browser.addHyperlinkListener(new HyperlinkListener() {
      public void hyperlinkUpdate(final HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          final String description = e.getDescription();
          final String addPrefix = "add";
          if (description.startsWith(addPrefix)) {
            final String typeId = description.substring(addPrefix.length());
            if (!typeId.isEmpty()) {
              for (ConfigurationType type : Extensions.getExtensions(ConfigurationType.CONFIGURATION_TYPE_EP)) {
                if (Comparing.strEqual(type.getId(), typeId)) {
                  final ConfigurationFactory[] factories = type.getConfigurationFactories();
                  if (factories.length > 0) {
                    createNewConfiguration(factories[0]);
                  }
                  return;
                }
              }
            }
            new MyToolbarAddAction().actionPerformed(null);
          }
        }
      }
    });
    panel.add(browser);

    panel.add(new JLabel(ExecutionBundle.message("empty.run.configuration.panel.text.label3", configurationTypeDescription)));
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(panel);
    scrollPane.setBorder(null);

    myRightPanel.removeAll();
    myRightPanel.add(scrollPane, BorderLayout.CENTER);
    if (configurationType == null) {
      myRightPanel.add(createRecentLimitPanel(), BorderLayout.SOUTH);
    }
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
    return leftPanel;
  }

  private JPanel createRecentLimitPanel() {
    final JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEADING));

//    box.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
    bottomPanel.add(new JLabel("<html>Temporary configurations limit:</html>"));
    Dimension size = new Dimension(25, myRecentsLimit.getPreferredSize().height);
    myRecentsLimit.setPreferredSize(size);
    myRecentsLimit.setMaximumSize(size);
    myRecentsLimit.setMinimumSize(size);
    bottomPanel.add(myRecentsLimit);
    myRecentsLimit.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        setModified(true);
      }
    });
    return bottomPanel;
  }

  private DefaultActionGroup createActionsGroup() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new MyToolbarAddAction());
    group.add(new MyRemoveAction());
    group.add(new MyCopyAction());
    group.add(new MySaveAction());
    group.add(new AnAction(ExecutionBundle.message("run.configuration.edit.default.configuration.settings.button"),
                           "Edit default settings", EDIT_DEFAULTS_ICON) {
      public void actionPerformed(final AnActionEvent e) {
        TreeNode defaults = TreeUtil.findNodeWithObject("Defaults", myTree.getModel(), myRoot);
        if (defaults != null) {
          final ConfigurationType configurationType = getSelectedConfigurationType();
          if (configurationType != null) {
            defaults = TreeUtil.findNodeWithObject(configurationType, myTree.getModel(), defaults);
          }
        }
        final DefaultMutableTreeNode defaultsNode = (DefaultMutableTreeNode)defaults;
        final TreePath path = TreeUtil.getPath(myRoot, defaultsNode);
        myTree.expandPath(path);
        TreeUtil.selectInTree(defaultsNode, true, myTree);
        myTree.scrollPathToVisible(path);
      }

      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(TreeUtil.findNodeWithObject("Defaults", myTree.getModel(), myRoot) != null);
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
    mySplitter.setFirstComponent(createLeftPanel());
    mySplitter.setSecondComponent(myRightPanel);
    myWholePanel.add(mySplitter, BorderLayout.CENTER);

    updateDialog();

    Dimension d = myWholePanel.getPreferredSize();
    d.width = Math.max(d.width, 800);
    d.height = Math.max(d.height, 600);
    myWholePanel.setPreferredSize(d);

    mySplitter.setProportion(myProperties.getFloat(DIVIDER_PROPORTION, 0.3f));

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

  public Configurable getSelectedConfigurable() {
    return mySelectedConfigurable;
  }

  public void apply() throws ConfigurationException {
    updateActiveConfigurationFromSelected();

    final RunManagerImpl manager = getRunManager();
    final ConfigurationType[] configurationTypes = manager.getConfigurationFactories();
    for (ConfigurationType configurationType : configurationTypes) {
      applyByType(configurationType);
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

    for (Configurable configurable : myStoredComponents.values()) {
      if (configurable.isModified()){
        configurable.apply();
      }
    }

    setModified(false);
  }

  protected void updateActiveConfigurationFromSelected() {
    if (mySelectedConfigurable != null && mySelectedConfigurable instanceof SingleConfigurationConfigurable) {
      RunnerAndConfigurationSettings settings =
        (RunnerAndConfigurationSettings)((SingleConfigurationConfigurable)mySelectedConfigurable).getSettings();

      getRunManager().setSelectedConfiguration(settings);
    }
  }

  private void applyByType(ConfigurationType type) throws ConfigurationException {
    DefaultMutableTreeNode typeNode = getConfigurationTypeNode(type);
    final RunManagerImpl manager = getRunManager();
    final ArrayList<RunConfigurationBean> stableConfigurations = new ArrayList<RunConfigurationBean>();
    if (typeNode != null) {
      final Set<String> names = new HashSet<String>();
      for (int i = 0; i < typeNode.getChildCount(); i++) {
        final DefaultMutableTreeNode node = (DefaultMutableTreeNode)typeNode.getChildAt(i);
        final Object userObject = node.getUserObject();
        RunConfigurationBean configurationBean = null;
        if (userObject instanceof SingleConfigurationConfigurable) {
          final SingleConfigurationConfigurable configurable = (SingleConfigurationConfigurable)userObject;
          final RunnerAndConfigurationSettings settings = (RunnerAndConfigurationSettings)configurable.getSettings();
          if (manager.isTemporary(settings)) {
            applyConfiguration(typeNode, configurable);
          }
          configurationBean = new RunConfigurationBean(configurable);
        }
        else if (userObject instanceof RunnerAndConfigurationSettingsImpl) {
          RunnerAndConfigurationSettings settings = (RunnerAndConfigurationSettings)userObject;
          configurationBean = new RunConfigurationBean(settings,
                                                       manager.isConfigurationShared(settings),
                                                       manager.getBeforeRunTasks(settings.getConfiguration()));

        }
        if (configurationBean != null) {
          final SingleConfigurationConfigurable configurable = configurationBean.getConfigurable();
          final String nameText = configurable != null ? configurable.getNameText() : configurationBean.getSettings().getName();
          if (!names.add(nameText)) {
            TreeUtil.selectNode(myTree, node);
            throw new ConfigurationException("Configuration with name \'" + nameText + "\' already exists");
          }
          stableConfigurations.add(configurationBean);
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
    Set<RunnerAndConfigurationSettings> toDeleteSettings = new THashSet<RunnerAndConfigurationSettings>();
    for (RunConfiguration each : manager.getConfigurations(type)) {
      ContainerUtil.addIfNotNull(toDeleteSettings, manager.getSettings(each));
    }

    for (RunConfigurationBean each : stableConfigurations) {
      toDeleteSettings.remove(each.getSettings());
      manager.addConfiguration(each.getSettings(), each.isShared(), each.getStepsBeforeLaunch());
    }

    RunnerAndConfigurationSettings selected = manager.getSelectedConfiguration();
    for (RunnerAndConfigurationSettings each : toDeleteSettings) {
      manager.removeConfiguration(each);
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

  private void applyConfiguration(DefaultMutableTreeNode typeNode, SingleConfigurationConfigurable<?> configurable) throws ConfigurationException {
    try {
      if (configurable != null) {
        configurable.apply();
        RunManagerImpl.getInstanceImpl(myProject).fireRunConfigurationChanged(configurable.getSettings());
      }
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
    final RunConfiguration[] allConfigurations = runManager.getAllConfigurations();
    final Set<RunConfiguration> currentConfigurations = new HashSet<RunConfiguration>();
    for (int i = 0; i < myRoot.getChildCount(); i++) {
      DefaultMutableTreeNode typeNode = (DefaultMutableTreeNode)myRoot.getChildAt(i);
      final Object object = typeNode.getUserObject();
      if (object instanceof ConfigurationType) {
        final RunnerAndConfigurationSettings[] configurationSettings =
          runManager.getConfigurationSettings((ConfigurationType)object);
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
    }
    for (RunConfiguration configuration : allConfigurations) {
      if (!currentConfigurations.contains(configuration)) return true;
    }

    for (Configurable configurable : myStoredComponents.values()) {
      if (configurable.isModified()) return true;
    }

    return false;
  }

  public void disposeUIResources() {
    isDisposed = true;
    for (Configurable configurable : myStoredComponents.values()) {
      configurable.disposeUIResources();
    }
    myStoredComponents.clear();

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
    myProperties.setFloat(DIVIDER_PROPORTION, mySplitter.getProportion());
    mySplitter.dispose();
  }

  private void updateDialog() {
    final Executor executor = myRunDialog != null ? myRunDialog.getExecutor() : null;
    if (executor == null) return;
    final StringBuilder buffer = new StringBuilder();
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

  @Nullable
  private DefaultMutableTreeNode getSelectedConfigurationTypeNode() {
    final DefaultMutableTreeNode node = (DefaultMutableTreeNode)myTree.getSelectionPath().getLastPathComponent();
    final Object userObject = node.getUserObject();
    if (userObject instanceof ConfigurationType) {
      return node;
    }
    else {
      final DefaultMutableTreeNode parent = (DefaultMutableTreeNode)node.getParent();
      if (parent != null && parent.getUserObject() instanceof ConfigurationType) {
        return parent;
      }
      return null;
    }
  }

  private static String createUniqueName(DefaultMutableTreeNode typeNode, @Nullable String baseName) {
    String str = (baseName == null) ? ExecutionBundle.message("run.configuration.unnamed.name.prefix") : baseName;
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

    final Matcher matcher = Pattern.compile("(.*?)\\s*\\(\\d+\\)").matcher(str);
    final String originalName = (matcher.matches()) ? matcher.group(1) : str;
    int i = 1;
    while (true) {
      final String newName = String.format("%s (%d)", originalName, i);
      if (!currentNames.contains(newName)) return newName;
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
      //final DefaultMutableTreeNode lastChild = (DefaultMutableTreeNode)myRoot.getLastChild();
      //if (lastChild.getUserObject() instanceof String) {
      //  int index = myRoot.getIndex(lastChild);
      //  myRoot.insert(node, Math.max(index - 1, 0));
      //} else {
        myRoot.add(node);
      //}

      sortTree(myRoot);
      ((DefaultTreeModel)myTree.getModel()).reload();
    }
    final RunnerAndConfigurationSettings settings = getRunManager().createConfiguration(createUniqueName(node, null), factory);
    if (factory instanceof ConfigurationFactoryEx) {
      ((ConfigurationFactoryEx)factory).onNewConfigurationCreated(settings.getConfiguration());
    }
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
          nodeIndexToSelect = Math.max(0, nodeIndexToSelect - 1);
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
          final Object userObject = node.getUserObject();
          if (!(userObject instanceof ConfigurationType) && !(userObject instanceof String)) {
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
            PlatformIcons.COPY_ICON);

      final AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_DUPLICATE);
      registerCustomShortcutSet(action.getShortcutSet(), myTree);
    }


    public void actionPerformed(AnActionEvent e) {
      final SingleConfigurationConfigurable<RunConfiguration> configuration = getSelectedConfiguration();
      LOG.assertTrue(configuration != null);
      try {
        final DefaultMutableTreeNode typeNode = getSelectedConfigurationTypeNode();
        final RunnerAndConfigurationSettings settings = configuration.getSnapshot();
        final String copyName = createUniqueName(typeNode, configuration.getNameText());
        settings.setName(copyName);
        final ConfigurationFactory factory = settings.getFactory();
        if (factory instanceof ConfigurationFactoryEx) {
          ((ConfigurationFactoryEx)factory).onConfigurationCopied(settings.getConfiguration());
        }
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
        if (!(treeNode.getUserObject() instanceof ConfigurationType)) {
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

  public interface RunDialogBase {
    void setOKActionEnabled(boolean isEnabled);

    @Nullable
    Executor getExecutor();

    void setTitle(String title);

    void clickDefaultButton();
  }
}
