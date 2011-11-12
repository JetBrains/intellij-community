/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.errorTreeView;

import com.intellij.ide.*;
import com.intellij.ide.actions.*;
import com.intellij.ide.errorTreeView.impl.ErrorTreeViewConfiguration;
import com.intellij.ide.errorTreeView.impl.ErrorViewTextExporter;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.ui.AutoScrollToSourceHandler;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SideBorder;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.MessageView;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.ui.MutableErrorTreeView;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;

public class NewErrorTreeViewPanel extends JPanel implements DataProvider, OccurenceNavigator, MutableErrorTreeView, CopyProvider {
  protected static final Logger LOG = Logger.getInstance("#com.intellij.ide.errorTreeView.NewErrorTreeViewPanel");
  private String myProgressText = "";
  private final boolean myCreateExitAction;
  private ErrorViewStructure myErrorViewStructure;
  private ErrorViewTreeBuilder myBuilder;

  public interface ProcessController {
    void stopProcess();

    boolean isProcessStopped();
  }

  private ActionToolbar myLeftToolbar;
  private ActionToolbar myRightToolbar;
  private final TreeExpander myTreeExpander = new MyTreeExpander();
  private ExporterToTextFile myExporterToTextFile;
  protected Project myProject;
  private String myHelpId;
  protected Tree myTree;
  private JPanel myMessagePanel;
  private ProcessController myProcessController;

  private JLabel myProgressTextLabel;
  private JLabel myProgressStatisticsLabel;
  private JPanel myProgressPanel;

  private AutoScrollToSourceHandler myAutoScrollToSourceHandler;
  private MyOccurenceNavigatorSupport myOccurenceNavigatorSupport;

  public NewErrorTreeViewPanel(Project project, String helpId) {
    this(project, helpId, true);
  }

  public NewErrorTreeViewPanel(Project project, String helpId, boolean createExitAction) {
    this(project, helpId, createExitAction, true);
  }

  public NewErrorTreeViewPanel(Project project, String helpId, boolean createExitAction, boolean createToolbar) {
    this(project, helpId, createExitAction, createToolbar, null);
  }

