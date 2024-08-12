// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl;

import com.intellij.ide.PsiCopyPasteManager;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.ui.customization.CustomizationUtil;
import com.intellij.ide.util.treeView.AbstractTreeStructureBase;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TreeUIHelper;
import com.intellij.ui.stripe.ErrorStripe;
import com.intellij.ui.stripe.ErrorStripePainter;
import com.intellij.ui.stripe.TreeUpdater;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.Comparator;

import static com.intellij.ide.projectView.ProjectViewSelectionTopicKt.PROJECT_VIEW_SELECTION_TOPIC;

public abstract class AbstractProjectViewPaneWithAsyncSupport extends AbstractProjectViewPane {
  private AsyncProjectViewSupport myAsyncSupport;
  private JComponent myComponent;

  protected AbstractProjectViewPaneWithAsyncSupport(@NotNull Project project) {
    super(project);
  }

  @Override
  public @NotNull JComponent createComponent() {
    if (myComponent != null) {
      SwingUtilities.updateComponentTreeUI(myComponent);
      return myComponent;
    }

    DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(null);
    DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
    myTree = createTree(treeModel);
    enableDnD();
    JScrollPane treePaneScroll = ScrollPaneFactory.createScrollPane(myTree);
    JComponent promoter = createPromoter();
    if (promoter != null ) {
      JPanel contentAndPromoter = new JPanel();
      contentAndPromoter.setLayout(new BoxLayout(contentAndPromoter, BoxLayout.Y_AXIS));
      contentAndPromoter.add(treePaneScroll);
      contentAndPromoter.add(promoter);
      myComponent = contentAndPromoter;
    } else {
      myComponent = treePaneScroll;
    }
    if (Registry.is("error.stripe.enabled")) {
      ErrorStripePainter painter = new ErrorStripePainter(true);
      Disposer.register(this, new TreeUpdater<>(painter, treePaneScroll, myTree) {
        @Override
        protected void update(ErrorStripePainter painter, int index, Object object) {
          if (object instanceof DefaultMutableTreeNode node) {
            object = node.getUserObject();
          }
          super.update(painter, index, getStripe(object, myTree.isExpanded(index)));
        }
      });
    }
    myTreeStructure = createStructure();
    myAsyncSupport = new AsyncProjectViewSupport(this, myProject, myTreeStructure, createComparator());
    configureAsyncSupport(myAsyncSupport);
    myAsyncSupport.setModelTo(myTree);

    initTree();

    Disposer.register(this, UiNotifyConnector.installOn(myTree, new Activatable() {
      private boolean showing;

      @Override
      public void showNotify() {
        if (!showing) {
          showing = true;
          restoreExpandedPaths();
        }
      }

      @Override
      public void hideNotify() {
        if (showing) {
          showing = false;
          saveExpandedPaths();
        }
      }
    }));
    return myComponent;
  }

  @ApiStatus.Internal
  protected void configureAsyncSupport(@NotNull AsyncProjectViewSupport support) {
  }

  @Override
  public void installComparator(@NotNull Comparator<? super NodeDescriptor<?>> comparator) {
    if (myAsyncSupport != null) {
      myAsyncSupport.setComparator(comparator);
    }
  }

  @Override
  public void dispose() {
    myAsyncSupport = null;
    myComponent = null;
    super.dispose();
  }

  private void initTree() {
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    myTree.getSelectionModel().addTreeSelectionListener(e -> onSelectionChanged());
    myTree.addFocusListener(new FocusListener() {
      void updateIfMultipleSelection() {
        if (myTree != null && myTree.getSelectionCount() > 1) {
          onSelectionChanged();
        }
      }

      @Override
      public void focusGained(FocusEvent e) {
        updateIfMultipleSelection();
      }

      @Override
      public void focusLost(FocusEvent e) {
        updateIfMultipleSelection();
      }
    });
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);

    EditSourceOnDoubleClickHandler.install(myTree);
    EditSourceOnEnterKeyHandler.install(myTree);

    ToolTipManager.sharedInstance().registerComponent(myTree);
    TreeUtil.installActions(myTree);

    TreeUIHelper.getInstance().installTreeSpeedSearch(myTree);

    myTree.addKeyListener(new PsiCopyPasteManager.EscapeHandler());
    CustomizationUtil.installPopupHandler(myTree, IdeActions.GROUP_PROJECT_VIEW_POPUP, ActionPlaces.PROJECT_VIEW_POPUP);
  }

  protected void onSelectionChanged() {
    if (myProject.isDisposed()) return;
    myProject.getMessageBus().syncPublisher(PROJECT_VIEW_SELECTION_TOPIC).onChanged();

    if (myTree != null && myTree.getSelectionModel() != null) {
      int count = myTree.getSelectionModel().getSelectionCount();
      String description = count > 1 && myTree.hasFocus() ? LangBundle.message("project.view.elements.selected", count) : null;
      ActionMenu.showDescriptionInStatusBar(true, myTree, description);
    }
  }

  @Override
  public @NotNull ActionCallback updateFromRoot(boolean restoreExpandedPaths) {
    Runnable afterUpdate;
    final ActionCallback cb = new ActionCallback();
    afterUpdate = cb.createSetDoneRunnable();
    if (myAsyncSupport != null) {
      myAsyncSupport.updateAll(afterUpdate);
    }
    else {
      return ActionCallback.REJECTED;
    }
    return cb;
  }

  @Override
  public void select(Object element, VirtualFile file, boolean requestFocus) {
    selectCB(element, file, requestFocus);
  }

  public @NotNull ActionCallback selectCB(Object element, VirtualFile file, boolean requestFocus) {
    if (file != null) {
      if (myAsyncSupport != null) {
        return myAsyncSupport.select(myTree, element, file);
      }
    }
    return ActionCallback.DONE;
  }

  protected abstract @NotNull AbstractTreeStructureBase createStructure();

  protected abstract @NotNull DnDAwareTree createTree(@NotNull DefaultTreeModel treeModel);

  @ApiStatus.Internal
  protected @Nullable JComponent createPromoter() {
    return null;
  }

  /**
   * @param object   an object that represents a node in the project tree
   * @param expanded {@code true} if the corresponding node is expanded,
   *                 {@code false} if it is collapsed
   * @return a non-null value if the corresponding node should be , or {@code null}
   */
  protected ErrorStripe getStripe(Object object, boolean expanded) {
    if (expanded && object instanceof PsiDirectoryNode) return null;
    if (object instanceof PresentableNodeDescriptor node) {
      TextAttributesKey key = node.getPresentation().getTextAttributesKey();
      TextAttributes attributes = key == null ? null : EditorColorsManager.getInstance().getSchemeForCurrentUITheme().getAttributes(key);
      Color color = attributes == null ? null : attributes.getErrorStripeColor();
      if (color != null) return ErrorStripe.create(color, 1);
    }
    return null;
  }

  @Override
  AsyncProjectViewSupport getAsyncSupport() {
    return myAsyncSupport;
  }

  @ApiStatus.Internal
  public @NotNull AsyncProjectViewSupport createAsyncSupport(@NotNull Disposable parent, @NotNull Comparator<NodeDescriptor<?>> comparator) {
    return new AsyncProjectViewSupport(parent, myProject, createStructure(), comparator);
  }
}
