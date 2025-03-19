// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow;

import com.intellij.codeInsight.daemon.impl.IntentionsUI;
import com.intellij.codeWithMe.ClientId;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ToggleOptionAction.Option;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.client.ClientProjectSession;
import com.intellij.openapi.client.ClientSessionsUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.pom.Navigatable;
import com.intellij.ui.*;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.preview.DescriptorPreview;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.RestoreSelectionListener;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import com.intellij.util.SingleAlarm;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static com.intellij.openapi.application.ModalityState.stateForComponent;
import static com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl.OPEN_IN_PREVIEW_TAB;
import static com.intellij.ui.ColorUtil.toHtmlColor;
import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static com.intellij.ui.scale.JBUIScale.scale;
import static com.intellij.util.ArrayUtil.getFirstElement;
import static com.intellij.util.OpenSourceUtil.navigate;
import static javax.swing.tree.TreeSelectionModel.SINGLE_TREE_SELECTION;

public class ProblemsViewPanel extends OnePixelSplitter implements Disposable, UiCompatibleDataProvider, ProblemsViewTab {
  private final ClientProjectSession mySession;
  volatile boolean myDisposed;
  private final String myId;
  private final ProblemsViewState myState;
  private final Supplier<@NlsContexts.TabTitle String> myName;
  private final ProblemsTreeModel myTreeModel = new ProblemsTreeModel(this);
  protected final DescriptorPreview myPreview;
  private final JPanel myPanel;
  protected final ActionToolbar myToolbar;
  private final @NotNull JScrollPane myScrollPane;
  private final Insets myToolbarInsets = JBUI.insetsRight(1);
  private final Tree myTree;
  private final TreeExpander myTreeExpander;
  private final AtomicReference<Long> myShowTime = new AtomicReference<>();
  private final SingleAlarm mySelectionAlarm = new SingleAlarm(() -> {
    ProblemNode node = TreeUtil.getLastUserObject(ProblemNode.class, getTree().getSelectionPath());
    if (node != null) ProblemsViewStatsCollector.problemSelected(this, node.getProblem());
    updateAutoscroll();
    updatePreview();
  }, 50, this, stateForComponent(this));

  private final SingleAlarm myUpdateAlarm = new SingleAlarm(() -> {
    ToolWindow window = getCurrentToolWindow();
    if (window == null) return;
    Content content = getCurrentContent();
    if (content == null) return;

    Root root = myTreeModel.getRoot();
    int count = root == null ? 0 : root.getProblemCount();
    content.setDisplayName(getName(count));
    ProblemsViewIconUpdater.update(getProject());
  }, 50, this, stateForComponent(this));

  private final Option myAutoscrollToSource = new Option() {
    @Override
    public boolean isSelected() {
      return myState.getAutoscrollToSource();
    }

    @Override
    public void setSelected(boolean selected) {
      myState.setAutoscrollToSource(selected);
      if (selected) updateAutoscroll();
    }
  };
  private final Option myOpenInPreviewTab = new Option() {
    @Override
    public boolean isSelected() {
      return UISettings.getInstance().getOpenInPreviewTabIfPossible();
    }

    @Override
    public void setSelected(boolean selected) {
      UISettings.getInstance().setOpenInPreviewTabIfPossible(selected);
      if (selected) updateAutoscroll();
    }
  };
  private final Option myShowPreview = new Option() {
    @Override
    public boolean isEnabled() {
      VirtualFile file = getSelectedFile();
      return file != null && file.isValid() && ProblemsView.getDocument(getProject(), file) != null;
    }

    @Override
    public boolean isAlwaysVisible() {
      return true;
    }

    @Override
    public boolean isSelected() {
      return myState.getShowPreview();
    }

    @Override
    public void setSelected(boolean selected) {
      myState.setShowPreview(selected);
      updatePreview();
    }
  };
  private final Option myGroupByToolId = new Option() {
    @Override
    public boolean isSelected() {
      return myState.getGroupByToolId();
    }

    @Override
    public void setSelected(boolean selected) {
      myState.setGroupByToolId(selected);
      myTreeModel.structureChanged(null);
    }
  };
  @SuppressWarnings("unused")
  private final Option mySortFoldersFirst = new Option() {
    @Override
    public boolean isSelected() {
      return myState.getSortFoldersFirst();
    }

    @Override
    public void setSelected(boolean selected) {
      myState.setSortFoldersFirst(selected);
      myTreeModel.setComparator(createComparator());
    }
  };
  protected final Option mySortBySeverity = new Option() {
    @Override
    public boolean isSelected() {
      return myState.getSortBySeverity();
    }

    @Override
    public void setSelected(boolean selected) {
      myState.setSortBySeverity(selected);
      myTreeModel.setComparator(createComparator());
    }
  };
  private final Option mySortByName = new Option() {
    @Override
    public boolean isSelected() {
      return myState.getSortByName();
    }

    @Override
    public void setSelected(boolean selected) {
      myState.setSortByName(selected);
      myTreeModel.setComparator(createComparator());
    }
  };

