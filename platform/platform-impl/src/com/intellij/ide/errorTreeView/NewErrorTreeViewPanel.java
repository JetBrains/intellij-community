/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.icons.AllIcons;
import com.intellij.ide.*;
import com.intellij.ide.actions.*;
import com.intellij.ide.errorTreeView.impl.ErrorTreeViewConfiguration;
import com.intellij.ide.errorTreeView.impl.ErrorViewTextExporter;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
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
import com.intellij.util.Alarm;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.MutableErrorTreeView;
import com.intellij.util.ui.StatusText;
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
import java.util.Collections;
import java.util.List;

public class NewErrorTreeViewPanel extends JPanel implements DataProvider, OccurenceNavigator, MutableErrorTreeView, CopyProvider {
  protected static final Logger LOG = Logger.getInstance("#com.intellij.ide.errorTreeView.NewErrorTreeViewPanel");
  private volatile String myProgressText = "";
  private volatile float myFraction;
  private final boolean myCreateExitAction;
  private final ErrorViewStructure myErrorViewStructure;
  private final ErrorViewTreeBuilder myBuilder;
  private final Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private volatile boolean myIsDisposed;
  private final ErrorTreeViewConfiguration myConfiguration;

  public interface ProcessController {
    void stopProcess();

    boolean isProcessStopped();
  }

  private ActionToolbar myLeftToolbar;
  private ActionToolbar myRightToolbar;
  private final TreeExpander myTreeExpander = new MyTreeExpander();
  private final ExporterToTextFile myExporterToTextFile;
  protected Project myProject;
  private final String myHelpId;
  protected Tree myTree;
  private final JPanel myMessagePanel;
  private ProcessController myProcessController;

  private JLabel myProgressLabel;
  private JPanel myProgressPanel;

  private final AutoScrollToSourceHandler myAutoScrollToSourceHandler;
  private final MyOccurrenceNavigatorSupport myOccurrenceNavigatorSupport;

  public NewErrorTreeViewPanel(Project project, String helpId) {
    this(project, helpId, true);
  }

  public NewErrorTreeViewPanel(Project project, String helpId, boolean createExitAction) {
    this(project, helpId, createExitAction, true);
  }

  public NewErrorTreeViewPanel(Project project, String helpId, boolean createExitAction, boolean createToolbar) {
    this(project, helpId, createExitAction, createToolbar, null);
  }

