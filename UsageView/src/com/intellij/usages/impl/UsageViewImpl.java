package com.intellij.usages.impl;

import com.intellij.ide.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SmartExpander;
import com.intellij.ui.content.Content;
import com.intellij.usages.*;
import com.intellij.usages.rules.MergeableUsage;
import com.intellij.usages.rules.UsageGroupingRuleProvider;
import com.intellij.usages.rules.UsageInFile;
import com.intellij.usages.rules.UsageInFiles;
import com.intellij.util.Alarm;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.Processor;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 16, 2004
 * Time: 4:54:09 PM
 * To change this template use File | Settings | File Templates.
 */
public class UsageViewImpl implements UsageView {
  private UsageNodeTreeBuilder myBuilder;
  private MyPanel myRootPanel;
  private JTree myTree = new JTree();
  private Content myContent;
  private UsageViewPresentation myPresentation;
  private UsageTarget[] myTargets;
  private Factory<UsageSearcher> myUsageSearcherFactory;
  private Project myProject;
  private TreeExpander myTreeExpander;
  private boolean mySearchInProgress = true;
  private ExporterToTextFile myTextFileExporter;
  private Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private Alarm myFlushAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private UsageModelTracker myModelTracker;
  private Map<Usage, UsageNode> myUsageNodes = new HashMap<Usage, UsageNode>();
  private ButtonPanel myButtonPanel = new ButtonPanel();
  private boolean myChangesDetected = false;
  private List<Usage> myUsagesToFlush = new ArrayList<Usage>();

  public UsageViewImpl(UsageViewPresentation presentation,
                       UsageTarget[] targets,
                       Factory<UsageSearcher> usageSearcherFactory,
                       Project project) {

    myPresentation = presentation;
    myTargets = targets;
    myUsageSearcherFactory = usageSearcherFactory;
    myProject = project;
    myRootPanel = new MyPanel(myTree);

    UsageViewTreeModelBuilder model = new UsageViewTreeModelBuilder(myPresentation, targets);
    myBuilder = new UsageNodeTreeBuilder(getRuleProvider().getActiveRules(project), (GroupNode)model.getRoot());
    myTree.setModel(model);

    myRootPanel.setLayout(new BorderLayout());

    JPanel centralPanel = new JPanel();
    centralPanel.setLayout(new BorderLayout());
    myRootPanel.add(centralPanel, BorderLayout.CENTER);

    JPanel toolbarPanel = new JPanel(new BorderLayout());
    toolbarPanel.add(createToolbar(), BorderLayout.WEST);
    toolbarPanel.add(createFiltersToolbar(), BorderLayout.CENTER);
    myRootPanel.add(toolbarPanel, BorderLayout.WEST);

    centralPanel.add(ScrollPaneFactory.createScrollPane(myTree), BorderLayout.CENTER);
    centralPanel.add(myButtonPanel, BorderLayout.SOUTH);

    initTree();

    myTree.setCellRenderer(new UsageViewTreeCellRenderer(this));
    collapseAll();

    myModelTracker = new UsageModelTracker(project);
    myModelTracker.addListener(new UsageModelTracker.UsageModelTrackerListener() {
                                     public void modelChanged() {
                                       myChangesDetected = true;
                                       updateLater();
                                     }
                                   });

    if (myPresentation.isShowCancelButton()) {
      addButtonToLowerPane(new Runnable() {
                                 public void run() {
                                   close();
                                 }
                               }, "Cancel", 'C');
    }
  }

  private void initTree() {
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    SmartExpander.installOn(myTree);
    TreeUtil.installActions(myTree);
    EditSourceOnDoubleClickHandler.install(myTree);
    myTree.addKeyListener(
        new KeyAdapter() {
        public void keyPressed(KeyEvent e) {
          if (KeyEvent.VK_ENTER == e.getKeyCode()) {
            TreePath leadSelectionPath = myTree.getLeadSelectionPath();
            if (leadSelectionPath == null) return;

            DefaultMutableTreeNode node = ((DefaultMutableTreeNode)leadSelectionPath.getLastPathComponent());
            if (node instanceof UsageNode) {
              Usage usage = ((UsageNode)node).getUsage();
              usage.navigate(false);
              usage.highlightInEditor();
            }
            else {
              Navigatable navigatable = getNavigateableForNode(node);
              if (navigatable != null && navigatable.canNavigate()) {
                navigatable.navigate(false);
              }
            }
          }
        }
      }
    );

    PopupHandler.installPopupHandler(myTree, IdeActions.GROUP_USAGE_VIEW_POPUP, ActionPlaces.USAGE_VIEW_POPUP);
    //TODO: install speed search. Not in openapi though. It makes sense to create a common TreeEnchancer service.
  }