  public ProblemsViewPanel(@NotNull Project project,
                           @NotNull String id,
                           @NotNull ProblemsViewState state,
                           @NotNull Supplier<String> name) {
    super(false, .5f, .1f, .9f);
    mySession = ClientSessionsUtil.getCurrentSession(project);
    myPreview = new DescriptorPreview(this, true, mySession);

    this.myId = id;
    myState = state;
    myName = name;

    myTreeModel.setComparator(createComparator());
    myTree = new Tree(new AsyncTreeModel(myTreeModel, this));
    myTree.setRootVisible(false);
    myTree.getSelectionModel().setSelectionMode(SINGLE_TREE_SELECTION);
    myTree.addTreeSelectionListener(new RestoreSelectionListener());
    myTree.addTreeSelectionListener(event -> mySelectionAlarm.cancelAndRequest());
    TreeUIHelper.getInstance().installTreeSpeedSearch(myTree);
    EditSourceOnDoubleClickHandler.install(myTree);
    EditSourceOnEnterKeyHandler.install(myTree);
    PopupHandler.installPopupMenu(myTree, getPopupHandlerGroupId(), ActionPlaces.PROBLEMS_VIEW_POPUP);
    myTreeExpander = new DefaultTreeExpander(myTree);

    JComponent centerComponent = createCenterComponent();
    myToolbar = createToolbar();
    myToolbar.setTargetComponent(centerComponent);
    myToolbar.getComponent().setVisible(state.getShowToolbar());
    myPanel = new JPanel(new BorderLayout());
    myScrollPane = createScrollPane(centerComponent, true);
    if (ExperimentalUI.isNewUI()) {
      updateBorders();
    }
    else {
      UIUtil.addBorder(myToolbar.getComponent(), new CustomLineBorder(myToolbarInsets));
    }

    myPanel.add(BorderLayout.CENTER, myScrollPane);
    myPanel.add(BorderLayout.WEST, myToolbar.getComponent());
    myPanel.putClientProperty(OPEN_IN_PREVIEW_TAB, true);
    setFirstComponent(myPanel);
  }

  private void updateBorders() {
    if (!ExperimentalUI.isNewUI()) {
      return;
    }
    int orientation = myToolbar.getOrientation();
    Insets i = orientation == SwingConstants.VERTICAL ? JBUI.CurrentTheme.Toolbar.verticalToolbarInsets()
                                                      : JBUI.CurrentTheme.Toolbar.horizontalToolbarInsets();
    Border border = i != null ? JBUI.Borders.empty(i) : JBUI.Borders.empty(2);
    myToolbar.getComponent().setBorder(border);
    ScrollableContentBorder.setup(myScrollPane, orientation == SwingConstants.VERTICAL ? Side.LEFT : Side.TOP);
  }

  @Override
  public void dispose() {
    visibilityChangedTo(false);
    myPreview.close();
    myDisposed = true;
  }

  private @Nullable Content getCurrentContent() {
    ToolWindow window = getCurrentToolWindow();
    if (window == null) {
      return null;
    }
    ContentManager manager = window.getContentManagerIfCreated();
    if (manager == null) {
      return null;
    }
    return manager.getContent(this);
  }

