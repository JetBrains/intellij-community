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
package com.intellij.refactoring.typeMigration.ui;

import com.intellij.CommonBundle;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.refactoring.typeMigration.TypeMigrationProcessor;
import com.intellij.refactoring.typeMigration.usageInfo.TypeMigrationUsageInfo;
import com.intellij.ui.*;
import com.intellij.ui.content.Content;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewManager;
import com.intellij.usages.TextChunk;
import com.intellij.usages.UsageInfoToUsageConverter;
import com.intellij.usages.UsagePresentation;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.Set;

/**
 * @author anna
 * Date: 24-Mar-2008
 */
public class MigrationPanel extends JPanel implements Disposable {
  @NonNls private static final String MIGRATION_USAGES = "migration.usages";
  private static final DataKey<TypeMigrationUsageInfo[]> MIGRATION_USAGES_KEYS = DataKey.create(MIGRATION_USAGES);

  private final PsiElement[] myInitialRoots;
  private final TypeMigrationLabeler myLabeler;


  private final MyTree myRootsTree;
  private final Project myProject;
  private Content myContent;
  private final MigrationUsagesPanel myUsagesPanel;
  private final MigrationConflictsPanel myConflictsPanel;

  public MigrationPanel(final PsiElement[] roots, TypeMigrationLabeler labeler, final Project project, final boolean previewUsages) {
    super(new BorderLayout());
    myInitialRoots = roots;
    myLabeler = labeler;
    myProject = project;

    myRootsTree = new MyTree(new DefaultTreeModel(new DefaultMutableTreeNode()));
    final TypeMigrationTreeBuilder builder = new TypeMigrationTreeBuilder(myRootsTree, project);

    final MigrationRootNode currentRoot = new MigrationRootNode(project, myLabeler, roots, previewUsages);
    builder.setRoot(currentRoot);
    initTree(myRootsTree);
    myRootsTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(final TreeSelectionEvent e) {
        selectionChanged();
      }
    });

    final Splitter treeSplitter = new Splitter();
    Disposer.register(this, new Disposable() {
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
      public void dispose() {
        conflictsSplitter.dispose();
      }
    });
    conflictsSplitter.setFirstComponent(treeSplitter);
    myConflictsPanel = new MigrationConflictsPanel(myProject);
    conflictsSplitter.setSecondComponent(myConflictsPanel);
    add(conflictsSplitter, BorderLayout.CENTER);
    Disposer.register(this, myConflictsPanel);

    builder.addSubtreeToUpdate((DefaultMutableTreeNode)myRootsTree.getModel().getRoot(), () -> SwingUtilities.invokeLater(() -> {
      if (builder.isDisposed()) return;
      myRootsTree.expandPath(new TreePath(myRootsTree.getModel().getRoot()));
      final Collection<? extends AbstractTreeNode> children = currentRoot.getChildren();
      if (!children.isEmpty()) {
        builder.select(children.iterator().next());
      }
    }));

    Disposer.register(this, builder);
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
    final JButton performButton = new JButton(RefactoringBundle.message("type.migration.migrate.button.text"));
    performButton.addActionListener(new ActionListener() {
      private void expandTree(MigrationNode migrationNode) {
        if (!migrationNode.getInfo().isExcluded() || migrationNode.areChildrenInitialized()) { //do not walk into excluded collapsed nodes: nothing to migrate can be found
          final Collection<? extends AbstractTreeNode> nodes = migrationNode.getChildren();
          for (final AbstractTreeNode node : nodes) {
            ApplicationManager.getApplication().runReadAction(() -> expandTree((MigrationNode)node));
          }
        }
      }

      public void actionPerformed(final ActionEvent e) {
        final Object root = myRootsTree.getModel().getRoot();
        if (root instanceof DefaultMutableTreeNode) {
          final Object userObject = ((DefaultMutableTreeNode)root).getUserObject();
          if (userObject instanceof MigrationRootNode) {
            ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
              final HashSet<VirtualFile> files = new HashSet<>();
              final TypeMigrationUsageInfo[] usages = ApplicationManager.getApplication().runReadAction(new Computable<TypeMigrationUsageInfo[]>() {
                @Override
                public TypeMigrationUsageInfo[] compute() {
                  final Collection<? extends AbstractTreeNode> children = ((MigrationRootNode)userObject).getChildren();
                  for (AbstractTreeNode child : children) {
                    expandTree((MigrationNode)child);
                  }
                  final TypeMigrationUsageInfo[] usages = myLabeler.getMigratedUsages();
                  for (TypeMigrationUsageInfo usage : usages) {
                    if (!usage.isExcluded()) {
                      final PsiElement element = usage.getElement();
                      if (element != null) {
                        files.add(element.getContainingFile().getVirtualFile());
                      }
                    }
                  }
                  return usages;
                }
              });


              ApplicationManager.getApplication().invokeLater(() -> {
                if (ReadonlyStatusHandler.getInstance(myProject).
                  ensureFilesWritable(VfsUtilCore.toVirtualFileArray(files)).hasReadonlyFiles()) {
                  return;
                }
                new WriteCommandAction(myProject) {
                  protected void run(@NotNull Result result) throws Throwable {
                    TypeMigrationProcessor.change(usages, myLabeler, myProject);
                  }
                }.execute();
              }, myProject.getDisposed());
            }, "Type Migration", false, myProject);
          }
        }
        UsageViewManager.getInstance(myProject).closeContent(myContent);
      }
    });
    panel.add(performButton, gc);
    final JButton closeButton = new JButton(CommonBundle.getCancelButtonText());
    closeButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        UsageViewManager.getInstance(myProject).closeContent(myContent);

      }
    });
    panel.add(closeButton, gc);
    final JButton rerunButton = new JButton(RefactoringBundle.message("type.migration.rerun.button.text"));
    rerunButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        UsageViewManager.getInstance(myProject).closeContent(myContent);
        SwingUtilities.invokeLater(() -> {
          final TypeMigrationDialog.MultipleElements dialog =
            new TypeMigrationDialog.MultipleElements(myProject, myInitialRoots, myLabeler.getMigrationRootTypeFunction(), myLabeler.getRules());
          dialog.show();
        });
      }
    });
    panel.add(rerunButton, gc);
    final JButton helpButton = new JButton(CommonBundle.getHelpButtonText());
    helpButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        HelpManager.getInstance().invokeHelp("reference.typeMigrationPreview");
      }
    });
    gc.weightx = 1;
    panel.add(helpButton, gc);

    return panel;
  }

  private void initTree(final Tree tree) {
    final MigrationRootsTreeCellRenderer rootsTreeCellRenderer = new MigrationRootsTreeCellRenderer();
    tree.setCellRenderer(rootsTreeCellRenderer);
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    UIUtil.setLineStyleAngled(tree);

    TreeUtil.installActions(tree);
    TreeUtil.expandAll(tree);
    SmartExpander.installOn(tree);
    EditSourceOnDoubleClickHandler.install(tree);
    new TreeSpeedSearch(tree);
    PopupHandler.installUnknownPopupHandler(tree, createTreePopupActions(), ActionManager.getInstance());
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


  public void dispose() {
  }

  public void setContent(final Content content) {
    myContent = content;
    Disposer.register(content, this);
  }

  private static class MyTree extends Tree implements DataProvider {
    private MyTree(final TreeModel treemodel) {
      super(treemodel);
    }

    @Override
    protected void paintComponent(final Graphics g) {
      DuplicateNodeRenderer.paintDuplicateNodesBackground(g, this);
      super.paintComponent(g);
    }

    public Object getData(@NonNls final String dataId) {
      if (DataConstants.PSI_ELEMENT.equals(dataId)) {
        final DefaultMutableTreeNode[] selectedNodes = getSelectedNodes(DefaultMutableTreeNode.class, null);
        return selectedNodes.length == 1 && selectedNodes[0].getUserObject() instanceof MigrationNode
               ? ((MigrationNode)selectedNodes[0].getUserObject()).getInfo().getElement() : null;
      }
      if (MIGRATION_USAGES.equals(dataId)) {
        DefaultMutableTreeNode[] selectedNodes = getSelectedNodes(DefaultMutableTreeNode.class, null);
        final Set<TypeMigrationUsageInfo> usageInfos = new HashSet<>();
        for (DefaultMutableTreeNode selectedNode : selectedNodes) {
          final Object userObject = selectedNode.getUserObject();
          if (userObject instanceof MigrationNode) {
            collectInfos(usageInfos, (MigrationNode)userObject);
          }
        }
        return usageInfos.toArray(new TypeMigrationUsageInfo[usageInfos.size()]);
      }
      return null;
    }

    private static void collectInfos(final Set<TypeMigrationUsageInfo> usageInfos, final MigrationNode currentNode) {
      usageInfos.add(currentNode.getInfo());
      if (!currentNode.areChildrenInitialized()) return;
      final Collection<? extends AbstractTreeNode> nodes = currentNode.getChildren();
      for (AbstractTreeNode node : nodes) {
        collectInfos(usageInfos, (MigrationNode)node);
      }
    }
  }

  private class ExcludeAction extends ExcludeIncludeActionBase {
    public ExcludeAction() {
      super(RefactoringBundle.message("type.migration.exclude.action.text"));
      registerCustomShortcutSet(CommonShortcuts.getDelete(), myRootsTree);
    }

    protected void processUsage(final TypeMigrationUsageInfo usageInfo) {
      usageInfo.setExcluded(true);
    }
  }

  private class IncludeAction extends ExcludeIncludeActionBase {
    public IncludeAction() {
      super(RefactoringBundle.message("type.migration.include.action.text"));
      registerCustomShortcutSet(CommonShortcuts.INSERT, myRootsTree);
    }

    protected void processUsage(final TypeMigrationUsageInfo usageInfo) {
      usageInfo.setExcluded(false);
    }

    @Override
    public void update(final AnActionEvent e) {
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

    ExcludeIncludeActionBase(final String text) {
      super(text);
    }

    @Nullable
    private TypeMigrationUsageInfo[] getUsages(AnActionEvent context) {
      return MIGRATION_USAGES_KEYS.getData(context.getDataContext());
    }

    public void update(AnActionEvent e) {
      final TreePath[] selectionPaths = myRootsTree.getSelectionPaths();
      e.getPresentation().setEnabled(selectionPaths != null && selectionPaths.length > 0);
    }

    public void actionPerformed(AnActionEvent e) {
      final TypeMigrationUsageInfo[] usages = getUsages(e);
      assert usages != null;
      for (TypeMigrationUsageInfo usageInfo : usages) {
        processUsage(usageInfo);
      }
      myRootsTree.repaint();
    }
  }

  private static class MigrationRootsTreeCellRenderer extends ColoredTreeCellRenderer {
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
          PsiDocumentManager.getInstance(element.getProject()).commitAllDocuments();
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
            if (location != null) location = PsiBundle.message("aux.context.display", location);
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
