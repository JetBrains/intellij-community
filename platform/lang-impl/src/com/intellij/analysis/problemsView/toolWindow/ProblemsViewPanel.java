// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView.toolWindow;

import com.intellij.codeInsight.daemon.impl.IntentionsUI;
import com.intellij.codeWithMe.ClientId;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.TreeExpander;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ToggleOptionAction.Option;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorActivityManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.pom.Navigatable;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.RestoreSelectionListener;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import com.intellij.util.SingleAlarm;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static com.intellij.openapi.application.ModalityState.stateForComponent;
import static com.intellij.ui.ColorUtil.toHtmlColor;
import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static com.intellij.ui.scale.JBUIScale.scale;
import static com.intellij.util.OpenSourceUtil.navigate;
import static javax.swing.tree.TreeSelectionModel.SINGLE_TREE_SELECTION;

class ProblemsViewPanel extends OnePixelSplitter implements Disposable, DataProvider {
  protected final ClientId myClientId = ClientId.getCurrent();

  private final Project myProject;
  private final ProblemsViewState myState;
  private final Supplier<@NlsContexts.TabTitle String> myName;
  private final ProblemsTreeModel myTreeModel = new ProblemsTreeModel(this);
  private final ProblemsViewPreview myPreview = new ProblemsViewPreview(this);
  private final JPanel myPanel;
  private final ActionToolbar myToolbar;
  private final Insets myToolbarInsets = JBUI.insetsRight(1);
  private final Tree myTree;
  private final TreeExpander myTreeExpander;
  private final AtomicReference<Long> myShowTime = new AtomicReference<>();
  private final SingleAlarm mySelectionAlarm = new SingleAlarm(() -> {
    ProblemNode node = TreeUtil.getLastUserObject(ProblemNode.class, getTree().getSelectionPath());
    if (node != null) ProblemsViewStatsCollector.problemSelected(this, node.getProblem());
    updateAutoscroll();
    updatePreview();
  }, 50, stateForComponent(this), this);
  private final SingleAlarm myUpdateAlarm = new SingleAlarm(() -> {
    ToolWindow window = ProblemsView.getToolWindow(getProject());
    if (window == null) return;
    ContentManager manager = window.getContentManagerIfCreated();
    if (manager == null) return;
    Content content = manager.getContent(this);
    if (content == null) return;

    Root root = myTreeModel.getRoot();
    int count = root == null ? 0 : root.getProblemCount();
    content.setDisplayName(getName(count));
    Icon icon = getToolWindowIcon(count);
    if (icon != null) window.setIcon(icon);
  }, 50, stateForComponent(this), this);

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
  private final Option myShowPreview = new Option() {
    @Override
    public boolean isEnabled() {
      VirtualFile file = getSelectedFile();
      return file != null && null != ProblemsView.getDocument(getProject(), file);
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
  private final Option mySortByGroupId = new Option() {
    @Override
    public boolean isSelected() {
      return myState.getSortByGroupId();
    }

    @Override
    public void setSelected(boolean selected) {
      myState.setSortByGroupId(selected);
      myTreeModel.setComparator(createComparator());
    }
  };
  private final Option mySortBySeverity = new Option() {
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

  ProblemsViewPanel(@NotNull Project project, @NotNull ProblemsViewState state, @NotNull Supplier<String> name) {
    super(false, .5f, .1f, .9f);
    myProject = project;
    myState = state;
    myName = name;

    myTreeModel.setComparator(createComparator());
    myTree = new Tree(new AsyncTreeModel(myTreeModel, this));
    myTree.setRootVisible(false);
    myTree.getSelectionModel().setSelectionMode(SINGLE_TREE_SELECTION);
    myTree.addTreeSelectionListener(new RestoreSelectionListener());
    myTree.addTreeSelectionListener(event -> mySelectionAlarm.cancelAndRequest());
    new TreeSpeedSearch(myTree);
    EditSourceOnDoubleClickHandler.install(myTree);
    EditSourceOnEnterKeyHandler.install(myTree);
    PopupHandler.installPopupHandler(myTree, "ProblemsView.ToolWindow.TreePopup", ActionPlaces.POPUP);
    myTreeExpander = new DefaultTreeExpander(myTree);

    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction("ProblemsView.ToolWindow.Toolbar");
    myToolbar = ActionManager.getInstance().createActionToolbar(getClass().getName(), group, false);
    myToolbar.getComponent().setVisible(state.getShowToolbar());
    UIUtil.addBorder(myToolbar.getComponent(), new CustomLineBorder(myToolbarInsets));

    myPanel = new JPanel(new BorderLayout());
    myPanel.add(BorderLayout.CENTER, createScrollPane(myTree, true));
    myPanel.add(BorderLayout.WEST, myToolbar.getComponent());
    setFirstComponent(myPanel);

    putClientProperty(UIUtil.NOT_IN_HIERARCHY_COMPONENTS, (Iterable<ProblemsViewPreview>)()
      -> JBIterable.of(myPreview).filter(component -> null == component.getParent()).iterator());
  }

  @Override
  public void dispose() {
    visibilityChangedTo(false);
    myPreview.preview(false);
  }

  @Override
  public @Nullable Object getData(@NotNull String dataId) {
    if (CommonDataKeys.PROJECT.is(dataId)) return getProject();
    if (PlatformDataKeys.TREE_EXPANDER.is(dataId)) return getTreeExpander();
    if (PlatformDataKeys.FILE_EDITOR.is(dataId)) {
      // this code allows to perform Editor's Undo action from the Problems View
      VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(this);
      return file == null ? null : getPreview().findFileEditor(file, getProject());
    }
    Node node = getSelectedNode();
    if (node != null) {
      if (CommonDataKeys.NAVIGATABLE.is(dataId)) return node.getNavigatable();
      if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) return node.getVirtualFile();
      if (CommonDataKeys.NAVIGATABLE_ARRAY.is(dataId)) {
        Navigatable navigatable = node.getNavigatable();
        return navigatable == null ? null : new Navigatable[]{navigatable};
      }
      if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
        VirtualFile file = node.getVirtualFile();
        return file == null ? null : new VirtualFile[]{file};
      }
    }
    return null;
  }

  final void updateToolWindowContent() {
    myUpdateAlarm.cancelAndRequest();
  }

  @Nullable Icon getToolWindowIcon(int count) {
    return null;
  }

  @NotNull @NlsContexts.TabTitle String getName(int count) {
    String name = myName.get();
    if (count <= 0) return name;
    return new HtmlBuilder().append(name).append(" ").append(
      HtmlChunk.tag("font").attr("color", toHtmlColor(UIUtil.getInactiveTextColor())).addText(String.valueOf(count))
    ).wrapWithHtmlBody().toString();
  }

  @Override
  protected void loadProportion() {
    if (myState != null) setProportion(myState.getProportion());
  }

  @Override
  protected void saveProportion() {
    if (myState != null) myState.setProportion(getProportion());
  }

  final @NotNull Project getProject() {
    return myProject;
  }

  final @NotNull ProblemsViewState getState() {
    return myState;
  }

  final @NotNull ProblemsTreeModel getTreeModel() {
    return myTreeModel;
  }

  final @NotNull Tree getTree() {
    return myTree;
  }

  final @NotNull ProblemsViewPreview getPreview() {
    return myPreview;
  }

  @Nullable TreeExpander getTreeExpander() {
    return myTreeExpander;
  }

  void orientationChangedTo(boolean vertical) {
    setOrientation(vertical);
    myPanel.remove(myToolbar.getComponent());
    myToolbar.setOrientation(vertical ? SwingConstants.HORIZONTAL : SwingConstants.VERTICAL);
    myToolbarInsets.right = !vertical ? scale(1) : 0;
    myToolbarInsets.bottom = vertical ? scale(1) : 0;
    myPanel.add(vertical ? BorderLayout.NORTH : BorderLayout.WEST, myToolbar.getComponent());
    updatePreview();
  }

  void selectionChangedTo(boolean selected) {
    if (selected) {
      myTreeModel.setComparator(createComparator());
      updatePreview();

      ToolWindow window = ProblemsView.getToolWindow(getProject());
      if (window instanceof ToolWindowEx) {
        ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction("ProblemsView.ToolWindow.SecondaryActions");
        ((ToolWindowEx)window).setAdditionalGearActions(group);
      }
    }
    visibilityChangedTo(selected);
  }

  void visibilityChangedTo(boolean visible) {
    if (visible) {
      myShowTime.set(System.nanoTime());
      ProblemsViewStatsCollector.tabShown(this);
    }
    else {
      Long time = myShowTime.getAndSet(null);
      if (time != null) ProblemsViewStatsCollector.tabHidden(this, System.nanoTime() - time);
      IntentionsUI.getInstance(getProject()).hide();
    }
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
    if (isActiveTab() && isNotNullAndSelected(getAutoscrollToSource())) {
      invokeLater(() -> {
        Node node = getSelectedNode();
        Navigatable navigatable = node == null ? null : node.getNavigatable();
        if (navigatable != null && navigatable.canNavigateToSource()) {
          ClientId.withClientId(myClientId, () -> {
            navigate(false, navigatable);
          });
        }
      });
    }
  }

  private void updatePreview() {
    Editor editor = myPreview.preview(isNotNullAndSelected(getShowPreview()));
    if (editor != null) {
      invokeLater(() -> {
        if (EditorActivityManager.getInstance().isVisible(editor)) {
          Node node = getSelectedNode();
          OpenFileDescriptor descriptor = node == null ? null : node.getDescriptor();
          if (descriptor != null) {
            ClientId.withClientId(myClientId, () -> {
              descriptor.navigateIn(editor);
            });
          }
        }
      });
    }
  }

  private void invokeLater(@NotNull Runnable runnable) {
    getApplication().invokeLater(runnable, stateForComponent(this));
  }

  @NotNull Comparator<Node> createComparator() {
    return new NodeComparator(
      isNullableOrSelected(getSortFoldersFirst()),
      isNotNullAndSelected(getSortByGroupId()),
      isNullableOrSelected(getSortBySeverity()),
      isNotNullAndSelected(getSortByName()));
  }

  @Nullable Option getAutoscrollToSource() {
    return isNotNullAndSelected(getShowPreview()) ? null : myAutoscrollToSource;
  }

  @Nullable Option getShowPreview() {
    return myShowPreview;
  }

  @Nullable Option getSortFoldersFirst() {
    return null; // TODO:malenkov - support file hierarchy & mySortFoldersFirst;
  }

  @Nullable Option getSortByGroupId() {
    return this instanceof HighlightingPanel ? mySortByGroupId : null;
  }

  @Nullable Option getSortBySeverity() {
    return this instanceof HighlightingPanel ? mySortBySeverity : null;
  }

  @Nullable Option getSortByName() {
    return mySortByName;
  }

  private static boolean isNotNullAndSelected(@Nullable Option option) {
    return option != null && option.isSelected();
  }

  private static boolean isNullableOrSelected(@Nullable Option option) {
    return option == null || option.isSelected();
  }
}
