// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView.toolWindow;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ToggleOptionAction.Option;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.content.Content;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.OpenSourceUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Comparator;
import java.util.function.Predicate;

import static com.intellij.ui.ColorUtil.toHtmlColor;
import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static com.intellij.ui.scale.JBUIScale.scale;
import static com.intellij.util.ui.UIUtil.getInactiveTextColor;
import static javax.swing.tree.TreeSelectionModel.SINGLE_TREE_SELECTION;

abstract class ProblemsViewPanel extends OnePixelSplitter implements Disposable {
  private final Project myProject;
  private final ProblemsViewState myState;
  private final ProblemsTreeModel myTreeModel = new ProblemsTreeModel(this);
  private final ProblemsViewPreview myPreview = new ProblemsViewPreview(this);
  private final JPanel myPanel;
  private final ActionToolbar myToolbar;
  private final Insets myToolbarInsets = JBUI.insetsRight(1);
  private final JTree myTree;

  private final Option myAutoscrollToSource = new Option() {
    @Override
    public boolean isSelected() {
      return myState.getAutoscrollToSource();
    }

    @Override
    public void setSelected(boolean selected) {
      myState.setAutoscrollToSource(selected);
      updateAutoscroll(getDescriptor(getTree().getSelectionPath()));
    }
  };
  private final Option myShowPreview = new Option() {
    @Override
    public boolean isSelected() {
      return myState.getShowPreview();
    }

    @Override
    public void setSelected(boolean selected) {
      myState.setShowPreview(selected);
      updatePreview(getDescriptor(getTree().getSelectionPath()));
    }
  };
  private final Option myShowErrors = new Option() {
    @Override
    public boolean isSelected() {
      return myState.getShowErrors();
    }

    @Override
    public void setSelected(boolean selected) {
      myState.setShowErrors(selected);
      myTreeModel.setFilter(createFilter());
    }
  };
  private final Option myShowWarnings = new Option() {
    @Override
    public boolean isSelected() {
      return myState.getShowWarnings();
    }

    @Override
    public void setSelected(boolean selected) {
      myState.setShowWarnings(selected);
      myTreeModel.setFilter(createFilter());
    }
  };
  private final Option myShowInformation = new Option() {
    @Override
    public boolean isSelected() {
      return myState.getShowInformation();
    }

    @Override
    public void setSelected(boolean selected) {
      myState.setShowInformation(selected);
      myTreeModel.setFilter(createFilter());
    }
  };
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

  ProblemsViewPanel(@NotNull Project project, @NotNull ProblemsViewState state) {
    super(false, .5f, .1f, .9f);
    myProject = project;
    myState = state;

    myTree = new Tree(new AsyncTreeModel(myTreeModel, this));
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(false);
    myTree.getSelectionModel().setSelectionMode(SINGLE_TREE_SELECTION);
    myTree.addTreeSelectionListener(event -> {
      OpenFileDescriptor descriptor = getDescriptor(event.getPath());
      updateAutoscroll(descriptor);
      updatePreview(descriptor);
    });

    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction("ProblemsView.ToolWindow.Toolbar");
    myToolbar = ActionManager.getInstance().createActionToolbar(getClass().getName(), group, false);
    myToolbar.getComponent().setVisible(state.getShowToolbar());
    UIUtil.addBorder(myToolbar.getComponent(), new CustomLineBorder(myToolbarInsets));

    myPanel = new JPanel(new BorderLayout());
    myPanel.add(BorderLayout.CENTER, createScrollPane(myTree, true));
    myPanel.add(BorderLayout.WEST, myToolbar.getComponent());
    setFirstComponent(myPanel);
  }

  @Override
  public void dispose() {
    myPreview.preview(null);
  }

  abstract @NotNull String getDisplayName();

