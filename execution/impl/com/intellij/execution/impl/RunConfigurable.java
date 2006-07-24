package com.intellij.execution.impl;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
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
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.config.StorageAccessors;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
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
  private static final Icon EDIT_DEFUALTS_ICON = IconLoader.getIcon(EDIT_DEFAULTS_ICON_PATH);
  @NonNls private static final String DIVIDER_PROPORTION = "dividerProportion";

  private final Project myProject;
  private final RunDialog myRunDialog;
  private JCheckBox myCbShowSettingsBeforeRunning;
  @NonNls private DefaultMutableTreeNode myRoot = new DefaultMutableTreeNode("Root");
  private Tree myTree = new Tree(myRoot);
  private JPanel myRightPanel = new JPanel(new BorderLayout());
  private JComponent myToolbarComponent;
  private Splitter myPanel = new Splitter();
  private JPanel myWholePanel;
  private StorageAccessors myConfig;
  private JCheckBox myCbCompileBeforeRunning;
  private SingleConfigurationConfigurable<RunConfiguration> mySelectedConfigurable = null;
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.impl.RunConfigurable");

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

  private void initTree(){
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    UIUtil.setLineStyleAngled(myTree);
    TreeToolTipHandler.install(myTree);
    TreeUtil.installActions(myTree);
    PopupHandler.installFollowingSelectionTreePopup(myTree, createActionsGroup(), ActionPlaces.UNKNOWN, ActionManager.getInstance());
    myTree.setCellRenderer(new ColoredTreeCellRenderer(){
      public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (value instanceof DefaultMutableTreeNode){
          final DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
          final Object userObject = node.getUserObject();
          if (userObject instanceof ConfigurationType){
            final ConfigurationType configurationType = (ConfigurationType)userObject;
            append(configurationType.getDisplayName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
            setIcon(configurationType.getIcon());
          } else if (userObject instanceof SingleConfigurationConfigurable){
            final SingleConfigurationConfigurable settings = (SingleConfigurationConfigurable)userObject;
            final RunManager runManager = getRunManager();
            final RunConfiguration configuration = settings.getConfiguration();
            append(settings.getNameText(), runManager.isTemporary(configuration) ? SimpleTextAttributes.GRAY_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
            setIcon(ExecutionUtil.getConfigurationIcon(getProject(), configuration, !settings.isValid()));
          }
        }
      }
    });
    final RunManagerEx manager = getRunManager();
    final ConfigurationType[] factories = getRunManager().getConfigurationFactories();
    for (ConfigurationType type : factories) {
      final RunnerAndConfigurationSettingsImpl[] configurations = manager.getConfigurationSettings(type);
      if (configurations != null && configurations.length > 0){
        final DefaultMutableTreeNode typeNode = new DefaultMutableTreeNode(type);
        myRoot.add(typeNode);
        for (RunnerAndConfigurationSettingsImpl configuration : configurations) {
          final SingleConfigurationConfigurable<RunConfiguration> configurationConfigurable =
            SingleConfigurationConfigurable.editSettings(configuration);
          installUpdateListeners(configurationConfigurable);
          typeNode.add(new DefaultMutableTreeNode(configurationConfigurable));
        }
      }
    }

    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        final TreePath selectionPath = myTree.getSelectionPath();
        if (selectionPath != null){
          DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
          final Object userObject = node.getUserObject();
          if (userObject instanceof SingleConfigurationConfigurable){
            myRightPanel.removeAll();
            mySelectedConfigurable = (SingleConfigurationConfigurable<RunConfiguration>)userObject;
            myRightPanel.add(mySelectedConfigurable.createComponent(), BorderLayout.CENTER);
            updateCompileMethodComboStatus(mySelectedConfigurable);
            setupDialogBounds();
          } else if (userObject instanceof ConfigurationType){
            drawPressAddButtonMessage(((ConfigurationType)userObject));
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
          while (enumeration.hasMoreElements()){
            final DefaultMutableTreeNode node = (DefaultMutableTreeNode)enumeration.nextElement();
            final Object userObject = node.getUserObject();
            if (userObject instanceof SingleConfigurationConfigurable) {
              final SingleConfigurationConfigurable<RunConfiguration> configurationConfigurable = ((SingleConfigurationConfigurable<RunConfiguration>)userObject);
              if (configurationConfigurable.getConfiguration().getType() == settings.getType() &&
                  Comparing.strEqual(configurationConfigurable.getConfiguration().getName(),
                                     settings.getName())){
                mySelectedConfigurable = configurationConfigurable;
                TreeUtil.selectInTree(node, true, myTree);
                return;
              }
            }
          }
        } else {
          mySelectedConfigurable = null;
        }
        TreeUtil.selectFirstNode(myTree);
        drawPressAddButtonMessage(null);
      }
    });
    sortTree(myRoot);
    ((DefaultTreeModel)myTree.getModel()).reload();
  }

  private void updateCompileMethodComboStatus(final SingleConfigurationConfigurable<RunConfiguration> configurationConfigurable) {
    final ConfigurationSettingsEditorWrapper editor = (ConfigurationSettingsEditorWrapper)configurationConfigurable.getEditor();
    editor.setCompileMethodState(myCbCompileBeforeRunning.isSelected());
  }

  public static void sortTree(DefaultMutableTreeNode root) {
    TreeUtil.sort(root, new Comparator() {
      public int compare(final Object o1, final Object o2) {
        final Object userObject1 = ((DefaultMutableTreeNode)o1).getUserObject();
        final Object userObject2 = ((DefaultMutableTreeNode)o2).getUserObject();
        if (userObject1 instanceof ConfigurationType && userObject2 instanceof ConfigurationType){
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
    info.getEditor().addSettingsEditorListener(new SettingsEditorListener<RunnerAndConfigurationSettingsImpl>() {
      public void stateChanged(final SettingsEditor<RunnerAndConfigurationSettingsImpl> editor) {
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
    browser.setText(ExecutionBundle.message("empty.run.configuration.panel.text.label", font.getFontName(), addUrl, defaultsURL, configurationTypeDescription));
    browser.setPreferredSize(new Dimension(200, 50));
    myRightPanel.removeAll();
    myRightPanel.add(browser, BorderLayout.CENTER);
    myRightPanel.revalidate();
    myRightPanel.repaint();
  }

  public JPanel createLeftPanel() {
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
    leftPanel.add(editDefaultsButton, BorderLayout.SOUTH);
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
                           ExecutionBundle.message("run.configuration.edit.default.configuration.settings.button"),
                           EDIT_DEFUALTS_ICON){
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
    final JPanel bottomPanel = new JPanel(new GridLayout(1, 2, 5, 0));
    myCbShowSettingsBeforeRunning = new JCheckBox(ExecutionBundle.message("run.configuration.display.settings.checkbox"));
    bottomPanel.add(myCbShowSettingsBeforeRunning);

    myCbCompileBeforeRunning = new JCheckBox(ExecutionBundle.message("run.configuration.make.module.before.running.checkbox"));
    myCbCompileBeforeRunning.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final SingleConfigurationConfigurable<RunConfiguration> configurationConfigurable = getSelectedConfiguration();
        if (configurationConfigurable != null){
          updateCompileMethodComboStatus(configurationConfigurable);
        }
      }
    });
    bottomPanel.add(myCbCompileBeforeRunning);

    myWholePanel.add(bottomPanel, BorderLayout.SOUTH);
    bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    final ItemListener cbListener = new ItemListener() {
      public void itemStateChanged(final ItemEvent e) {
        setModified(true);
      }
    };
    myCbCompileBeforeRunning.addItemListener(cbListener);
    myCbShowSettingsBeforeRunning.addItemListener(cbListener);

    updateDialog();
    return myWholePanel;
  }

  public Icon getIcon() {
    return ICON;
  }

  public void reset() {
    final RunManagerEx manager = getRunManager();
    final RunManagerConfig config = manager.getConfig();
    myCbShowSettingsBeforeRunning.setSelected(config.isShowSettingsBeforeRun());
    myCbCompileBeforeRunning.setSelected(config.isCompileBeforeRunning());
    setModified(false);
  }

  public Project getProject() {
    return myProject;
  }

  public void apply() throws ConfigurationException {
    final RunManagerEx manager = getRunManager();
    final ConfigurationType[] configurationTypes = manager.getConfigurationFactories();
    for (ConfigurationType configurationType : configurationTypes) {
      applyByType(configurationType);
    }

    if (mySelectedConfigurable != null) {
      manager.setSelectedConfiguration(mySelectedConfigurable.getSettings());
    } else {
      manager.setSelectedConfiguration(null);
    }

    manager.getConfig().setShowSettingsBeforeRun(myCbShowSettingsBeforeRunning.isSelected());
    manager.getConfig().setCompileBeforeRunning(myCbCompileBeforeRunning.isSelected());

    setModified(false);
  }

  public void applyByType(ConfigurationType type) throws ConfigurationException {
    DefaultMutableTreeNode typeNode = getConfigurationTypeNode(type);
    final RunManagerImpl manager = getRunManager();
    final ArrayList<SingleConfigurationConfigurable> stableConfigurations = new ArrayList<SingleConfigurationConfigurable>();
    SingleConfigurationConfigurable tempConfiguration = null;

    if (typeNode != null) {
      for (int i = 0; i < typeNode.getChildCount(); i++) {
        final SingleConfigurationConfigurable configurable =
          ((SingleConfigurationConfigurable)((DefaultMutableTreeNode)typeNode.getChildAt(i))
            .getUserObject());
        if (manager.isTemporary((RunnerAndConfigurationSettingsImpl)configurable.getSettings())) {
          tempConfiguration = configurable;
        }
        else {
          stableConfigurations.add(configurable);
        }
      }
    }
    // try to apply all
    for (SingleConfigurationConfigurable configurable : stableConfigurations) {
      applyConfiguration(typeNode, configurable);
    }
    if (tempConfiguration != null) {
      applyConfiguration(typeNode, tempConfiguration);
    }

    // if apply succeeded, update the list of configurations in RunManager
    manager.removeConfigurations(type);
    for (final SingleConfigurationConfigurable<RunConfiguration> stableConfiguration : stableConfigurations) {
      final ConfigurationSettingsEditorWrapper settingsEditorWrapper = ((ConfigurationSettingsEditorWrapper)stableConfiguration.getEditor());
      manager.addConfiguration(stableConfiguration.getSettings(),
                               settingsEditorWrapper.isStoreProjectConfiguration(),
                               settingsEditorWrapper.getCompileMethodBeforeRunning());
    }
    if (tempConfiguration != null) {
      manager.setTemporaryConfiguration((RunnerAndConfigurationSettingsImpl)tempConfiguration.getSettings());
    }
  }

  @Nullable
  private DefaultMutableTreeNode getConfigurationTypeNode(final ConfigurationType type) {
    for (int i = 0; i < myRoot.getChildCount(); i++){
      final DefaultMutableTreeNode node = ((DefaultMutableTreeNode)myRoot.getChildAt(i));
      if (node.getUserObject() == type) return node;
    }
    return null;
  }

  private void applyConfiguration(DefaultMutableTreeNode typeNode, SingleConfigurationConfigurable configurable) throws ConfigurationException {
    try {
      if (configurable != null) configurable.apply();
    }
    catch (ConfigurationException e) {
      for (int i = 0 ; i < typeNode.getChildCount(); i++){
        final DefaultMutableTreeNode node = ((DefaultMutableTreeNode)typeNode.getChildAt(i));
        if (Comparing.equal(configurable, node.getUserObject())){
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
    final RunnerAndConfigurationSettingsImpl settings = runManager.getSelectedConfiguration();
    if (mySelectedConfigurable == null) {
      return settings != null;
    }
    if (settings == null ||
        mySelectedConfigurable.getConfiguration().getType() != settings.getType() ||
        (mySelectedConfigurable.getConfiguration().getType() == settings.getType() &&
         !Comparing.strEqual(mySelectedConfigurable.getNameText(), settings.getConfiguration().getName()))){
      return true;
    }
    final RunConfiguration[] allConfigurations = runManager.getAllConfigurations();
    final Set<RunConfiguration> currentConfigurations = new HashSet<RunConfiguration>();
    for(int i = 0; i < myRoot.getChildCount(); i++){
      DefaultMutableTreeNode typeNode = (DefaultMutableTreeNode)myRoot.getChildAt(i);
      final RunnerAndConfigurationSettingsImpl[] configurationSettings =
        runManager.getConfigurationSettings((ConfigurationType)typeNode.getUserObject());
      if (configurationSettings.length != typeNode.getChildCount()) return true;
      for(int j= 0; j < typeNode.getChildCount(); j++){
        SingleConfigurationConfigurable configurable = (SingleConfigurationConfigurable)((DefaultMutableTreeNode)typeNode.getChildAt(j)).getUserObject();
        if (!Comparing.strEqual(configurationSettings[j].getConfiguration().getName(), configurable.getConfiguration().getName())) return true;
        if (configurable.isModified()) return true;
        currentConfigurations.add(configurable.getConfiguration());
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
        if (node instanceof DefaultMutableTreeNode){
          final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)node;
          final Object userObject = treeNode.getUserObject();
          if (userObject instanceof SingleConfigurationConfigurable){
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

  void updateDialog() {
    if (myRunDialog == null) return;
    final StringBuffer buffer = new StringBuffer();
    buffer.append(myRunDialog.getRunnerInfo().getId());
    final SingleConfigurationConfigurable<RunConfiguration> configuration = getSelectedConfiguration();
    if (configuration != null) {
      buffer.append(" - ");
      buffer.append(configuration.getNameText());
    }
    myRunDialog.setOKActionEnabled(canRunConfiguration(configuration));
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
  private SingleConfigurationConfigurable<RunConfiguration> getSelectedConfiguration(){
    final TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath != null){
      final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
      if (treeNode.getUserObject() instanceof SingleConfigurationConfigurable){
        return (SingleConfigurationConfigurable<RunConfiguration>)treeNode.getUserObject();
      }
    }
    return null;
  }

  public static boolean canRunConfiguration(SingleConfigurationConfigurable<RunConfiguration> configuration) {
    try {
      return configuration != null && RunManagerImpl.canRunConfiguration(configuration.getSnapshot().getConfiguration());
    }
    catch (ConfigurationException e) {
      return false;
    }
  }

  private RunManagerImpl getRunManager() {
    return RunManagerImpl.getInstanceImpl(myProject);
  }

  public String getHelpTopic() {
    return "project.propRunDebug";
  }

  public void clickDefaultButton() {
    if (myRunDialog != null) myRunDialog.clickDefaultButton();
  }

  private DefaultMutableTreeNode getSelectedConfigurationTypeNode(){
    final DefaultMutableTreeNode node = (DefaultMutableTreeNode)myTree.getSelectionPath().getLastPathComponent();
    final Object userObject = node.getUserObject();
    if (userObject instanceof ConfigurationType){
      return node;
    } else {
      return (DefaultMutableTreeNode)node.getParent();
    }
  }

  private static String createUniqueName(DefaultMutableTreeNode typeNode) {
    String str = ExecutionBundle.message("run.configuration.unnamed.name.prefix");
    final ArrayList<String> currentNames = new ArrayList<String>();
    for (int i = 0; i < typeNode.getChildCount(); i++) {
      currentNames.add(((SingleConfigurationConfigurable)((DefaultMutableTreeNode)typeNode.getChildAt(i)).getUserObject()).getNameText());
    }
    if (!currentNames.contains(str)) return str;
    int i = 1;
    while (true) {
      if (!currentNames.contains(str + i)) return str + i;
      i++;
    }
  }

  private void createNewConfiguration(final RunnerAndConfigurationSettingsImpl settings, final DefaultMutableTreeNode node) {
    final SingleConfigurationConfigurable<RunConfiguration> configurationConfigurable = SingleConfigurationConfigurable.editSettings(settings);
    installUpdateListeners(configurationConfigurable);
    DefaultMutableTreeNode nodeToAdd = new DefaultMutableTreeNode(configurationConfigurable);
    node.add(nodeToAdd);
    ((DefaultTreeModel)myTree.getModel()).reload(node);
    TreeUtil.selectNode(myTree, nodeToAdd);
  }

  private void createNewConfiguration(final ConfigurationFactory factory) {
    DefaultMutableTreeNode node = null;
    for(int i = 0; i < myRoot.getChildCount(); i++){
      final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)myRoot.getChildAt(i);
      if (treeNode.getUserObject() == factory.getType()){
        node = treeNode;
        break;
      }
    }
    if (node == null){
      node = new DefaultMutableTreeNode(factory.getType());
      myRoot.add(node);
      sortTree(myRoot);
      ((DefaultTreeModel)myTree.getModel()).reload();
    }
    final RunnerAndConfigurationSettingsImpl settings = getRunManager().createConfiguration(createUniqueName(node), factory);
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
      final ConfigurationType[] configurationTypes = getRunManager().getConfigurationFactories();
      Arrays.sort(configurationTypes, new Comparator<ConfigurationType>() {
        public int compare(final ConfigurationType type1, final ConfigurationType type2) {
          return type1.getDisplayName().compareTo(type2.getDisplayName());
        }
      });
      final ListPopup popup =
        popupFactory.createWizardStep(new BaseListPopupStep<ConfigurationType>(ExecutionBundle.message("add.new.run.configuration.acrtion.name"), configurationTypes) {

          @NotNull
          public String getTextFor(final ConfigurationType type) {
            return type.getDisplayName();
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
            return PopupStep.FINAL_CHOICE;
          }

          public int getDefaultOptionIndex() {
            final TreePath selectionPath = myTree.getSelectionPath();            
            if (selectionPath != null){
              DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
              final Object userObject = node.getUserObject();
              ConfigurationType type = null;
              if (userObject instanceof SingleConfigurationConfigurable){
                final SingleConfigurationConfigurable configurable = (SingleConfigurationConfigurable)userObject;
                type = configurable.getConfiguration().getType();
              } else if (userObject instanceof ConfigurationType){
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
                return PopupStep.FINAL_CHOICE;
              }

            };
          }

          public boolean hasSubstep(final ConfigurationType type) {
            return type.getConfigurationFactories().length > 1;
          }

        });
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
      final DefaultMutableTreeNode typeNode = getSelectedConfigurationTypeNode();
      final DefaultMutableTreeNode child = (DefaultMutableTreeNode)myTree.getSelectionPath().getLastPathComponent();
      ((SingleConfigurationConfigurable)child.getUserObject()).disposeUIResources();
      int index = typeNode.getIndex(child);
      typeNode.remove(child);
      if (typeNode.getChildCount() > 0){
        ((DefaultTreeModel)myTree.getModel()).reload(typeNode);
        TreeUtil.selectInTree((DefaultMutableTreeNode)(index < typeNode.getChildCount() ? typeNode.getChildAt(index) : typeNode.getChildAt(index - 1)), true, myTree);
      } else {
        index = myRoot.getIndex(typeNode);
        myRoot.remove(typeNode);
        ((DefaultTreeModel)myTree.getModel()).reload();
        if (myRoot.getChildCount() > 0) {
          final TreeNode treeNode = index < myRoot.getChildCount() ? myRoot.getChildAt(index) : myRoot.getChildAt(index - 1);
          TreeUtil.selectInTree((DefaultMutableTreeNode)treeNode.getChildAt(0), true, myTree);
        } else {
          mySelectedConfigurable = null;
          drawPressAddButtonMessage(null);
        }
      }
    }


    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(getSelectedConfiguration() != null);
    }

  }

  private class MyCopyAction extends AnAction {
    public MyCopyAction() {
      super(ExecutionBundle.message("copy.configuration.action.name"),
            ExecutionBundle.message("copy.configuration.action.name"),
            COPY_ICON);
      registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_D, KeyEvent.CTRL_MASK)), myTree);
    }


    public void actionPerformed(AnActionEvent e) {
      final SingleConfigurationConfigurable<RunConfiguration> configuration = getSelectedConfiguration();
      LOG.assertTrue(configuration != null);
      try {
        final DefaultMutableTreeNode typeNode = getSelectedConfigurationTypeNode();
        final RunnerAndConfigurationSettingsImpl settings = configuration.getSnapshot();
        settings.setName(createUniqueName(typeNode));
        createNewConfiguration(settings, typeNode);
      }
      catch (ConfigurationException e1) {
        Messages.showErrorDialog(myToolbarComponent, e1.getMessage(), e1.getTitle());
      }
    }

    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(getSelectedConfiguration() != null);
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
      final RunnerAndConfigurationSettingsImpl originalConfiguration = configurationConfigurable.getSettings();
      if (getRunManager().isTemporary(originalConfiguration)) {
        getRunManager().makeStable(originalConfiguration.getConfiguration());
      }
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
    private int myDirection;

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
      if (selectionPath != null){
        final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
        if (treeNode.getUserObject() instanceof SingleConfigurationConfigurable){
          if (myDirection < 0){
            presentation.setEnabled(treeNode.getPreviousSibling() != null);
          } else {
            presentation.setEnabled(treeNode.getNextSibling() != null);
          }
        }
      }
    }
  }
}
