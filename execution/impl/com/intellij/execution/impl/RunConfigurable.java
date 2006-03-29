package com.intellij.execution.impl;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorListener;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.*;
import com.intellij.util.config.StorageAccessors;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;

class RunConfigurable extends BaseConfigurable {
  private static final Icon ICON = IconLoader.getIcon("/general/configurableRunDebug.png");
  @NonNls private static final String GENERAL_ADD_ICON_PATH = "/general/add.png";
  private static final Icon ADD_ICON = IconLoader.getIcon(GENERAL_ADD_ICON_PATH);
  private static final Icon REMOVE_ICON = IconLoader.getIcon("/general/remove.png");
  private static final Icon COPY_ICON = IconLoader.getIcon("/actions/copy.png");
  private static final Icon SAVE_ICON = IconLoader.getIcon("/runConfigurations/saveTempConfig.png");
  private static final Icon EDIT_DEFUALTS_ICON = IconLoader.getIcon("/general/ideOptions.png");
  @NonNls private static final String DIVIDER_PROPORTION = "dividerProportion";

  private final Project myProject;
  private final RunDialog myRunDialog;
  private JCheckBox myCbShowSettingsBeforeRunning;
  @NonNls private DefaultMutableTreeNode myRoot = new DefaultMutableTreeNode("Root");
  private JTree myTree = new JTree(myRoot);
  private JPanel myRightPanel = new JPanel(new BorderLayout());
  private JComponent myToolbarComponent;
  private Splitter myPanel = new Splitter();
  private JPanel myWholePanel;
  private StorageAccessors myConfig;
  private JCheckBox myCbCompileBeforeRunning;

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
    myTree.addMouseListener(new PopupHandler() {
      public void invokePopup(Component comp, int x, int y) {
        if (myTree.getPathForLocation(x, y) != null &&
            Arrays.binarySearch(myTree.getSelectionRows(), myTree.getRowForLocation(x, y)) > -1) {
          final ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN, createActionsGroup());
          popupMenu.getComponent().show(comp, x, y);
        }
      }
    });
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
      final DefaultMutableTreeNode typeNode = new DefaultMutableTreeNode(type);
      myRoot.add(typeNode);
      final RunnerAndConfigurationSettingsImpl[] configurations = manager.getConfigurationSettings(type);
      for (RunnerAndConfigurationSettingsImpl configuration : configurations) {
        final SingleConfigurationConfigurable<RunConfiguration> configurationConfigurable =
          SingleConfigurationConfigurable.editSettings(configuration);
        installUpdateListeners(configurationConfigurable);
        typeNode.add(new DefaultMutableTreeNode(configurationConfigurable));
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
            final SingleConfigurationConfigurable<RunConfiguration> configurationConfigurable = (SingleConfigurationConfigurable<RunConfiguration>)userObject;
            myRightPanel.add(configurationConfigurable.createComponent(), BorderLayout.CENTER);
            updateCompileMethodComboStatus(configurationConfigurable);
            setupDialogBounds();
          } else if (userObject instanceof ConfigurationType){
            drawPressAddButtonMessage(((ConfigurationType)userObject));
          }
        }
        updateDialog();
      }
    });
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        myTree.requestFocusInWindow();
        TreeUtil.selectFirstNode(myTree);
        final RunnerAndConfigurationSettings settings = manager.getSelectedConfiguration();
        if (settings == null) return;
        final Enumeration enumeration = myRoot.breadthFirstEnumeration();
        while (enumeration.hasMoreElements()){
          final DefaultMutableTreeNode node = (DefaultMutableTreeNode)enumeration.nextElement();
          final Object userObject = node.getUserObject();
          if (userObject instanceof SingleConfigurationConfigurable) {
            final SingleConfigurationConfigurable configurationConfigurable = ((SingleConfigurationConfigurable)userObject);
            if (configurationConfigurable.getConfiguration().getType() == settings.getType() &&
                Comparing.strEqual(configurationConfigurable.getConfiguration().getName(),
                                   settings.getName())){
              TreeUtil.selectInTree(node, true, myTree);
              break;
            }
          }
        }
      }
    });
    sortTree();
    ((DefaultTreeModel)myTree.getModel()).reload();
  }

  private void updateCompileMethodComboStatus(final SingleConfigurationConfigurable<RunConfiguration> configurationConfigurable) {
    final ConfigurationSettingsEditorWrapper editor = (ConfigurationSettingsEditorWrapper)configurationConfigurable.getEditor();
    editor.setCompileMethodState(myCbCompileBeforeRunning.isSelected());
  }

  private void sortTree() {
    TreeUtil.sort(myRoot, new Comparator() {
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
    final DefaultMutableTreeNode node = (DefaultMutableTreeNode)myTree.getSelectionPath().getLastPathComponent();
    ((DefaultTreeModel)myTree.getModel()).reload(node);
  }

  private void installUpdateListeners(final SingleConfigurationConfigurable<RunConfiguration> info) {
    info.getEditor().addSettingsEditorListener(new SettingsEditorListener<RunnerAndConfigurationSettingsImpl>() {
      public void stateChanged(final SettingsEditor<RunnerAndConfigurationSettingsImpl> editor) {
        update();
        setupDialogBounds();
      }
    });
    info.getEditor().addSettingsEditorListener(new SettingsEditorListener<RunnerAndConfigurationSettingsImpl>() {
      public void stateChanged(SettingsEditor settingsEditor) {
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
    final URL url = getClass().getResource(GENERAL_ADD_ICON_PATH);
    final Font font = UIUtil.getLabelFont();
    final String configurationTypeDescription = configurationType.getConfigurationTypeDescription();
    browser.setText(
      ExecutionBundle.message("empty.run.configuration.panel.text.label", font.getFontName(), url, configurationTypeDescription));
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
    return leftPanel;
  }

  private ConfigurationType getSelectedType() {
    return (ConfigurationType)getSelectedConfigurationTypeNode().getUserObject();
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
        ConfigurationType type = getSelectedType();
        final SingleConfigurableEditor editor =
          new SingleConfigurableEditor(getProject(), TypeTemplatesConfigurable.createConfigurable(type, getProject()));
        editor.setTitle(ExecutionBundle.message("default.settings.editor.dialog.title", type.getDisplayName()));
        editor.show();
      }
    });
    return group;
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

    for (int i = 0; i < myRoot.getChildCount(); i++) {
      applyByType((DefaultMutableTreeNode)myRoot.getChildAt(i));
    }

    final SingleConfigurationConfigurable<RunConfiguration> configurationConfigurable = getSelectedConfiguration();
    if (configurationConfigurable != null) {
      manager.setSelectedConfiguration(configurationConfigurable.getSettings());
    } else {
      manager.setSelectedConfiguration(null);
    }

    manager.getConfig().setShowSettingsBeforeRun(myCbShowSettingsBeforeRunning.isSelected());
    manager.getConfig().setCompileBeforeRunning(myCbCompileBeforeRunning.isSelected());

    setModified(false);
  }

  public void applyByType(DefaultMutableTreeNode typeNode) throws ConfigurationException {

    final RunManagerImpl manager = getRunManager();
    final ArrayList<SingleConfigurationConfigurable> stableConfigurations = new ArrayList<SingleConfigurationConfigurable>();
    SingleConfigurationConfigurable tempConfiguration = null;

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
    // try to apply all
    for (SingleConfigurationConfigurable configurable : stableConfigurations) {
      applyConfiguration(typeNode, configurable);
    }
    if (tempConfiguration != null) {
      applyConfiguration(typeNode, tempConfiguration);
    }

    // if apply succeeded, update the list of configurations in RunManager
    manager.removeConfigurations((ConfigurationType)typeNode.getUserObject());
    for (final SingleConfigurationConfigurable stableConfiguration : stableConfigurations) {
      manager.addConfiguration((RunnerAndConfigurationSettingsImpl)stableConfiguration.getSettings());
    }
    if (tempConfiguration != null) {
      manager.setTemporaryConfiguration((RunnerAndConfigurationSettingsImpl)tempConfiguration.getSettings());
    }
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
    final Object userObject = ((DefaultMutableTreeNode)myTree.getSelectionPath().getLastPathComponent()).getUserObject();
    if (userObject instanceof SingleConfigurationConfigurable){
      if (settings == null) return true;
      final SingleConfigurationConfigurable configurationConfigurable = ((SingleConfigurationConfigurable)userObject);
      if (configurationConfigurable.getConfiguration().getType() == settings.getType() &&
          !Comparing.strEqual(configurationConfigurable.getNameText(),
                              settings.getConfiguration().getName())) return true;
    }
    final boolean [] modified = new boolean[1];
    TreeUtil.traverseDepth(myRoot, new TreeUtil.Traverse() {
      public boolean accept(Object node) {
        if (node instanceof DefaultMutableTreeNode){
          final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)node;
          final Object userObject = treeNode.getUserObject();
          if (userObject instanceof SingleConfigurationConfigurable){
            if (((SingleConfigurationConfigurable)userObject).isModified()){
              modified[0] = true;
              return false;
            }
          }
        }
        return true;
      }
    });
    return modified[0];
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
        myWholePanel.revalidate();
        myWholePanel.repaint();
        final Window window = SwingUtilities.windowForComponent(myWholePanel);
        if (window != null &&
            (window.getSize().height < window.getMinimumSize().height ||
             window.getSize().width < window.getMinimumSize().width)) {
          window.pack();
        }
      }
    });
  }

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

  private class MyToolbarAddAction extends AnAction {
    public MyToolbarAddAction() {
      super(ExecutionBundle.message("add.new.run.configuration.acrtion.name"),
            ExecutionBundle.message("add.new.run.configuration.acrtion.name"), ADD_ICON);
      registerCustomShortcutSet(CommonShortcuts.INSERT, myTree);
    }

    public void actionPerformed(AnActionEvent e) {
      final ConfigurationType type = getSelectedType();
      final ConfigurationFactory[] configurationFactories = type.getConfigurationFactories();
      if (configurationFactories.length > 1){
        final DefaultActionGroup group = new DefaultActionGroup();
        for (ConfigurationFactory factory : configurationFactories) {
          group.add(new MyAddAction(factory));
        }
        JBPopupFactory.getInstance().createActionGroupPopup(ExecutionBundle.message("add.new.run.configuration.action.name", getSelectedType().getDisplayName()),
                                                            group,
                                                            e.getDataContext(),
                                                            JBPopupFactory.ActionSelectionAid.NUMBERING,
                                                            false)
          .showUnderneathOf(myToolbarComponent);
      } else {
        new MyAddAction(configurationFactories[0]).actionPerformed(e);
      }
    }
  }

  private class MyAddAction extends AnAction {
    private ConfigurationFactory myFactory;
    public MyAddAction(ConfigurationFactory factory) {
      super(factory.getName());
      myFactory = factory;
    }

    public void actionPerformed(AnActionEvent e) {
      final DefaultMutableTreeNode node = getSelectedConfigurationTypeNode();
      final RunnerAndConfigurationSettingsImpl settings = getRunManager().createConfiguration(createUniqueName(node), myFactory);
      createNewConfiguration(settings, node);
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
      final int index = typeNode.getIndex(child);
      typeNode.remove(index);
      ((DefaultTreeModel)myTree.getModel()).reload(typeNode);
      if (typeNode.getChildCount() > 0){
        TreeUtil.selectNode(myTree, index < typeNode.getChildCount() ? typeNode.getChildAt(index) : typeNode.getChildAt(index - 1));
      } else {
        TreeUtil.selectNode(myTree, typeNode);
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
      try {
        final DefaultMutableTreeNode typeNode = getSelectedConfigurationTypeNode();
        final RunnerAndConfigurationSettingsImpl settings = configuration.getSnapshot();
        settings.setName(createUniqueName(typeNode));
        final RunManagerConfig managerConfig = getRunManager().getConfig();
        final ConfigurationSettingsEditorWrapper settingsEditorWrapper = (ConfigurationSettingsEditorWrapper)configuration.getEditor();
        managerConfig.setCompileMethodBeforeRunning(settings.getConfiguration(), settingsEditorWrapper.getCompileMethodBeforeRunning());
        managerConfig.setStoreProjectConfiguration(settings.getConfiguration(), settingsEditorWrapper.isStoreProjectConfiguration());
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
}
