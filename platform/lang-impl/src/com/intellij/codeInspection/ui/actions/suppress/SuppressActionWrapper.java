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

/*
 * User: anna
 * Date: 29-Jan-2007
 */
package com.intellij.codeInspection.ui.actions.suppress;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.SequentialModalProgressTask;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;

import static com.intellij.codeInspection.ui.actions.InspectionViewActionBase.getView;

public class SuppressActionWrapper extends ActionGroup implements CompactActionGroup {
  private final static Logger LOG = Logger.getInstance(SuppressActionWrapper.class);

  public SuppressActionWrapper() {
    super(InspectionsBundle.message("suppress.inspection.problem"), false);
  }

  @Override
  @NotNull
  public AnAction[] getChildren(@Nullable final AnActionEvent e) {
    final InspectionResultsView view = getView(e);
    if (view == null) return AnAction.EMPTY_ARRAY;
    final InspectionToolWrapper wrapper = view.getTree().getSelectedToolWrapper(true);
    if (wrapper == null) return AnAction.EMPTY_ARRAY;
    final Set<SuppressIntentionAction> suppressActions = view.getSuppressActions(wrapper);

    if (suppressActions.isEmpty()) return AnAction.EMPTY_ARRAY;
    final AnAction[] actions = new AnAction[suppressActions.size() + 1];

    int i = 0;
    for (SuppressIntentionAction action : suppressActions) {
      actions[i++] = new SuppressTreeAction(action);
    }
    actions[suppressActions.size()] = Separator.getInstance();

    Arrays.sort(actions, new Comparator<AnAction>() {
      @Override
      public int compare(AnAction a1, AnAction a2) {
        return getWeight(a1) - getWeight(a2);
      }

      public int getWeight(AnAction a) {
        return a instanceof Separator ? 0 : ((SuppressTreeAction)a).isSuppressAll() ? 1 : -1;
      }
    });
    return actions;
  }

  public static class SuppressTreeAction extends KeyAwareInspectionViewAction {
    private final SuppressIntentionAction mySuppressAction;

    public SuppressTreeAction(final SuppressIntentionAction suppressAction) {
      super(suppressAction.getText());
      mySuppressAction = suppressAction;
    }

    @Override
    protected void actionPerformed(@NotNull InspectionResultsView view, @NotNull HighlightDisplayKey key) {
      ApplicationManager.getApplication().invokeLater(() -> {
        Project project = view.getProject();
        final String templatePresentationText = getTemplatePresentation().getText();
        LOG.assertTrue(templatePresentationText != null);
        final InspectionToolWrapper wrapper = view.getTree().getSelectedToolWrapper(true);
        LOG.assertTrue(wrapper != null);
        final Set<SuppressableInspectionTreeNode> nodesAsSet = getNodesToSuppress(view);
        final SuppressableInspectionTreeNode[] nodes = nodesAsSet.toArray(new SuppressableInspectionTreeNode[nodesAsSet.size()]);
        CommandProcessor.getInstance().executeCommand(project, () -> {
          CommandProcessor.getInstance().markCurrentCommandAsGlobal(project);
          final SequentialModalProgressTask progressTask =
            new SequentialModalProgressTask(project, templatePresentationText, true);
          progressTask.setMinIterationTime(200);
          progressTask.setTask(new SuppressActionSequentialTask(nodes, mySuppressAction, wrapper, view.getGlobalInspectionContext()));
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
      final Set<SuppressableInspectionTreeNode> suppressNodes = getNodesToSuppress(view);
      for (SuppressableInspectionTreeNode node : suppressNodes) {
        if (node.getAvailableSuppressActions().contains(mySuppressAction)) {
          String text = mySuppressAction.getFamilyName();
          if (suppressNodes.size() == 1) {
            final PsiElement element = node.getSuppressContent().getFirst();
            if (element != null) {
              mySuppressAction.isAvailable(view.getProject(), null, element);
              text = mySuppressAction.getText();
            }
          }
          e.getPresentation().setText(text);
          return true;
        }
      }
      return false;
    }

    public boolean isSuppressAll() {
      return mySuppressAction.isSuppressAll();
    }

    private Set<SuppressableInspectionTreeNode> getNodesToSuppress(@NotNull InspectionResultsView view) {
      final TreePath[] paths = view.getTree().getSelectionPaths();
      if (paths == null) return Collections.emptySet();
      final Set<SuppressableInspectionTreeNode> result = new HashSet<>();
      for (TreePath path : paths) {
        final Object node = path.getLastPathComponent();
        if (!(node instanceof TreeNode)) continue;
        TreeUtil.traverse((TreeNode)node, node1 -> {    //fetch leaves
          final InspectionTreeNode n = (InspectionTreeNode)node1;
          if (n instanceof SuppressableInspectionTreeNode &&
              ((SuppressableInspectionTreeNode)n).canSuppress() &&
              ((SuppressableInspectionTreeNode)n).getAvailableSuppressActions().contains(mySuppressAction) &&
              n.isValid()) {
            result.add((SuppressableInspectionTreeNode)n);
          }
          return true;
        });
      }
      return result;
    }

  }
}
