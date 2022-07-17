// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.typeMigration.ui;

import com.intellij.CommonBundle;
import com.intellij.core.JavaPsiBundle;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.refactoring.typeMigration.TypeMigrationProcessor;
import com.intellij.refactoring.typeMigration.usageInfo.TypeMigrationUsageInfo;
import com.intellij.ui.*;
import com.intellij.ui.content.Content;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewContentManager;
import com.intellij.usages.TextChunk;
import com.intellij.usages.UsageInfoToUsageConverter;
import com.intellij.usages.UsagePresentation;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class MigrationPanel extends JPanel implements Disposable {
  private static final DataKey<TypeMigrationUsageInfo[]> MIGRATION_USAGES_KEY = DataKey.create("migration.usages");

  private final PsiElement[] myInitialRoots;
  private final TypeMigrationLabeler myLabeler;


  private final MyTree myRootsTree;
  private final Project myProject;
  private Content myContent;
  private final MigrationUsagesPanel myUsagesPanel;
  private final MigrationConflictsPanel myConflictsPanel;

  public MigrationPanel(final PsiElement[] roots, @NotNull TypeMigrationLabeler labeler, final Project project, final boolean previewUsages) {
    super(new BorderLayout());
    myInitialRoots = roots;
    myLabeler = labeler;
    myProject = project;

    final MigrationRootNode currentRoot = new MigrationRootNode(project, myLabeler, roots, previewUsages);
    myRootsTree = new MyTree();
    TypeMigrationTreeStructure structure = new TypeMigrationTreeStructure(project);
    structure.setRoots(currentRoot);
    StructureTreeModel model = new StructureTreeModel<>(structure, AlphaComparator.INSTANCE, this);
    myRootsTree.setModel(new AsyncTreeModel(model, this));

    initTree(myRootsTree);
    myRootsTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(final TreeSelectionEvent e) {
        selectionChanged();
      }
    });

    final Splitter treeSplitter = new Splitter();
    Disposer.register(this, new Disposable() {
      @Override
      public void dispose() {
        treeSplitter.dispose();
      }
    });
    treeSplitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myRootsTree));


    myUsagesPanel = new MigrationUsagesPanel(myProject);
    treeSplitter.setSecondComponent(myUsagesPanel);
    Disposer.register(this, myUsagesPanel);

    add(createToolbar(), BorderLayout.SOUTH);

    final Splitter conflictsSplitter = new Splitter(true, .8f);
    Disposer.register(this, new Disposable() {
      @Override
      public void dispose() {
        conflictsSplitter.dispose();
      }
    });
    conflictsSplitter.setFirstComponent(treeSplitter);
    myConflictsPanel = new MigrationConflictsPanel(myProject);
    conflictsSplitter.setSecondComponent(myConflictsPanel);
    add(conflictsSplitter, BorderLayout.CENTER);
    Disposer.register(this, myConflictsPanel);

    model.invalidate();

  }

  private void selectionChanged() {
    myConflictsPanel.setToInitialPosition();
    myUsagesPanel.setToInitialPosition();
    final DefaultMutableTreeNode[] migrationNodes = myRootsTree.getSelectedNodes(DefaultMutableTreeNode.class, null);
    if (migrationNodes.length == 0) return;
    final Object userObject = migrationNodes[0].getUserObject();
    if (userObject instanceof MigrationNode) {
      final MigrationNode migrationNode = (MigrationNode)userObject;
      final UsageInfo[] failedUsages = myLabeler.getFailedUsages(migrationNode.getInfo());
      if (failedUsages.length > 0) {
        myConflictsPanel.showUsages(PsiElement.EMPTY_ARRAY, failedUsages);
      }
      final AbstractTreeNode rootNode = migrationNode.getParent();
      if (rootNode instanceof MigrationNode) {
        myUsagesPanel.showRootUsages(((MigrationNode)rootNode).getInfo(), migrationNode.getInfo(), myLabeler);
      }
    }
  }

  private JComponent createToolbar() {
    final JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gc = new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 0, 1, GridBagConstraints.NORTHWEST,
                                                   GridBagConstraints.NONE, JBUI.insets(5, 10, 5, 0), 0, 0);
    final JButton performButton = new JButton(JavaRefactoringBundle.message("type.migration.migrate.button.text"));
    performButton.addActionListener(new ActionListener() {
      private void expandTree(MigrationNode migrationNode) {
        if (!migrationNode.getInfo().isExcluded() || migrationNode.areChildrenInitialized()) { //do not walk into excluded collapsed nodes: nothing to migrate can be found
          final Collection<? extends AbstractTreeNode<?>> nodes = migrationNode.getChildren();
          for (final AbstractTreeNode node : nodes) {
            ApplicationManager.getApplication().runReadAction(() -> expandTree((MigrationNode)node));
          }
        }
      }

      @Override
      public void actionPerformed(final ActionEvent e) {
        final Object root = myRootsTree.getModel().getRoot();
        if (root instanceof DefaultMutableTreeNode) {
          final Object userObject = ((DefaultMutableTreeNode)root).getUserObject();
          if (userObject instanceof MigrationRootNode) {
            ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
              final Set<VirtualFile> files = new HashSet<>();
              final TypeMigrationUsageInfo[] usages = ReadAction.compute(() -> {
                  final Collection<? extends AbstractTreeNode<?>> children = ((MigrationRootNode)userObject).getChildren();
                  for (AbstractTreeNode child : children) {
                    expandTree((MigrationNode)child);
                  }
                  final TypeMigrationUsageInfo[] usages1 = myLabeler.getMigratedUsages();
                  for (TypeMigrationUsageInfo usage : usages1) {
                    if (!usage.isExcluded()) {
                      final PsiElement element = usage.getElement();
                      if (element != null) {
                        files.add(element.getContainingFile().getVirtualFile());
                      }
                    }
                  }
                  return usages1;
                });


              ApplicationManager.getApplication().invokeLater(() -> {
                if (ReadonlyStatusHandler.getInstance(myProject).
                  ensureFilesWritable(files).hasReadonlyFiles()) {
                  return;
                }
                WriteCommandAction.writeCommandAction(myProject).run(() -> TypeMigrationProcessor.change(usages, myLabeler, myProject));
              }, myProject.getDisposed());
            }, JavaRefactoringBundle.message("type.migration.action.name"), false, myProject);
          }
        }
        UsageViewContentManager.getInstance(myProject).closeContent(myContent);
      }
    });
    panel.add(performButton, gc);
    final JButton closeButton = new JButton(CommonBundle.getCancelButtonText());
    closeButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        UsageViewContentManager.getInstance(myProject).closeContent(myContent);

      }
    });
    panel.add(closeButton, gc);
    final JButton rerunButton = new JButton(JavaRefactoringBundle.message("type.migration.rerun.button.text"));
    rerunButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        UsageViewContentManager.getInstance(myProject).closeContent(myContent);
        final TypeMigrationDialog.MultipleElements dialog =
          new TypeMigrationDialog.MultipleElements(myProject, myInitialRoots, myLabeler.getMigrationRootTypeFunction(), myLabeler.getRules());
        dialog.show();
      }
    });
    panel.add(rerunButton, gc);
    final JButton helpButton = new JButton(CommonBundle.getHelpButtonText());
    helpButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        HelpManager.getInstance().invokeHelp("reference.typeMigrationPreview");
      }
    });
    gc.weightx = 1;
    panel.add(helpButton, gc);

    return panel;
  }

  private void initTree(final Tree tree) {
    final TreeCellRenderer rootsTreeCellRenderer = new MigrationRootsTreeCellRenderer();
    tree.setCellRenderer(rootsTreeCellRenderer);
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);

    TreeUtil.installActions(tree);
    TreeUtil.expandAll(tree);
    SmartExpander.installOn(tree);
    EditSourceOnDoubleClickHandler.install(tree);
    new TreeSpeedSearch(tree);
    PopupHandler.installPopupMenu(tree, createTreePopupActions(), "MigrationPanelPopup");
  }

  private ActionGroup createTreePopupActions() {
    final DefaultActionGroup group = new DefaultActionGroup();
    //group.add(new PerformRefactoringAction());
    group.add(new ExcludeAction());
    group.add(new IncludeAction());
    group.addSeparator();
    final ActionManager actionManager = ActionManager.getInstance();
    group.add(actionManager.getAction(IdeActions.ACTION_EDIT_SOURCE));
    group.add(actionManager.getAction(IdeActions.GROUP_VERSION_CONTROLS));
    return group;
  }


  @Override
  public void dispose() {
  }

  public void setContent(final Content content) {
    myContent = content;
    Disposer.register(content, this);
  }

  private static final class MyTree extends Tree implements DataProvider {
    @Override
    protected void paintComponent(final Graphics g) {
      DuplicateNodeRenderer.paintDuplicateNodesBackground(g, this);
      super.paintComponent(g);
    }

    @Override
    public Object getData(@NotNull @NonNls final String dataId) {
      if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
        final DefaultMutableTreeNode[] selectedNodes = getSelectedNodes(DefaultMutableTreeNode.class, null);
        return selectedNodes.length == 1 && selectedNodes[0].getUserObject() instanceof MigrationNode
               ? ((MigrationNode)selectedNodes[0].getUserObject()).getInfo().getElement() : null;
      }
      if (MIGRATION_USAGES_KEY.is(dataId)) {
        DefaultMutableTreeNode[] selectedNodes = getSelectedNodes(DefaultMutableTreeNode.class, null);
        final Set<TypeMigrationUsageInfo> usageInfos = new HashSet<>();
        for (DefaultMutableTreeNode selectedNode : selectedNodes) {
          final Object userObject = selectedNode.getUserObject();
          if (userObject instanceof MigrationNode) {
            collectInfos(usageInfos, (MigrationNode)userObject);
          }
        }
        return usageInfos.toArray(new TypeMigrationUsageInfo[0]);
      }
      return null;
    }

    private static void collectInfos(final Set<? super TypeMigrationUsageInfo> usageInfos, final MigrationNode currentNode) {
      usageInfos.add(currentNode.getInfo());
      if (!currentNode.areChildrenInitialized()) return;
      final Collection<? extends AbstractTreeNode<?>> nodes = currentNode.getChildren();
      for (AbstractTreeNode node : nodes) {
        collectInfos(usageInfos, (MigrationNode)node);
      }
    }
  }

  private class ExcludeAction extends ExcludeIncludeActionBase {
    ExcludeAction() {
      super(JavaRefactoringBundle.message("type.migration.exclude.action.text"));
      registerCustomShortcutSet(CommonShortcuts.getDelete(), myRootsTree);
    }

    @Override
    protected void processUsage(final TypeMigrationUsageInfo usageInfo) {
      usageInfo.setExcluded(true);
    }
  }

  private class IncludeAction extends ExcludeIncludeActionBase {
    IncludeAction() {
      super(JavaRefactoringBundle.message("type.migration.include.action.text"));
      registerCustomShortcutSet(CommonShortcuts.INSERT, myRootsTree);
    }

    @Override
    protected void processUsage(final TypeMigrationUsageInfo usageInfo) {
      usageInfo.setExcluded(false);
    }

    @Override
    public void update(@NotNull final AnActionEvent e) {
      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(false);
      final DefaultMutableTreeNode[] selectedNodes = myRootsTree.getSelectedNodes(DefaultMutableTreeNode.class, null);
      for (DefaultMutableTreeNode node : selectedNodes) {
        final Object userObject = node.getUserObject();
        if (!(userObject instanceof MigrationNode)) return;
        final AbstractTreeNode parent = ((MigrationNode)userObject).getParent(); //disable include if parent was excluded
        if (parent instanceof MigrationNode && ((MigrationNode)parent).getInfo().isExcluded()) return;
      }
      presentation.setEnabled(true);
    }
  }

  private abstract class ExcludeIncludeActionBase extends AnAction {
    protected abstract void processUsage(TypeMigrationUsageInfo usageInfo);

    ExcludeIncludeActionBase(final @NlsActions.ActionText String text) {
      super(text);
    }

    private TypeMigrationUsageInfo @Nullable [] getUsages(AnActionEvent context) {
      return context.getData(MIGRATION_USAGES_KEY);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      final TreePath[] selectionPaths = myRootsTree.getSelectionPaths();
      e.getPresentation().setEnabled(selectionPaths != null && selectionPaths.length > 0);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final TypeMigrationUsageInfo[] usages = getUsages(e);
      assert usages != null;
      for (TypeMigrationUsageInfo usageInfo : usages) {
        processUsage(usageInfo);
      }
      myRootsTree.repaint();
    }
  }

  private static class MigrationRootsTreeCellRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(@NotNull final JTree tree,
                                      final Object value,
                                      final boolean selected,
                                      final boolean expanded,
                                      final boolean leaf,
                                      final int row,
                                      final boolean hasFocus) {
      final Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
      if (!(userObject instanceof MigrationNode)) return;
      final TypeMigrationUsageInfo usageInfo = ((MigrationNode)userObject).getInfo();
      if (usageInfo != null) {
        final PsiElement element = usageInfo.getElement();
        if (element != null) {
          PsiElement typeElement = null;
          if (element instanceof PsiVariable) {
            typeElement = ((PsiVariable)element).getTypeElement();
          } else if (element instanceof PsiMethod) {
            typeElement = ((PsiMethod)element).getReturnTypeElement();
          }
          if (typeElement == null) typeElement = element;

          final UsagePresentation presentation = UsageInfoToUsageConverter.convert(new PsiElement[]{typeElement}, new UsageInfo(typeElement)).getPresentation();
          boolean isPrefix = true;  //skip usage position
          for (TextChunk chunk : presentation.getText()) {
            if (!isPrefix) append(chunk.getText(), patchAttrs(usageInfo, chunk.getSimpleAttributesIgnoreBackground()));
            isPrefix = false;
          }
          setIcon(presentation.getIcon());

          String location;
          if (element instanceof PsiMember) {
            location = SymbolPresentationUtil.getSymbolContainerText(element);
          }
          else {
            final PsiMember member = PsiTreeUtil.getParentOfType(element, PsiMember.class);
            if (member instanceof PsiField) {
              location = PsiFormatUtil.formatVariable((PsiVariable)member, PsiFormatUtilBase.SHOW_NAME |
                                                                           PsiFormatUtilBase.SHOW_CONTAINING_CLASS |
                                                                           PsiFormatUtilBase.SHOW_FQ_NAME, PsiSubstitutor.EMPTY);
            }
            else if (member instanceof PsiMethod) {
              location = PsiFormatUtil.formatMethod((PsiMethod)member, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME |
                                                                                             PsiFormatUtilBase.SHOW_CONTAINING_CLASS |
                                                                                             PsiFormatUtilBase.SHOW_FQ_NAME,
                                                    PsiFormatUtilBase.SHOW_TYPE);
            }
            else if (member instanceof PsiClass) {
              location = PsiFormatUtil.formatClass((PsiClass)member, PsiFormatUtilBase.SHOW_NAME |
                                                                     PsiFormatUtilBase.SHOW_FQ_NAME);
            }
            else {
              location = null;
            }
            if (location != null) location = JavaPsiBundle.message("aux.context.display", location);
          }
          if (location != null) {
            append(location, SimpleTextAttributes.GRAYED_ATTRIBUTES);
          }
        } else {
          append(UsageViewBundle.message("node.invalid"), SimpleTextAttributes.ERROR_ATTRIBUTES);
        }
      }
    }

    private static SimpleTextAttributes patchAttrs(TypeMigrationUsageInfo usageInfo, SimpleTextAttributes original) {
      if (usageInfo.isExcluded()) {
        original = new SimpleTextAttributes(original.getStyle() | SimpleTextAttributes.STYLE_STRIKEOUT, original.getFgColor(), original.getWaveColor());
      }
      return original;
    }
  }
}
