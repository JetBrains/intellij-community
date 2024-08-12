// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.dnd.*;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ProjectViewProjectNode;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.List;
import java.util.*;

import static com.intellij.ide.dnd.FileCopyPasteUtil.getVirtualFileListFromAttachedObject;

public abstract class AttachableProjectViewPane extends ProjectViewPane {
  private final DropAreaDecorator myDecorator = new DropAreaDecorator();

  public AttachableProjectViewPane(Project project) {
    super(project);
  }

  @Override
  public @NotNull String getTitle() {
    return IdeBundle.message("attachable.project.pane.name");
  }

  @Override
  protected @NotNull ProjectViewTree createTree(@NotNull DefaultTreeModel treeModel) {
    ProjectViewTree tree = super.createTree(treeModel);
    tree.getEmptyText().setText(IdeBundle.message("label.empty.text.attach.directories.with.right.click"));
    return tree;
  }

  @Override
  public @NotNull JComponent createComponent() {
    return myDecorator.wrap(super.createComponent());
  }

  @Override
  protected void beforeDnDUpdate(DnDEvent event) {
    myDecorator.processDnD(event);
  }

  @Override
  protected void beforeDnDLeave() {
    myDecorator.processDnD(null);
  }

  @Override
  protected @NotNull ProjectAbstractTreeStructureBase createStructure() {
    return new ProjectViewPaneTreeStructure() {
      @Override
      protected AbstractTreeNode<?> createRoot(final @NotNull Project project, @NotNull ViewSettings settings) {
        return new ProjectViewProjectNode(project, settings) {
          @Override
          public @NotNull Collection<AbstractTreeNode<?>> getChildren() {
            Project project = Objects.requireNonNull(getProject());
            Set<AbstractTreeNode<?>> result = new LinkedHashSet<>();
            PsiManager psiManager = PsiManager.getInstance(project);
            for (VirtualFile virtualFile : ProjectRootManager.getInstance(project).getContentRoots()) {
              PsiDirectory directory = psiManager.findDirectory(virtualFile);
              if (directory == null) continue;
              result.add(new PsiDirectoryNode(getProject(), directory, getSettings()) {
                @Override
                protected boolean shouldShowModuleName() {
                  return false;
                }
              });
            }
            return result;
          }
        };
      }
    };
  }

  protected void processDroppedDirectories(@NotNull List<? extends VirtualFile> dirs) {
    if (dirs.isEmpty()) return;
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    if (modules.length == 0) return;
    final Module module = modules[0];
    ModuleRootModificationUtil.updateModel(module, model -> {
      for (VirtualFile file : dirs) {
        model.addContentEntry(file);
      }
    });
  }

  private final class DropAreaDecorator extends JPanel implements DnDTargetChecker, DnDDropHandler {
    private JComponent myWrappee;
    private final JPanel myDropArea = new JPanel(new BorderLayout());
    private final JLabel myLabel = new JLabel(
      IdeBundle.message("label.text.html.center.drop.here.to.attach.br.as.a.root.directory.center.html"));

    DropAreaDecorator() {
      super(new BorderLayout());
      myLabel.setHorizontalAlignment(SwingConstants.CENTER);
      myLabel.setForeground(new JBColor(ColorUtil.fromHex("8b98ad"), ColorUtil.fromHex("6c7073")));
      myLabel.setBorder(JBUI.Borders.empty(25));
      myDropArea.setBackground(new JBColor(ColorUtil.fromHex("edf4ff"), ColorUtil.fromHex("343638")));
      myDropArea.add(myLabel, BorderLayout.CENTER);
    }

    private static @NotNull List<VirtualFile> getDirectories(@NotNull DnDEvent event) {
      return ContainerUtil.filter(getVirtualFileListFromAttachedObject(event.getAttachedObject()),
                                  file -> file.isDirectory());
    }

    private @NotNull JComponent wrap(@NotNull JComponent wrappee) {
      if (wrappee != myWrappee) {
        myWrappee = wrappee;
        init(wrappee);
      }
      return this;
    }

    private void init(@NotNull JComponent wrappee) {
      Runnable leaveCallback = () -> {
        if (!isOverComponent(myTree) && !isOverComponent(myLabel)) hideDropArea();
      };
      DnDSupport
        .createBuilder(myDropArea)
        .enableAsNativeTarget()
        .setCleanUpOnLeaveCallback(leaveCallback)
        .setDropEndedCallback(() -> hideDropArea())
        .setTargetChecker(this)
        .setDropHandler(this)
        .setDisposableParent(AttachableProjectViewPane.this)
        .install();

      hideDropArea();
      removeAll();
      add(myDropArea, BorderLayout.SOUTH);
      add(wrappee, BorderLayout.CENTER);
    }

    private void hideDropArea() {
      myLabel.setVisible(false);
    }

    @Override
    public void drop(final @NotNull DnDEvent event) {
      hideDropArea();
      processDroppedDirectories(getDirectories(event));
    }

    @Override
    public boolean update(@NotNull DnDEvent event) {
      if (!isDroppable(event)) {
        hideDropArea();
        return false;
      }
      event.setHighlighting(myLabel, DnDEvent.DropTargetHighlightingType.RECTANGLE);
      event.setDropPossible(true);
      myDropArea.setVisible(true);
      return false;
    }

    private static boolean isDroppable(@NotNull DnDEvent event) {
      return FileCopyPasteUtil.isFileListFlavorAvailable(event);
    }

    private static boolean isOverComponent(@Nullable JComponent component) {
      if (component == null) return false;
      Point location = MouseInfo.getPointerInfo().getLocation();
      Point p = new Point(location);
      SwingUtilities.convertPointFromScreen(p, component);
      return component.getVisibleRect().contains(p);
    }

    private void processDnD(DnDEvent event) {
      if (event != null) {
        myLabel.setVisible(isDroppable(event));
      }
      else if (!isOverComponent(myLabel)) {
        hideDropArea();
      }
    }
  }
}