  public NewErrorTreeViewPanel(Project project, String helpId, boolean createExitAction, boolean createToolbar, @Nullable Runnable rerunAction) {
    myProject = project;
    myHelpId = helpId;
    myCreateExitAction = createExitAction;
    myConfiguration = ErrorTreeViewConfiguration.getInstance(project);
    setLayout(new BorderLayout());

    myAutoScrollToSourceHandler = new AutoScrollToSourceHandler() {
      @Override
      protected boolean isAutoScrollMode() {
        return myConfiguration.isAutoscrollToSource();
      }

      @Override
      protected void setAutoScrollMode(boolean state) {
        myConfiguration.setAutoscrollToSource(state);
      }
    };

    myMessagePanel = new JPanel(new BorderLayout());

    myErrorViewStructure = createErrorViewStructure(project, canHideWarnings());
    DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    root.setUserObject(myErrorViewStructure.createDescriptor(myErrorViewStructure.getRootElement(), null));
    final DefaultTreeModel treeModel = new DefaultTreeModel(root);
    myTree = createTree(treeModel);
    myTree.getEmptyText().setText(IdeBundle.message("errortree.noMessages"));
    myBuilder = new ErrorViewTreeBuilder(myTree, treeModel, myErrorViewStructure);

    myExporterToTextFile = new ErrorViewTextExporter(myErrorViewStructure);
    myOccurrenceNavigatorSupport = new MyOccurrenceNavigatorSupport(myTree);

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
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          navigateToSource(false);
        }
      }
    });

    myTree.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        popupInvoked(comp, x, y);
      }
    });

    EditSourceOnDoubleClickHandler.install(myTree);
  }

  @NotNull
  protected Tree createTree(@NotNull final DefaultTreeModel treeModel) {
    return new Tree(treeModel) {
      @Override
      public void setRowHeight(int i) {
        super.setRowHeight(0);
        // this is needed in order to make UI calculate the height for each particular row
      }
    };
  }

  protected ErrorViewStructure createErrorViewStructure(Project project, boolean canHideWarnings) {
    return new ErrorViewStructure(project, canHideWarnings);
  }

  @Override
  public void dispose() {
    myIsDisposed = true;
    myErrorViewStructure.clear();
    myUpdateAlarm.cancelAllRequests();
    Disposer.dispose(myUpdateAlarm);
    Disposer.dispose(myBuilder);
  }

  @Override
  public void performCopy(@NotNull DataContext dataContext) {
    List<ErrorTreeNodeDescriptor> descriptors = getSelectedNodeDescriptors();
    if (!descriptors.isEmpty()) {
      CopyPasteManager.getInstance().setContents(new StringSelection(StringUtil.join(descriptors, descriptor -> {
        ErrorTreeElement element = descriptor.getElement();
        return NewErrorTreeRenderer.calcPrefix(element) + StringUtil.join(element.getText(), "\n");
      }, "\n")));
    }
  }

  @Override
  public boolean isCopyEnabled(@NotNull DataContext dataContext) {
    return !getSelectedNodeDescriptors().isEmpty();
  }

  @Override
  public boolean isCopyVisible(@NotNull DataContext dataContext) {
    return true;
  }
  
  @NotNull public StatusText getEmptyText() {
    return myTree.getEmptyText();
  } 

  @Override
  public Object getData(String dataId) {
    if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
      return this;
    }
    if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
      final NavigatableErrorTreeElement selectedMessageElement = getSelectedNavigatableElement();
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
      ErrorTreeElement selectedMessageElement = getSelectedErrorTreeElement();
      return selectedMessageElement != null ? selectedMessageElement.getData() : null;
    }
    return null;
  }

  public void selectFirstMessage() {
    final ErrorTreeElement firstError = myErrorViewStructure.getFirstMessage(ErrorTreeElementKind.ERROR);
    if (firstError != null) {
      selectElement(firstError, () -> {
        if (shouldShowFirstErrorInEditor()) {
          TransactionGuard.submitTransaction(myBuilder, () -> navigateToSource(false));
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

  public void updateTree() {
    if (!myIsDisposed) {
      myBuilder.updateTree();
    }
  }

  @Override
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
    if (myIsDisposed) {
      return;
    }
    myErrorViewStructure.addMessage(ErrorTreeElementKind.convertMessageFromCompilerErrorType(type), text, underFileGroup, file, line, column, data);
    myBuilder.updateTree();
  }

  @Override
  public void addMessage(int type,
                         @NotNull String[] text,
                         @Nullable String groupName,
                         @NotNull Navigatable navigatable,
                         @Nullable String exportTextPrefix,
                         @Nullable String rendererTextPrefix,
                         @Nullable Object data) {
    if (myIsDisposed) {
      return;
    }
    VirtualFile file = data instanceof VirtualFile ? (VirtualFile)data : null;
    if (file == null && navigatable instanceof OpenFileDescriptor) {
      file = ((OpenFileDescriptor)navigatable).getFile();
    }
    final String exportPrefix = exportTextPrefix == null ? "" : exportTextPrefix;
    final String renderPrefix = rendererTextPrefix == null ? "" : rendererTextPrefix;
    final ErrorTreeElementKind kind = ErrorTreeElementKind.convertMessageFromCompilerErrorType(type);
    myErrorViewStructure.addNavigatableMessage(groupName, navigatable, kind, text, data, exportPrefix, renderPrefix, file);
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

  @Override
  @NotNull
  public JComponent getComponent() {
    return this;
  }

  @Nullable
  private NavigatableErrorTreeElement getSelectedNavigatableElement() {
    final ErrorTreeElement selectedElement = getSelectedErrorTreeElement();
    return selectedElement instanceof NavigatableErrorTreeElement ? (NavigatableErrorTreeElement)selectedElement : null;
  }

  @Nullable
  public ErrorTreeElement getSelectedErrorTreeElement() {
    final ErrorTreeNodeDescriptor treeNodeDescriptor = getSelectedNodeDescriptor();
    return treeNodeDescriptor == null? null : treeNodeDescriptor.getElement();
  }

  @Nullable
  public ErrorTreeNodeDescriptor getSelectedNodeDescriptor() {
    List<ErrorTreeNodeDescriptor> descriptors = getSelectedNodeDescriptors();
    return descriptors.size() == 1 ? descriptors.get(0) : null;
  }

  private List<ErrorTreeNodeDescriptor> getSelectedNodeDescriptors() {
    TreePath[] paths = myTree.getSelectionPaths();
    if (paths == null) {
      return Collections.emptyList();
    }
    List<ErrorTreeNodeDescriptor> result = ContainerUtil.newArrayList();
    for (TreePath path : paths) {
      DefaultMutableTreeNode lastPathNode = (DefaultMutableTreeNode)path.getLastPathComponent();
      Object userObject = lastPathNode.getUserObject();
      if (userObject instanceof ErrorTreeNodeDescriptor) {
        result.add((ErrorTreeNodeDescriptor)userObject);
      }
    }
    return result;
  }

  private void navigateToSource(final boolean focusEditor) {
    NavigatableErrorTreeElement element = getSelectedNavigatableElement();
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
    if (getData(CommonDataKeys.NAVIGATABLE.getName()) != null) {
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
  
  public void setProgress(final String s, float fraction) {
    initProgressPanel();
    myProgressText = s;
    myFraction = fraction;
    updateProgress();
  }

  public void setProgressText(final String s) {
    initProgressPanel();
    myProgressText = s;
    updateProgress();
  }
  
  public void setFraction(final float fraction) {
    initProgressPanel();
    myFraction = fraction;
    updateProgress();
  }

  public void clearProgressData() {
    if (myProgressPanel != null) {
      myProgressText = " ";
      myFraction = 0.0f;
      updateProgress();
    }
  }

  private void updateProgress() {
    if (myIsDisposed) {
      return;
    }
    myUpdateAlarm.cancelAllRequests();
    myUpdateAlarm.addRequest(() -> {
      final float fraction = myFraction;
      final String text = myProgressText;
      if (fraction > 0.0f) {
        myProgressLabel.setText((int)(fraction * 100 + 0.5) + "%  " + text);
      }
      else {
        myProgressLabel.setText(text);
      }
    }, 50, ModalityState.NON_MODAL);

  }

  
  private void initProgressPanel() {
    if (myProgressPanel == null) {
      myProgressPanel = new JPanel(new GridLayout(1, 2));
      myProgressLabel = new JLabel();
      myProgressPanel.add(myProgressLabel);
      //JLabel secondLabel = new JLabel();
      //myProgressPanel.add(secondLabel);
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

  private JPanel createToolbarPanel(@Nullable Runnable rerunAction) {
    AnAction closeMessageViewAction = new CloseTabToolbarAction() {
      @Override
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

    JPanel toolbarPanel = new JPanel(new BorderLayout());
    ActionManager actionManager = ActionManager.getInstance();
    myLeftToolbar = actionManager.createActionToolbar(ActionPlaces.COMPILER_MESSAGES_TOOLBAR, leftUpdateableActionGroup, false);
    myRightToolbar = actionManager.createActionToolbar(ActionPlaces.COMPILER_MESSAGES_TOOLBAR, rightUpdateableActionGroup, false);
    toolbarPanel.add(myLeftToolbar.getComponent(), BorderLayout.WEST);
    toolbarPanel.add(myRightToolbar.getComponent(), BorderLayout.CENTER);

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

  @Override
  public OccurenceInfo goNextOccurence() {
    return myOccurrenceNavigatorSupport.goNextOccurence();
  }

  @Override
  public OccurenceInfo goPreviousOccurence() {
    return myOccurrenceNavigatorSupport.goPreviousOccurence();
  }

  @Override
  public boolean hasNextOccurence() {
    return myOccurrenceNavigatorSupport.hasNextOccurence();
  }

  @Override
  public boolean hasPreviousOccurence() {
    return myOccurrenceNavigatorSupport.hasPreviousOccurence();
  }

  @Override
  public String getNextOccurenceActionName() {
    return myOccurrenceNavigatorSupport.getNextOccurenceActionName();
  }

  @Override
  public String getPreviousOccurenceActionName() {
    return myOccurrenceNavigatorSupport.getPreviousOccurenceActionName();
  }

  private class RerunAction extends DumbAwareAction {
    private final Runnable myRerunAction;
    private final AnAction myCloseAction;

    public RerunAction(@NotNull Runnable rerunAction, @NotNull AnAction closeAction) {
      super(IdeBundle.message("action.refresh"), null, AllIcons.Actions.Rerun);
      myRerunAction = rerunAction;
      myCloseAction = closeAction;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myCloseAction.actionPerformed(e);
      myRerunAction.run();
    }

    @Override
    public void update(AnActionEvent event) {
      final Presentation presentation = event.getPresentation();
      presentation.setEnabled(canControlProcess() && isProcessStopped());
    }
  }

  private class StopAction extends DumbAwareAction {
    public StopAction() {
      super(IdeBundle.message("action.stop"), null, AllIcons.Actions.Suspend);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      if (canControlProcess()) {
        stopProcess();
      }
      myLeftToolbar.updateActionsImmediately();
      myRightToolbar.updateActionsImmediately();
    }

    @Override
    public void update(AnActionEvent event) {
      Presentation presentation = event.getPresentation();
      presentation.setEnabled(canControlProcess() && !isProcessStopped());
      presentation.setVisible(canControlProcess());
    }
  }

  protected boolean canHideWarnings() {
    return true;
  }

  private class HideWarningsAction extends ToggleAction implements DumbAware {
    public HideWarningsAction() {
      super(IdeBundle.message("action.hide.warnings"), null, AllIcons.General.HideWarnings);
    }

    @Override
    public boolean isSelected(AnActionEvent event) {
      return isHideWarnings();
    }

    @Override
    public void setSelected(AnActionEvent event, boolean flag) {
      if (isHideWarnings() != flag) {
        myConfiguration.setHideWarnings(flag);
        myBuilder.updateTree();
      }
    }
  }

  public boolean isHideWarnings() {
    return myConfiguration.isHideWarnings();
  }

  private class MyTreeExpander implements TreeExpander {
    @Override
    public void expandAll() {
      NewErrorTreeViewPanel.this.expandAll();
    }

    @Override
    public boolean canExpand() {
      return true;
    }

    @Override
    public void collapseAll() {
      NewErrorTreeViewPanel.this.collapseAll();
    }

    @Override
    public boolean canCollapse() {
      return true;
    }
  }

  private static class MyOccurrenceNavigatorSupport extends OccurenceNavigatorSupport {
    public MyOccurrenceNavigatorSupport(final Tree tree) {
      super(tree);
    }

    @Override
    protected Navigatable createDescriptorForNode(DefaultMutableTreeNode node) {
      Object userObject = node.getUserObject();
      if (!(userObject instanceof ErrorTreeNodeDescriptor)) {
        return null;
      }
      final ErrorTreeNodeDescriptor descriptor = (ErrorTreeNodeDescriptor)userObject;
      final ErrorTreeElement element = descriptor.getElement();
      if (element instanceof NavigatableErrorTreeElement) {
        return ((NavigatableErrorTreeElement)element).getNavigatable();
      }
      return null;
    }

    @Override
    public String getNextOccurenceActionName() {
      return IdeBundle.message("action.next.message");
    }

    @Override
    public String getPreviousOccurenceActionName() {
      return IdeBundle.message("action.previous.message");
    }
  }

  @Override
  public List<Object> getGroupChildrenData(final String groupName) {
    return myErrorViewStructure.getGroupChildrenData(groupName);
  }

  @Override
  public void removeGroup(final String name) {
    myErrorViewStructure.removeGroup(name);
  }

  @Override
  public void addFixedHotfixGroup(String text, List<SimpleErrorData> children) {
    myErrorViewStructure.addFixedHotfixGroup(text, children);
  }

  @Override
  public void addHotfixGroup(HotfixData hotfixData, List<SimpleErrorData> children) {
    myErrorViewStructure.addHotfixGroup(hotfixData, children, this);
  }

  @Override
  public void reload() {
    myBuilder.updateTree();
  }
}
