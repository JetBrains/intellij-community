// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.ui.actions.suppress;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.SuppressIntentionAction;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.codeInspection.ui.InspectionTreeNode;
import com.intellij.codeInspection.ui.SuppressableInspectionTreeNode;
import com.intellij.codeInspection.ui.actions.KeyAwareInspectionViewAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.SequentialModalProgressTask;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import java.util.*;

import static com.intellij.codeInspection.ui.actions.InspectionViewActionBase.getView;

public final class SuppressActionWrapper extends ActionGroup {
  private static final Logger LOG = Logger.getInstance(SuppressActionWrapper.class);

  public SuppressActionWrapper() {
    super(InspectionsBundle.messagePointer("suppress.inspection.problem"), false);
    getTemplatePresentation().putClientProperty(ActionUtil.HIDE_DISABLED_CHILDREN, true);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public AnAction @NotNull [] getChildren(final @Nullable AnActionEvent e) {
    final InspectionResultsView view = getView(e);
    if (view == null) return AnAction.EMPTY_ARRAY;
    final InspectionToolWrapper wrapper = view.getTree().getSelectedToolWrapper(true);
    if (wrapper == null) return AnAction.EMPTY_ARRAY;
    final Set<SuppressIntentionAction> suppressActions = view.getSuppressActionHolder().getSuppressActions(wrapper);

    if (suppressActions.isEmpty()) return AnAction.EMPTY_ARRAY;
    final AnAction[] actions = new AnAction[suppressActions.size() + 1];

    int i = 0;
    for (SuppressIntentionAction action : suppressActions) {
      actions[i++] = new SuppressTreeAction(action);
    }
    actions[suppressActions.size()] = Separator.getInstance();
    Arrays.sort(actions, Comparator.comparingInt(a -> a instanceof Separator ? 0 : ((SuppressTreeAction)a).isSuppressAll() ? 1 : -1));
    return actions;
  }

  public static final class SuppressTreeAction extends KeyAwareInspectionViewAction {
    private final @NotNull SuppressIntentionAction mySuppressAction;

    public SuppressTreeAction(final @NotNull SuppressIntentionAction suppressAction) {
      super(suppressAction.getText());
      mySuppressAction = suppressAction;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      InspectionResultsView view = getView(e);
      final InspectionToolWrapper wrapper = getToolWrapper(e);
      LOG.assertTrue(wrapper != null);
      final Set<SuppressableInspectionTreeNode> nodesAsSet = getNodesToSuppress(e);
      Project project = e.getProject();
      ApplicationManager.getApplication().invokeLater(() -> {
        final String templatePresentationText = getTemplatePresentation().getText();
        LOG.assertTrue(templatePresentationText != null);

        final SuppressableInspectionTreeNode[] nodes = nodesAsSet.toArray(new SuppressableInspectionTreeNode[0]);
        CommandProcessor.getInstance().executeCommand(project, () -> {
          CommandProcessor.getInstance().markCurrentCommandAsGlobal(project);
          final SequentialModalProgressTask progressTask =
            new SequentialModalProgressTask(project, templatePresentationText, true);
          progressTask.setMinIterationTime(200);
          progressTask.setTask(new SuppressActionSequentialTask(nodes, mySuppressAction, wrapper));
          ProgressManager.getInstance().run(progressTask);
        }, templatePresentationText, null);

        final Set<GlobalInspectionContextImpl> globalInspectionContexts =
          ((InspectionManagerEx)InspectionManager.getInstance(project)).getRunningContexts();
        for (GlobalInspectionContextImpl context : globalInspectionContexts) {
          context.refreshViews();
        }
        view.syncRightPanel();
      });
    }

    @Override
    protected boolean isEnabled(@NotNull InspectionResultsView view, AnActionEvent e) {
      final Set<SuppressableInspectionTreeNode> nodesToSuppress = getNodesToSuppress(e);
      if (nodesToSuppress.isEmpty()) return false;
      if (nodesToSuppress.size() == 1) {
        final PsiElement element = Objects.requireNonNull(ContainerUtil.getFirstItem(nodesToSuppress)).getSuppressContent().getFirst();
        String text = mySuppressAction.getFamilyName();
        if (element != null) {
          mySuppressAction.isAvailable(e.getProject(), null, element);
          text = mySuppressAction.getText();
        }
        e.getPresentation().setText(text);
      } else {
        e.getPresentation().setText(mySuppressAction.getFamilyName());
      }
      return true;
    }

    public boolean isSuppressAll() {
      return mySuppressAction.isSuppressAll();
    }

    private Set<SuppressableInspectionTreeNode> getNodesToSuppress(AnActionEvent e) {
      Object[] selectedNodes = e.getData(PlatformCoreDataKeys.SELECTED_ITEMS);
      if (selectedNodes == null) return Collections.emptySet();
      final Set<SuppressableInspectionTreeNode> result = new HashSet<>();
      for (Object selectedNode : selectedNodes) {
        if (!TreeUtil.treeNodeTraverser((TreeNode)selectedNode).traverse().processEach(node1 -> {    //fetch leaves
          final InspectionTreeNode n = (InspectionTreeNode)node1;
          if (n instanceof SuppressableInspectionTreeNode &&
              ((SuppressableInspectionTreeNode)n).canSuppress() &&
              n.isValid()) {
            if (((SuppressableInspectionTreeNode)n).getAvailableSuppressActions().contains(mySuppressAction)) {
              result.add((SuppressableInspectionTreeNode)n);
            } else {
              return false;
            }
          }
          return true;
        })) {
          return Collections.emptySet();
        }
      }
      return result;
    }
  }
}