  final void updateDisplayName() {
    ToolWindow window = ProblemsView.getToolWindow(myProject);
    if (window == null) return;
    Content content = window.getContentManager().getContent(this);
    if (content == null) return;

    String name = getDisplayName();
    Root root = myTreeModel.getRoot();
    int count = root == null ? 0 : root.getProblemsCount();
    if (count > 0) {
      //noinspection HardCodedStringLiteral
      name = "<html><body>" + name + " <font color='" + toHtmlColor(getInactiveTextColor()) + "'>" + count + "</font></body></html>";
      //name = name + " (" + count + ")";
    }
    content.setDisplayName(name);
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

  final @NotNull ProblemsTreeModel getTreeModel() {
    return myTreeModel;
  }

  final @NotNull JTree getTree() {
    return myTree;
  }

  void orientationChangedTo(boolean vertical) {
    setOrientation(vertical);
    myPanel.remove(myToolbar.getComponent());
    myToolbar.setOrientation(vertical ? SwingConstants.HORIZONTAL : SwingConstants.VERTICAL);
    myToolbarInsets.right = !vertical ? scale(1) : 0;
    myToolbarInsets.bottom = vertical ? scale(1) : 0;
    myPanel.add(vertical ? BorderLayout.NORTH : BorderLayout.WEST, myToolbar.getComponent());
    updatePreview(getDescriptor(getTree().getSelectionPath()));
  }

  void selectionChangedTo(boolean selected) {
    myTreeModel.setComparator(createComparator());
    myTreeModel.setFilter(createFilter());
    updatePreview(getDescriptor(getTree().getSelectionPath()));

    ToolWindow window = ProblemsView.getToolWindow(getProject());
    if (window instanceof ToolWindowEx) {
      ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction("ProblemsView.ToolWindow.SecondaryActions");
      ((ToolWindowEx)window).setAdditionalGearActions(group);
    }
  }

  private static @Nullable OpenFileDescriptor getDescriptor(@Nullable TreePath path) {
    ProblemNode node = TreeUtil.getLastUserObject(ProblemNode.class, path);
    return node == null ? null : node.getDescriptor();
  }

  private void updateAutoscroll(@Nullable OpenFileDescriptor descriptor) {
    if (descriptor != null && isNotNullAndSelected(getAutoscrollToSource())) {
      ApplicationManager.getApplication().invokeLater(
        () -> OpenSourceUtil.navigate(false, descriptor),
        ModalityState.stateForComponent(getTree())
      );
    }
  }

  private void updatePreview(@Nullable OpenFileDescriptor descriptor) {
    myPreview.preview(isNotNullAndSelected(getShowPreview()) ? descriptor : null);
  }

  void select(@NotNull Node node) {
    TreeUtil.promiseSelect(getTree(), createVisitor(node));
  }

  @NotNull TreeVisitor createVisitor(@NotNull Node node) {
    return new TreeVisitor.ByTreePath<>(node.getPath(), o -> o);
  }

  Comparator<Node> createComparator() {
    return new NodeComparator(
      isNullableOrSelected(getSortFoldersFirst()),
      isNotNullAndSelected(getSortBySeverity()),
      isNotNullAndSelected(getSortByName()));
  }

  Predicate<Node> createFilter() {
    return new NodeFilter(
      isNullableOrSelected(getShowErrors()),
      isNotNullAndSelected(getShowWarnings()),
      isNotNullAndSelected(getShowInformation()));
  }

  @Nullable Option getAutoscrollToSource() {
    return isVertical() ? myAutoscrollToSource : null;
  }

  @Nullable Option getShowPreview() {
    return isVertical() ? null : myShowPreview;
  }

  @Nullable Option getShowErrors() {
    return myShowErrors;
  }

  @Nullable Option getShowWarnings() {
    return myShowWarnings;
  }

  @Nullable Option getShowInformation() {
    return myShowInformation;
  }

  @Nullable Option getSortFoldersFirst() {
    return mySortFoldersFirst;
  }

  @Nullable Option getSortBySeverity() {
    return mySortBySeverity;
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