  private ToolWindow getCurrentToolWindow() {
    return ProblemsView.getToolWindow(getProject());
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(CommonDataKeys.PROJECT, getProject());
    sink.set(PlatformDataKeys.TREE_EXPANDER, getTreeExpander());
    sink.set(PlatformDataKeys.TREE_EXPANDER_HIDE_ACTIONS_IF_NO_EXPANDER, shouldHideExpandCollapseActionsIfThereIsNoTreeExpander());

    Node node = getSelectedNode();
    // this code allows performing Editor's Undo action from the Problems View
    Editor editor = getPreview();
    if (editor != null) {
      sink.set(PlatformCoreDataKeys.FILE_EDITOR,
               TextEditorProvider.getInstance().getTextEditor(editor));
    }
    else {
      VirtualFile file = node == null ? null : node.getVirtualFile();
      sink.set(PlatformCoreDataKeys.FILE_EDITOR,
               file == null ? null : getFirstElement(FileEditorManager.getInstance(mySession.getProject()).getEditors(file)));
    }
    if (node == null) return;
    VirtualFile file = node.getVirtualFile();

    sink.set(PlatformCoreDataKeys.SELECTED_ITEM, node);
    sink.set(CommonDataKeys.VIRTUAL_FILE, node.getVirtualFile());
    sink.set(CommonDataKeys.VIRTUAL_FILE_ARRAY, file == null ? null : new VirtualFile[]{file});

    sink.lazy(CommonDataKeys.NAVIGATABLE, () -> node.getNavigatable());
    sink.lazy(CommonDataKeys.NAVIGATABLE_ARRAY, () -> {
      Navigatable navigatable = node.getNavigatable();
      return navigatable == null ? null : new Navigatable[]{navigatable};
    });
  }

  protected void updateToolWindowContent() {
    myUpdateAlarm.cancelAndRequest();
  }

  protected @NotNull JComponent createCenterComponent() {
    return myTree;
  }

  @Override
  public @NotNull @NlsContexts.TabTitle String getName(int count) {
    String name = myName.get();
    String padding = String.valueOf(count <= 0 ? 0 : JBUI.scale(8));
    String fg = toHtmlColor(NamedColorUtil.getInactiveTextColor());
    String number = count <= 0 ? "" : String.valueOf(count);
    @Language("HTML")
    @NonNls String labelWithCounter = """
      <html>
        <body>
          <table cellpadding='0' cellspacing='0'>
            <tr>
              <td><nobr>%s</nobr></td>
              <td width='%s'></td>
              <td><font color='%s'>%s</font></td>
            </tr>
          </table>
        </body>
      </html>
      """;
    return String.format(labelWithCounter, name, padding, fg, number);
  }

  @Override
  protected void loadProportion() {
    if (myState != null) setProportion(myState.getProportion());
  }

  @Override
  protected void saveProportion() {
    if (myState != null) myState.setProportion(getProportion());
  }

  public final @NotNull Project getProject() {
    return mySession.getProject();
  }

  public final @NotNull ClientProjectSession getSession() {
    return mySession;
  }

  public final @NotNull ProblemsViewState getState() {
    return myState;
  }

  public final @NotNull ProblemsTreeModel getTreeModel() {
    return myTreeModel;
  }

  public final @NotNull Tree getTree() {
    return myTree;
  }

  final @Nullable Editor getPreview() {
    return myPreview.editor();
  }

  @Nullable TreeExpander getTreeExpander() {
    return myTreeExpander;
  }

  Boolean shouldHideExpandCollapseActionsIfThereIsNoTreeExpander() {
    return true;
  }

  @Override
  public void orientationChangedTo(boolean vertical) {
    setOrientation(vertical);
    myPanel.remove(myToolbar.getComponent());
    myToolbar.setOrientation(vertical ? SwingConstants.HORIZONTAL : SwingConstants.VERTICAL);
    myToolbarInsets.right = !vertical ? scale(1) : 0;
    myToolbarInsets.bottom = vertical ? scale(1) : 0;
    myPanel.add(vertical ? BorderLayout.NORTH : BorderLayout.WEST, myToolbar.getComponent());
    updateBorders();
    updatePreview();
  }

  @Override
  public void selectionChangedTo(boolean selected) {
    if (selected) {
      myTreeModel.setComparator(createComparator());
      updatePreview();

      ToolWindow window = ProblemsView.getToolWindow(getProject());
      if (window instanceof ToolWindowEx) {
        ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction("ProblemsView.ToolWindow.SecondaryActions");
        window.setAdditionalGearActions(group);
      }
    }
    visibilityChangedTo(selected);
  }