  private JComponent createToolbar() {
    DefaultActionGroup group = new DefaultActionGroup() {
      public void update(AnActionEvent e) {
        super.update(e);
        myButtonPanel.update(e);
      }
    };

    AnAction[] actions = createActions();
    for (int i = 0; i < actions.length; i++) {
      AnAction action = actions[i];
      if (action != null) group.add(action);
    }
    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.USAGE_VIEW_TOOLBAR,
                                                                                  group, false);
    return actionToolbar.getComponent();
  }

  private JComponent createFiltersToolbar() {
    DefaultActionGroup group = new DefaultActionGroup();
    AnAction[] actions = createGroupingActions();
    for (int i = 0; i < actions.length; i++) {
      group.add(actions[i]);
    }

    group.add(new MergeDupLines());

    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.USAGE_VIEW_TOOLBAR,
                                                                                  group, false);
    return actionToolbar.getComponent();
  }

  private AnAction[] createActions() {
    myTreeExpander = new TreeExpander() {
      public void expandAll() {
        UsageViewImpl.this.expandAll();
      }

      public boolean canExpand() {
        return true;
      }

      public void collapseAll() {
        UsageViewImpl.this.collapseAll();
      }

      public boolean canCollapse() {
        return true;
      }
    };

    CommonActionsManager actionsManager = CommonActionsManager.getInstance();

    myTextFileExporter = new ExporterToTextFile(this);
    return new AnAction[]{
      canPerformReRun() ? new ReRunAction() : null,
      new CloseAction(),
      actionsManager.createCollapseAllAction(myTreeExpander),
      actionsManager.createExpandAllAction(myTreeExpander),
      actionsManager.createPrevOccurenceAction(myRootPanel),
      actionsManager.createNextOccurenceAction(myRootPanel),
      actionsManager.installAutoscrollToSourceHandler(myProject, myTree, new MyAutoScrollToSourceOptionProvider()),
      actionsManager.createExportToTextFileAction(myTextFileExporter),
      actionsManager.createHelpAction(null)
    };
  }

  private UsageGroupingRuleProviderImpl getRuleProvider() {
    return (UsageGroupingRuleProviderImpl)ApplicationManager.getApplication().getComponent(UsageGroupingRuleProvider.class);
  }

  private AnAction[] createGroupingActions() {
    return getRuleProvider().createFilteringActions(this);
  }

  public void rulesChanged() {
    Collection<Usage> allUsages = myUsageNodes.keySet();
    reset();
    myBuilder.setRules(getRuleProvider().getActiveRules(myProject));
    for (Iterator<Usage> i = allUsages.iterator(); i.hasNext();) {
      Usage usage = i.next();
      if (usage instanceof MergeableUsage) {
        ((MergeableUsage)usage).reset();
      }
      appendUsage(usage);
    }
  }

  private void expandAll() {
    TreeUtil.expandAll(myTree);
  }

  private void collapseAll() {
    TreeUtil.collapseAll(myTree, 3);
    TreeUtil.expand(myTree, 2);
  }

  public DefaultMutableTreeNode getModelRoot() {
    return (DefaultMutableTreeNode)myTree.getModel().getRoot();
  }


  private class CloseAction extends AnAction {
    private CloseAction() {
      super("Close", null, IconLoader.getIcon("/actions/cancel.png"));
    }

    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setVisible(myContent != null);
    }

    public void actionPerformed(AnActionEvent e) {
      close();
    }
  }

  private class MergeDupLines extends ToggleAction {
    public MergeDupLines() {
      super("Merge usages from the same line", null, IconLoader.getIcon("/toolbar/filterdups.png"));
    }

    public boolean isSelected(AnActionEvent e) {
      return UsageViewSettings.getInstance().IS_FILTER_DUPLICATED_LINE;
    }

    public void setSelected(AnActionEvent e, boolean state) {
      UsageViewSettings.getInstance().IS_FILTER_DUPLICATED_LINE = state;
      rulesChanged();
    }
  }

  private class ReRunAction extends AnAction {
    public ReRunAction() {
      super("Rerun", "Rerun search", IconLoader.getIcon("/actions/refreshUsages.png"));
      registerCustomShortcutSet(CommonShortcuts.getRerun(), myRootPanel);
    }

    public void actionPerformed(AnActionEvent e) {
      refreshUsages();
    }
  }

  private void refreshUsages() {
    reset();
    doReRun();
  }

  private void doReRun() {
    final Application application = ApplicationManager.getApplication();

    application.runProcessWithProgressSynchronously(
        new Runnable() {
        public void run() {
          setSearchInProgress(true);

          myChangesDetected = false;
          UsageSearcher usageSearcher = myUsageSearcherFactory.create();
          usageSearcher.generate(new Processor<Usage>() {
            public boolean process(final Usage usage) {
              appendUsageLater(usage);
              ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
              return indicator != null ? !indicator.isCanceled() : true;
            }
          });

          setSearchInProgress(false);
        }
      }, UsageViewManagerImpl.getProgressTitile(myPresentation), true, myProject);
  }

  private void reset() {
    myUsageNodes = new HashMap<Usage, UsageNode>();
    ((UsageViewTreeModelBuilder)myTree.getModel()).reset();
    TreeUtil.expand(myTree, 2);
  }

  public void appendUsageLater(final Usage usage) {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      myFlushAlarm.cancelAllRequests();
      myFlushAlarm.addRequest(new Runnable() {
        public void run() {
          flush();
        }
      }, 300, indicator.getModalityState());
    }

    synchronized (myUsagesToFlush) {
      myUsagesToFlush.add(usage);
      if (myUsagesToFlush.size() > 50) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            flush();
          }
        });
      }
    }
  }

  private void flush() {
    synchronized (myUsagesToFlush) {
      for (int i = 0; i < myUsagesToFlush.size(); i++) {
        Usage usage = myUsagesToFlush.get(i);
        appendUsage(usage);
      }
      myUsagesToFlush.clear();
    }
  }

  public void appendUsage(Usage usage) {
    UsageNode node = myBuilder.appendUsage(usage);
    if (myUsageNodes.size() == 0) { //first usage
      TreePath usagePath = new TreePath(node.getPath());
      myTree.expandPath(usagePath.getParentPath());
      myTree.setSelectionPath(usagePath);
    }
    myUsageNodes.put(usage, node);
  }

  public void includeUsages(Usage[] usages) {
    for (int i = 0; i < usages.length; i++) {
      myUsageNodes.get(usages[i]).setUsageExcluded(false);
    }
    updateImmediately();
  }

  public void excludeUsages(Usage[] usages) {
    for (int i = 0; i < usages.length; i++) {
      myUsageNodes.get(usages[i]).setUsageExcluded(true);
    }
    updateImmediately();
  }

  public JComponent getComponent() {
    return myRootPanel;
  }

  public void setContent(Content content) {
    myContent = content;
    content.setDisposer(this);
  }

  private void updateImmediately() {
    checkNodeValidity((DefaultMutableTreeNode)myTree.getModel().getRoot());
  }

  private void checkNodeValidity(DefaultMutableTreeNode node) {
    Enumeration enumeration = node.children();
    while (enumeration.hasMoreElements()) {
      checkNodeValidity((DefaultMutableTreeNode)enumeration.nextElement());
    }
    if (node instanceof Node && node != getModelRoot()) ((Node)node).update();
  }

  private void updateLater() {
    myUpdateAlarm.cancelAllRequests();
    myUpdateAlarm.addRequest(
        new Runnable() {
        public void run() {
          updateImmediately();
        }
      },
      300
    );
  }

  public void close() {
    com.intellij.usageView.UsageViewManager.getInstance(myProject).closeContent(myContent);
  }

  public void dispose() {
    myModelTracker.dispose();
    myUpdateAlarm.cancelAllRequests();
  }

  public boolean isSearchInProgress() {
    return mySearchInProgress;
  }

  public void setSearchInProgress(boolean searchInProgress) {
    mySearchInProgress = searchInProgress;
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        flush();
      }
    });
  }

  public void addButtonToLowerPane(final Runnable runnable, String text, char mnemonic) {
    int index = myButtonPanel.getComponentCount();

    if (index > 0 && myPresentation.isShowCancelButton()) index--;

    myButtonPanel.add(
      index,
      runnable,
      text,
      mnemonic
    );
  }

  public void addPerformOperationAction(final Runnable processRunnable,
                                        final String commandName,
                                        final String cannotMakeString,
                                        String shortDescription,
                                        char mnemonic) {

    addButtonToLowerPane(
      new Runnable() {
        public void run() {
          checkReadonlyUsages();
          PsiDocumentManager.getInstance(myProject).commitAllDocuments();
          if (cannotMakeString != null && myChangesDetected) {
            if (canPerformReRun() && allTargetsAreValid()) {
              int answer = Messages.showYesNoDialog(
                myProject,
                cannotMakeString + "\nWould you like to rerun the search now?",
                "Error",
                Messages.getErrorIcon()
              );
              if (answer == 0) {
                refreshUsages();
              }
            }
            else {
              Messages.showMessageDialog(
                myProject,
                cannotMakeString,
                "Error",
                Messages.getErrorIcon()
              );
              //todo[myakovlev] request focus to tree
              //myUsageView.getTree().requestFocus();
            }
            return;
          }

          close();

          CommandProcessor.getInstance().executeCommand(
              myProject, new Runnable() {
              public void run() {
                processRunnable.run();
              }
            },
            commandName,
            null
          );

        }

        private boolean allTargetsAreValid() {
          for (int i = 0; i < myTargets.length; i++) {
            UsageTarget target = myTargets[i];
            if (!target.isValid()) return false;
          }

          return true;
        }
      }, shortDescription, mnemonic);



  }

  public UsageViewPresentation getPresentation() {
    return myPresentation;
  }

  private boolean canPerformReRun() {
    return myUsageSearcherFactory != null;
  }

  private void checkReadonlyUsages() {
    final Set<VirtualFile> readOnlyUsages = getReadOnlyUsagesFiles();

    if (!readOnlyUsages.isEmpty()) {
      String readOnlyWarning = "Occurrences found in read-only files.\n" +
                               "The operation will not affect them.\n" +
                               "Continue anyway?";
      int result = Messages.showOkCancelDialog(
        myProject,
        readOnlyWarning,
        "Read-Only Files Found",
        Messages.getWarningIcon());
      if (result != 0) {
        VirtualFileManager.getInstance().fireReadOnlyModificationAttempt(
          readOnlyUsages.toArray(new VirtualFile[readOnlyUsages.size()])
        );
        return;
      }
    }
  }

  private Set<Usage> getReadOnlyUsages() {
    Set<Usage> result = new HashSet<Usage>();
    Collection<UsageNode> usageNodes = myUsageNodes.values();
    for (Iterator<UsageNode> i = usageNodes.iterator(); i.hasNext();) {
      UsageNode node = i.next();
      if (node.getUsage().isReadOnly()) {
        result.add(node.getUsage());
      }
    }

    return result;
  }

  private Set<VirtualFile> getReadOnlyUsagesFiles() {
    Set<Usage> usages = getReadOnlyUsages();
    Set<VirtualFile> result = new HashSet<VirtualFile>();
    for (Iterator<Usage> i = usages.iterator(); i.hasNext();) {
      Usage usage = i.next();
      if (usage instanceof UsageInFile) {
        UsageInFile usageInFile = (UsageInFile)usage;
        result.add(usageInFile.getFile());
      }

      if (usage instanceof UsageInFiles) {
        UsageInFiles usageInFiles = (UsageInFiles)usage;
        result.addAll(Arrays.asList(usageInFiles.getFiles()));
      }
    }
    return result;
  }

  public Set<Usage> getExcludedUsages() {
    Set<Usage> result = new HashSet<Usage>();
    Collection<UsageNode> usageNodes = myUsageNodes.values();
    for (Iterator<UsageNode> i = usageNodes.iterator(); i.hasNext();) {
      UsageNode node = i.next();
      if (node.isExcluded()) result.add(node.getUsage());
    }

    return result;
  }


  public Node getSelectedNode() {
    TreePath leadSelectionPath = myTree.getLeadSelectionPath();
    if (leadSelectionPath == null) return null;

    DefaultMutableTreeNode node = ((DefaultMutableTreeNode)leadSelectionPath.getLastPathComponent());
    return node instanceof Node ? (Node)node : null;
  }

  private Usage[] getSelectedUsages() {
    TreePath[] selectionPaths = myTree.getSelectionPaths();
    if (selectionPaths == null) return null;

    Set<Usage> usages = new HashSet<Usage>();
    for (int i = 0; i < selectionPaths.length; i++) {
      TreePath selectionPath = selectionPaths[i];
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
      collectUsages(node, usages);
    }

    return usages.toArray(new Usage[usages.size()]);
  }

  private void collectUsages(DefaultMutableTreeNode node, Set<Usage> usages) {
    if (node instanceof UsageNode) {
      UsageNode usageNode = (UsageNode)node;
      usages.add(usageNode.getUsage());
    }

    Enumeration enumeration = node.children();
    while (enumeration.hasMoreElements()) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode)enumeration.nextElement();
      collectUsages(child, usages);
    }
  }

  private UsageTarget[] getSelectedUsageTargets() {
    TreePath[] selectionPaths = myTree.getSelectionPaths();
    if (selectionPaths == null) return null;

    Set<UsageTarget> targets = new HashSet<UsageTarget>();
    for (int i = 0; i < selectionPaths.length; i++) {
      TreePath selectionPath = selectionPaths[i];
      Object lastPathComponent = selectionPath.getLastPathComponent();
      if (lastPathComponent instanceof UsageTargetNode) {
        UsageTargetNode usageTargetNode = (UsageTargetNode)lastPathComponent;
        UsageTarget target = usageTargetNode.getTarget();
        targets.add(target);
      }
    }

    return targets.size() > 0 ? targets.toArray(new UsageTarget[targets.size()]) : null;
  }

  public Navigatable getNavigateableForNode(DefaultMutableTreeNode node) {
    if (node == null) return null;
    Object userObject = node.getUserObject();
    if (userObject instanceof Navigatable) {
      return (Navigatable)userObject;
    }
    return null;
  }

  public boolean areTargetsValid() {
    return ((UsageViewTreeModelBuilder)myTree.getModel()).areTargetsValid();
  }

  private class MyPanel extends JPanel implements DataProvider, OccurenceNavigator {
    private OccurenceNavigatorSupport mySupport;

    public MyPanel(JTree tree) {
      mySupport = new OccurenceNavigatorSupport(tree) {
        protected Navigatable createDescriptorForNode(DefaultMutableTreeNode node) {
          if (node.getChildCount() > 0) return null;
          if (!((Node)node).isValid()) return null;
          return getNavigateableForNode(node);
        }

        public String getNextOccurenceActionName() {
          return "Next Occurence";
        }

        public String getPreviousOccurenceActionName() {
          return "Previous Occurence";
        }
      };
    }

    public boolean hasNextOccurence() {
      return mySupport.hasNextOccurence();
    }

    public boolean hasPreviousOccurence() {
      return mySupport.hasPreviousOccurence();
    }

    public OccurenceInfo goNextOccurence() {
      return mySupport.goNextOccurence();
    }

    public OccurenceInfo goPreviousOccurence() {
      return mySupport.goPreviousOccurence();
    }

    public String getNextOccurenceActionName() {
      return mySupport.getNextOccurenceActionName();
    }

    public String getPreviousOccurenceActionName() {
      return mySupport.getPreviousOccurenceActionName();
    }

    public Object getData(String dataId) {
      Node node = getSelectedNode();

      if (dataId.equals(USAGE_VIEW)) {
        return UsageViewImpl.this;
      }

      if (dataId.equals(DataConstants.NAVIGATABLE)) {
        return getNavigateableForNode(node);
      }

      if (dataId.equals(DataConstants.EXPORTER_TO_TEXT_FILE)) {
        return myTextFileExporter;
      }

      if (dataId.equals(USAGES)) {
        return getSelectedUsages();
      }

      if (dataId.equals(USAGE_TARGETS)) {
        Object selectedUsageTargets = getSelectedUsageTargets();
        if (selectedUsageTargets != null) return selectedUsageTargets;
      }

      if (node != null) {
        Object userObject = node.getUserObject();
        if (userObject instanceof DataProvider) {
          DataProvider dataProvider = (DataProvider)userObject;
          return dataProvider.getData(dataId);
        }
      }

      return null;
    }
  }

  private static class MyAutoScrollToSourceOptionProvider implements AutoScrollToSourceOptionProvider {
    public boolean isAutoScrollMode() {
      return UsageViewSettings.getInstance().IS_AUTOSCROLL_TO_SOURCE;
    }

    public void setAutoScrollMode(boolean state) {
      UsageViewSettings.getInstance().IS_AUTOSCROLL_TO_SOURCE = state;
    }
  }

  private final class ButtonPanel extends JPanel {
    public ButtonPanel() {
      super();

      setLayout(new FlowLayout(FlowLayout.LEFT, 8, 0));
    }

    public void add(int index, final Runnable runnable, String text, char mnemonic) {
      final JButton button = new JButton(text);

      button.setFocusable(false);
      button.addActionListener(new ActionListener() {
                                     public void actionPerformed(ActionEvent e) {
                                       runnable.run();
                                     }
                                   });

      button.setMnemonic(mnemonic);

      add(button, index);

      invalidate();
      if (getParent() != null) {
        getParent().validate();
      }
    }

    void update(AnActionEvent e) {
      for (int i = 0; i < getComponentCount(); ++i) {
        Component component = getComponent(i);
        if (component instanceof JButton) {
          final JButton button = (JButton)component;
          button.setEnabled(!isSearchInProgress());
        }
      }
    }
  }
}