  public NewErrorTreeViewPanel(Project project, String helpId, boolean createExitAction, boolean createToolbar, Runnable rerunAction) {
    myProject = project;
    myHelpId = helpId;
    myCreateExitAction = createExitAction;
    setLayout(new BorderLayout());

    myAutoScrollToSourceHandler = new AutoScrollToSourceHandler() {
      protected boolean isAutoScrollMode() {
        return ErrorTreeViewConfiguration.getInstance(myProject).isAutoscrollToSource();
      }

      protected void setAutoScrollMode(boolean state) {
        ErrorTreeViewConfiguration.getInstance(myProject).setAutoscrollToSource(state);
      }
    };

    myMessagePanel = new JPanel(new BorderLayout());

    myErrorViewStructure = new ErrorViewStructure(project, canHideWarnings());
    DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    root.setUserObject(myErrorViewStructure.createDescriptor(myErrorViewStructure.getRootElement(), null));
    final DefaultTreeModel treeModel = new DefaultTreeModel(root);
    myTree = new Tree(treeModel) {
      public void setRowHeight(int i) {
        super.setRowHeight(0);
        // this is needed in order to make UI calculate the height for each particular row
      }
    };
    myBuilder = new ErrorViewTreeBuilder(myTree, treeModel, myErrorViewStructure);

    myExporterToTextFile = new ErrorViewTextExporter(myErrorViewStructure);
    myOccurenceNavigatorSupport = new MyOccurenceNavigatorSupport(myTree);

    myAutoScrollToSourceHandler.install(myTree);
    TreeUtil.installActions(myTree);
    UIUtil.setLineStyleAngled(myTree);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.setLargeModel(true);

    JScrollPane scrollPane = NewErrorTreeRenderer.install(myTree);
    scrollPane.setBorder(IdeBorderFactory.createBorder(SideBorder.LEFT));
    myMessagePanel.add(scrollPane, BorderLayout.CENTER);

    if (createToolbar) {
      add(createToolbarPanel(rerunAction), BorderLayout.WEST);
    }

    add(myMessagePanel, BorderLayout.CENTER);

    myTree.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          navigateToSource(false);
        }
      }
    });

    myTree.addMouseListener(new PopupHandler() {
      public void invokePopup(Component comp, int x, int y) {
        popupInvoked(comp, x, y);
      }
    });

    EditSourceOnDoubleClickHandler.install(myTree);
  }

  public void dispose() {
    Disposer.dispose(myBuilder);
  }

  public void performCopy(DataContext dataContext) {
    final ErrorTreeNodeDescriptor descriptor = getSelectedNodeDescriptor();
    if (descriptor != null) {
      final String[] lines = descriptor.getElement().getText();
      CopyPasteManager.getInstance().setContents(new StringSelection(StringUtil.join(lines, "\n")));
    }
  }

  public boolean isCopyEnabled(DataContext dataContext) {
    return getSelectedNodeDescriptor() != null;
  }

  public boolean isCopyVisible(DataContext dataContext) {
    return true;
  }

  public Object getData(String dataId) {
    if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
      return this;
    }
    if (PlatformDataKeys.NAVIGATABLE.is(dataId)) {
      final NavigatableMessageElement selectedMessageElement = getSelectedMessageElement();
      return selectedMessageElement != null ? selectedMessageElement.getNavigatable() : null;
    }
    else if (PlatformDataKeys.HELP_ID.is(dataId)) {
      return myHelpId;
    }
    else if (PlatformDataKeys.TREE_EXPANDER.is(dataId)) {
      return myTreeExpander;
    }
    else if (PlatformDataKeys.EXPORTER_TO_TEXT_FILE.is(dataId)) {
      return myExporterToTextFile;
    }
    else if (CURRENT_EXCEPTION_DATA_KEY.is(dataId)) {
      NavigatableMessageElement selectedMessageElement = getSelectedMessageElement();
      return selectedMessageElement != null ? selectedMessageElement.getData() : null;
    }
    return null;
  }

  public void selectFirstMessage() {
    final ErrorTreeElement firstError = myErrorViewStructure.getFirstMessage(ErrorTreeElementKind.ERROR);
    if (firstError != null) {
      selectElement(firstError, new Runnable() {
        public void run() {
          if (shouldShowFirstErrorInEditor()) {
            navigateToSource(false);
          }
        }
      });
    }
    else {
      ErrorTreeElement firstWarning = myErrorViewStructure.getFirstMessage(ErrorTreeElementKind.WARNING);
      if (firstWarning == null) firstWarning = myErrorViewStructure.getFirstMessage(ErrorTreeElementKind.NOTE);

      if (firstWarning != null) {
        selectElement(firstWarning, null);
      }
      else {
        TreeUtil.selectFirstNode(myTree);
      }
    }
  }

  private void selectElement(final ErrorTreeElement element, final Runnable onDone) {
    myBuilder.select(element, onDone);
  }

  protected boolean shouldShowFirstErrorInEditor() {
    return false;
  }

  public void clearMessages() {
    myErrorViewStructure.clear();
    myBuilder.updateTree();
  }

  public void updateTree() {
    myBuilder.updateTree();
  }

  public void addMessage(int type, @NotNull String[] text, @Nullable VirtualFile file, int line, int column, @Nullable Object data) {
    addMessage(type, text, null, file, line, column, data);
  }

  @Override
  public void addMessage(int type,
                         @NotNull String[] text,
                         @Nullable VirtualFile underFileGroup,
                         @Nullable VirtualFile file,
                         int line,
                         int column,
                         @Nullable Object data) {
    myErrorViewStructure
      .addMessage(ErrorTreeElementKind.convertMessageFromCompilerErrorType(type), text, underFileGroup, file, line, column, data);
    myBuilder.updateTree();
  }

  public void addMessage(int type,
                         @NotNull String[] text,
                         @Nullable String groupName,
                         @NotNull Navigatable navigatable,
                         @Nullable String exportTextPrefix,
                         @Nullable String rendererTextPrefix,
                         @Nullable Object data) {
    myErrorViewStructure.addNavigatableMessage(groupName, navigatable, ErrorTreeElementKind.convertMessageFromCompilerErrorType(type), text,
                                               data,
                                               exportTextPrefix == null ? "" : exportTextPrefix,
                                               rendererTextPrefix == null ? "" : rendererTextPrefix,
                                               data instanceof VirtualFile ? (VirtualFile)data : null);
    myBuilder.updateTree();
  }

  public ErrorViewStructure getErrorViewStructure() {
    return myErrorViewStructure;
  }

  public static String createExportPrefix(int line) {
    return line < 0 ? "" : IdeBundle.message("errortree.prefix.line", line);
  }

  public static String createRendererPrefix(int line, int column) {
    if (line < 0) return "";
    if (column < 0) return "(" + line + ")";
    return "(" + line + ", " + column + ")";
  }

  @NotNull
  public JComponent getComponent() {
    return this;
  }

  private NavigatableMessageElement getSelectedMessageElement() {
    final ErrorTreeElement selectedElement = getSelectedErrorTreeElement();
    return selectedElement instanceof NavigatableMessageElement ? (NavigatableMessageElement)selectedElement : null;
  }

  public ErrorTreeElement getSelectedErrorTreeElement() {
    ErrorTreeNodeDescriptor treeNodeDescriptor = getSelectedNodeDescriptor();
    if (treeNodeDescriptor == null) return null;

    return treeNodeDescriptor.getElement();
  }

  public ErrorTreeNodeDescriptor getSelectedNodeDescriptor() {
    TreePath path = myTree.getSelectionPath();
    if (path == null) {
      return null;
    }
    DefaultMutableTreeNode lastPathNode = (DefaultMutableTreeNode)path.getLastPathComponent();
    Object userObject = lastPathNode.getUserObject();
    if (!(userObject instanceof ErrorTreeNodeDescriptor)) {
      return null;
    }
    return (ErrorTreeNodeDescriptor)userObject;
  }

  private void navigateToSource(final boolean focusEditor) {
    NavigatableMessageElement element = getSelectedMessageElement();
    if (element == null) {
      return;
    }
    final Navigatable navigatable = element.getNavigatable();
    if (navigatable.canNavigate()) {
      navigatable.navigate(focusEditor);
    }
  }

  public static String getQualifiedName(final VirtualFile file) {
    return file.getPresentableUrl();
  }

  private void popupInvoked(Component component, int x, int y) {
    final TreePath path = myTree.getLeadSelectionPath();
    if (path == null) {
      return;
    }
    DefaultActionGroup group = new DefaultActionGroup();
    if (getData(PlatformDataKeys.NAVIGATABLE.getName()) != null) {
      group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE));
    }
    group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_COPY));
    addExtraPopupMenuActions(group);

    ActionPopupMenu menu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.COMPILER_MESSAGES_POPUP, group);
    menu.getComponent().show(component, x, y);
  }

  protected void addExtraPopupMenuActions(DefaultActionGroup group) {
  }

  public void setProcessController(ProcessController controller) {
    myProcessController = controller;
  }

  public void stopProcess() {
    myProcessController.stopProcess();
  }

  public boolean canControlProcess() {
    return myProcessController != null;
  }

  public boolean isProcessStopped() {
    return myProcessController.isProcessStopped();
  }

  public void close() {
    MessageView messageView = MessageView.SERVICE.getInstance(myProject);
    Content content = messageView.getContentManager().getContent(this);
    if (content != null) {
      messageView.getContentManager().removeContent(content, true);
    }
  }

  public void setProgressStatistics(final String s) {
    initProgressPanel();
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        myProgressStatisticsLabel.setText(s);
      }
    });
  }

  public void setProgressText(final String s) {
    initProgressPanel();
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        myProgressText = s;
        myProgressTextLabel.setText(myProgressText);
      }
    });
  }

  public void setFraction(final double fraction) {
    initProgressPanel();
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        myProgressTextLabel.setText(myProgressText + " " + (int)(fraction * 100 + 0.5) + "%");
      }
    });
  }

  private void initProgressPanel() {
    if (myProgressPanel == null) {
      myProgressPanel = new JPanel(new GridLayout(1, 2));
      myProgressStatisticsLabel = new JLabel();
      myProgressPanel.add(myProgressStatisticsLabel);
      myProgressTextLabel = new JLabel();
      myProgressPanel.add(myProgressTextLabel);
      myMessagePanel.add(myProgressPanel, BorderLayout.SOUTH);
      myMessagePanel.validate();
    }
  }

  public void collapseAll() {
    TreeUtil.collapseAll(myTree, 2);
  }


  public void expandAll() {
    TreePath[] selectionPaths = myTree.getSelectionPaths();
    TreePath leadSelectionPath = myTree.getLeadSelectionPath();
    int row = 0;
    while (row < myTree.getRowCount()) {
      myTree.expandRow(row);
      row++;
    }

    if (selectionPaths != null) {
      // restore selection
      myTree.setSelectionPaths(selectionPaths);
    }
    if (leadSelectionPath != null) {
      // scroll to lead selection path
      myTree.scrollPathToVisible(leadSelectionPath);
    }
  }

  private JPanel createToolbarPanel(Runnable rerunAction) {
    AnAction closeMessageViewAction = new CloseTabToolbarAction() {
      public void actionPerformed(AnActionEvent e) {
        close();
      }
    };

    DefaultActionGroup leftUpdateableActionGroup = new DefaultActionGroup();
    if (rerunAction != null) {
      leftUpdateableActionGroup.add(new RerunAction(rerunAction, closeMessageViewAction));
    }

    leftUpdateableActionGroup.add(new StopAction());
    if (myCreateExitAction) {
      leftUpdateableActionGroup.add(closeMessageViewAction);
    }
    leftUpdateableActionGroup.add(new PreviousOccurenceToolbarAction(this));
    leftUpdateableActionGroup.add(new NextOccurenceToolbarAction(this));
    leftUpdateableActionGroup.add(new ExportToTextFileToolbarAction(myExporterToTextFile));
    leftUpdateableActionGroup.add(new ContextHelpAction(myHelpId));

    DefaultActionGroup rightUpdateableActionGroup = new DefaultActionGroup();
    fillRightToolbarGroup(rightUpdateableActionGroup);

    JPanel toolbarPanel = new JPanel(new GridLayout(1, 2));
    final ActionManager actionManager = ActionManager.getInstance();
    myLeftToolbar =
      actionManager.createActionToolbar(ActionPlaces.COMPILER_MESSAGES_TOOLBAR, leftUpdateableActionGroup, false);
    toolbarPanel.add(myLeftToolbar.getComponent());
    myRightToolbar =
      actionManager.createActionToolbar(ActionPlaces.COMPILER_MESSAGES_TOOLBAR, rightUpdateableActionGroup, false);
    toolbarPanel.add(myRightToolbar.getComponent());

    return toolbarPanel;
  }

  protected void fillRightToolbarGroup(DefaultActionGroup group) {
    group.add(CommonActionsManager.getInstance().createExpandAllAction(myTreeExpander, this));
    group.add(CommonActionsManager.getInstance().createCollapseAllAction(myTreeExpander, this));
    if (canHideWarnings()) {
      group.add(new HideWarningsAction());
    }
    group.add(myAutoScrollToSourceHandler.createToggleAction());
  }

  public OccurenceInfo goNextOccurence() {
    return myOccurenceNavigatorSupport.goNextOccurence();
  }

  public OccurenceInfo goPreviousOccurence() {
    return myOccurenceNavigatorSupport.goPreviousOccurence();
  }

  public boolean hasNextOccurence() {
    return myOccurenceNavigatorSupport.hasNextOccurence();
  }

  public boolean hasPreviousOccurence() {
    return myOccurenceNavigatorSupport.hasPreviousOccurence();
  }

  public String getNextOccurenceActionName() {
    return myOccurenceNavigatorSupport.getNextOccurenceActionName();
  }

  public String getPreviousOccurenceActionName() {
    return myOccurenceNavigatorSupport.getPreviousOccurenceActionName();
  }

  private class RerunAction extends AnAction {
    private final Runnable myRerunAction;
    private final AnAction myCloseAction;

    public RerunAction(@NotNull Runnable rerunAction, @NotNull AnAction closeAction) {
      super(IdeBundle.message("action.refresh"), null, IconLoader.getIcon("/actions/refreshUsages.png"));
      myRerunAction = rerunAction;
      myCloseAction = closeAction;
    }

    public void actionPerformed(AnActionEvent e) {
      myCloseAction.actionPerformed(e);
      myRerunAction.run();
    }

    public void update(AnActionEvent event) {
      final Presentation presentation = event.getPresentation();
      presentation.setEnabled(canControlProcess() && isProcessStopped());
    }
  }

  private class StopAction extends AnAction {
    public StopAction() {
      super(IdeBundle.message("action.stop"), null, IconLoader.getIcon("/actions/suspend.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      if (canControlProcess()) {
        stopProcess();
      }
      myLeftToolbar.updateActionsImmediately();
      myRightToolbar.updateActionsImmediately();
    }

    public void update(AnActionEvent event) {
      Presentation presentation = event.getPresentation();
      presentation.setEnabled(canControlProcess() && !isProcessStopped());
    }
  }

  protected boolean canHideWarnings() {
    return true;
  }

  private class HideWarningsAction extends ToggleAction {
    public HideWarningsAction() {
      super(IdeBundle.message("action.hide.warnings"), null, IconLoader.getIcon("/compiler/hideWarnings.png"));
    }

    public boolean isSelected(AnActionEvent event) {
      return ErrorTreeViewConfiguration.getInstance(myProject).isHideWarnings();
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      final ErrorTreeViewConfiguration configuration = ErrorTreeViewConfiguration.getInstance(myProject);
      final boolean hideWarnings = configuration.isHideWarnings();
      if (hideWarnings != flag) {
        configuration.setHideWarnings(flag);
        myBuilder.updateTree();
      }
    }
  }

  public boolean isHideWarnings() {
    return ErrorTreeViewConfiguration.getInstance(myProject).isHideWarnings();
  }

  private class MyTreeExpander implements TreeExpander {
    public void expandAll() {
      NewErrorTreeViewPanel.this.expandAll();
    }

    public boolean canExpand() {
      return true;
    }

    public void collapseAll() {
      NewErrorTreeViewPanel.this.collapseAll();
    }

    public boolean canCollapse() {
      return true;
    }
  }

  private static class MyOccurenceNavigatorSupport extends OccurenceNavigatorSupport {
    public MyOccurenceNavigatorSupport(final Tree tree) {
      super(tree);
    }

    protected Navigatable createDescriptorForNode(DefaultMutableTreeNode node) {
      Object userObject = node.getUserObject();
      if (!(userObject instanceof ErrorTreeNodeDescriptor)) {
        return null;
      }
      final ErrorTreeNodeDescriptor descriptor = (ErrorTreeNodeDescriptor)userObject;
      final ErrorTreeElement element = descriptor.getElement();
      if (element instanceof NavigatableMessageElement) {
        return ((NavigatableMessageElement)element).getNavigatable();
      }
      return null;
    }

    public String getNextOccurenceActionName() {
      return IdeBundle.message("action.next.message");
    }

    public String getPreviousOccurenceActionName() {
      return IdeBundle.message("action.previous.message");
    }
  }

  public List<Object> getGroupChildrenData(final String groupName) {
    return myErrorViewStructure.getGroupChildrenData(groupName);
  }

  public void removeGroup(final String name) {
    myErrorViewStructure.removeGroup(name);
  }

  public void addFixedHotfixGroup(String text, List<SimpleErrorData> children) {
    myErrorViewStructure.addFixedHotfixGroup(text, children);
  }

  public void addHotfixGroup(HotfixData hotfixData, List<SimpleErrorData> children) {
    myErrorViewStructure.addHotfixGroup(hotfixData, children, this);
  }

  public void reload() {
    myBuilder.updateTree();
  }
}