  @Override
  public void visibilityChangedTo(boolean visible) {
    if (visible) {
      myShowTime.set(System.nanoTime());
      ProblemsViewStatsCollector.tabShown(this);
    }
    else {
      Long time = myShowTime.getAndSet(null);
      if (time != null) {
        ProblemsViewStatsCollector.tabHidden(this, System.nanoTime() - time);
      }
      IntentionsUI intentionUI = getProject().getServiceIfCreated(IntentionsUI.class);
      if (intentionUI != null) {
        intentionUI.hide();
      }
    }
  }

  @Override
  public @NotNull @NonNls String getTabId() {
    return myId;
  }

  private static @Nullable Node getNode(@Nullable TreePath path) {
    return TreeUtil.getLastUserObject(Node.class, path);
  }

  private @Nullable Node getSelectedNode() {
    return getNode(getTree().getSelectionPath());
  }

  @Nullable VirtualFile getSelectedFile() {
    Node node = getSelectedNode();
    return node == null ? null : node.getVirtualFile();
  }

  private boolean isActiveTab() {
    ToolWindow window = ProblemsView.getToolWindow(getProject());
    if (window == null || !window.isActive()) return false;
    Content content = window.getContentManager().getSelectedContent();
    if (content == null) return false;
    return SwingUtilities.isDescendingFrom(this, content.getComponent());
  }

  private void updateAutoscroll() {
    if (isActiveTab() && (isNotNullAndSelected(getAutoscrollToSource()) || isNotNullAndSelected(getOpenInPreviewTab()))) {
      invokeLater(() -> {
        Node node = getSelectedNode();
        Navigatable navigatable = node == null ? null : node.getNavigatable();
        if (navigatable != null && navigatable.canNavigateToSource()) {
          try (AccessToken ignored = ClientId.withClientId(mySession.getClientId())) {
            navigate(false, navigatable);
          }
        }
      });
    }
  }

  protected void updatePreview() {
    Node node = isNotNullAndSelected(getShowPreview()) ? getSelectedNode() : null;
    myPreview.open(node == null ? null : node.getDescriptor());
  }

  private void invokeLater(@NotNull Runnable runnable) {
    getApplication().invokeLater(runnable, stateForComponent(this));
  }

  protected String getPopupHandlerGroupId() {
    return "ProblemsView.ToolWindow.TreePopup";
  }

  protected String getToolbarActionGroupId() {
    return "ProblemsView.ToolWindow.Toolbar";
  }

  protected @NotNull ActionToolbar createToolbar() {
    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(getToolbarActionGroupId());
    return ActionManager.getInstance().createActionToolbar(ActionPlaces.PROBLEMS_VIEW_TOOLBAR, group, false);
  }

  protected @NotNull Comparator<Node> createComparator() {
    return new ProblemsViewNodeComparator(
      isNullableOrSelected(getSortFoldersFirst()),
      isNullableOrSelected(getSortBySeverity()),
      isNotNullAndSelected(getSortByName()));
  }

  @Nullable
  Option getAutoscrollToSource() {
    return isNotNullAndSelected(getShowPreview()) ? null : myAutoscrollToSource;
  }

  @Nullable
  Option getOpenInPreviewTab() {
    return isNotNullAndSelected(getShowPreview()) ? null : myOpenInPreviewTab;
  }

  public @Nullable Option getShowPreview() {
    return myShowPreview;
  }

  @Nullable
  Option getGroupByToolId() {
    return this instanceof HighlightingPanel ? myGroupByToolId : null;
  }

  @Nullable
  Option getSortFoldersFirst() {
    return null; // TODO:malenkov - support file hierarchy & mySortFoldersFirst;
  }

  protected @Nullable Option getSortBySeverity() {
    return null;
  }

  protected @Nullable Option getSortByName() {
    return mySortByName;
  }

  private static boolean isNotNullAndSelected(@Nullable Option option) {
    return option != null && option.isSelected();
  }

  protected static boolean isNullableOrSelected(@Nullable Option option) {
    return option == null || option.isSelected();
  }
}