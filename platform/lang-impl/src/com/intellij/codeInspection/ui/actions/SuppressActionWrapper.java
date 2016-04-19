/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInspection.ui.actions;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.ui.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.IncorrectOperationException;
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
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.actions.SuppressActionWrapper");

  public SuppressActionWrapper() {
    super(InspectionsBundle.message("suppress.inspection.problem"), false);
  }

  @Override
  @NotNull
  public AnAction[] getChildren(@Nullable final AnActionEvent e) {
    final InspectionResultsView view = getView(e);
    if (view == null) return AnAction.EMPTY_ARRAY;
    final InspectionToolWrapper wrapper = view.getTree().getSelectedToolWrapper();
    if (wrapper == null) return AnAction.EMPTY_ARRAY;
    final SuppressIntentionAction[] suppressActions = InspectionManagerEx.getSuppressActions(wrapper);
    if (suppressActions == null || suppressActions.length == 0) return new SuppressTreeAction[0];
    final AnAction[] actions = new AnAction[suppressActions.length + 1];
    for (int i = 0; i < suppressActions.length; i++) {
      final SuppressIntentionAction suppressAction = suppressActions[i];
      actions[i] = new SuppressTreeAction(suppressAction);
    }
    actions[suppressActions.length] = Separator.getInstance();
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

  private static boolean suppress(@NotNull final PsiElement element,
                                  final CommonProblemDescriptor descriptor,
                                  final SuppressIntentionAction action,
                                  @NotNull final RefEntity refEntity, InspectionToolWrapper wrapper) {
    if (action instanceof SuppressIntentionActionFromFix && !(descriptor instanceof ProblemDescriptor)) {
      LOG.info("local suppression fix for specific problem descriptor:  " + wrapper.getTool().getClass().getName());
    }
    final Project project = element.getProject();
    final PsiModificationTracker tracker = PsiManager.getInstance(project).getModificationTracker();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        try {
          final long startModificationCount = tracker.getModificationCount();

          PsiElement container = null;
          if (action instanceof SuppressIntentionActionFromFix) {
            container = ((SuppressIntentionActionFromFix)action).getContainer(element);
          }
          if (container == null) {
            container = element;
          }

          if (action.isAvailable(project, null, element)) {
            action.invoke(project, null, element);
          }
          if (startModificationCount != tracker.getModificationCount()) {
            final Set<GlobalInspectionContextImpl> globalInspectionContexts = ((InspectionManagerEx)InspectionManager.getInstance(element.getProject())).getRunningContexts();
            for (GlobalInspectionContextImpl context : globalInspectionContexts) {
              context.ignoreElement(wrapper.getTool(), container);
              if (descriptor != null) {
                context.getPresentation(wrapper).ignoreCurrentElementProblem(refEntity, descriptor);
              }
            }
          }
        }
        catch (IncorrectOperationException e1) {
          LOG.error(e1);
        }
      }
    });
    return true;
  }

  private static Pair<PsiElement, CommonProblemDescriptor> getContentToSuppress(InspectionTreeNode node) {
    RefElement refElement = null;
    CommonProblemDescriptor descriptor = null;
    if (node instanceof RefElementNode) {
      final RefElementNode elementNode = (RefElementNode)node;
      final RefEntity element = elementNode.getElement();
      refElement = element instanceof RefElement ? (RefElement)element : null;
      descriptor = elementNode.getProblem();
    }
    else if (node instanceof ProblemDescriptionNode) {
      final ProblemDescriptionNode descriptionNode = (ProblemDescriptionNode)node;
      final RefEntity element = descriptionNode.getElement();
      refElement = element instanceof RefElement ? (RefElement)element : null;
      descriptor = descriptionNode.getDescriptor();
    }
    PsiElement element = descriptor instanceof ProblemDescriptor ? ((ProblemDescriptor)descriptor).getPsiElement() : refElement != null ? refElement.getElement() : null;
    return Pair.create(element, descriptor);
  }

  public static class SuppressTreeAction extends KeyAwareInspectionViewAction {
    private final SuppressIntentionAction mySuppressAction;

    public SuppressTreeAction(final SuppressIntentionAction suppressAction) {
      super(suppressAction.getText());
      mySuppressAction = suppressAction;
    }

    @Override
    protected void actionPerformed(@NotNull InspectionResultsView view, @NotNull HighlightDisplayKey key) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          Project project = view.getProject();
          CommandProcessor.getInstance().executeCommand(project, new Runnable() {
            @Override
            public void run() {
              final InspectionToolWrapper wrapper = view.getTree().getSelectedToolWrapper();
              for (SuppressableInspectionTreeNode node : getNodesToSuppress(view)) {
                final Pair<PsiElement, CommonProblemDescriptor> content = getContentToSuppress(node);
                if (content.first == null) break;
                final PsiElement element = content.first;
                RefEntity refEntity = node.getElement();
                if (!suppress(element, content.second, mySuppressAction, refEntity, wrapper)) break;
                node.markAsSuppressedFromView();
              }
              final Set<GlobalInspectionContextImpl> globalInspectionContexts = ((InspectionManagerEx)InspectionManager.getInstance(project)).getRunningContexts();
              for (GlobalInspectionContextImpl context : globalInspectionContexts) {
                context.refreshViews();
              }
              CommandProcessor.getInstance().markCurrentCommandAsGlobal(project);
            }
          }, getTemplatePresentation().getText(), null);
        }
      });
    }

    @Override
    protected boolean isEnabled(@NotNull InspectionResultsView view) {
      for (InspectionTreeNode node : getNodesToSuppress(view)) {
        final Pair<PsiElement, CommonProblemDescriptor> content = getContentToSuppress(node);
        if (content.first == null) continue;
        final PsiElement element = content.first;
        if (mySuppressAction.isAvailable(view.getProject(), null, element)) {
          return true;
        }
      }
      return false;
    }

    public boolean isSuppressAll() {
      return mySuppressAction.isSuppressAll();
    }
  }

  private static Set<SuppressableInspectionTreeNode> getNodesToSuppress(@NotNull InspectionResultsView view) {
    final TreePath[] paths = view.getTree().getSelectionPaths();
    if (paths == null) return Collections.emptySet();
    final Set<SuppressableInspectionTreeNode> result = new HashSet<>();
    for (TreePath path : paths) {
      final Object node = path.getLastPathComponent();
      if (!(node instanceof TreeNode)) continue;
      TreeUtil.traverse((TreeNode)node, new TreeUtil.Traverse() {
        @Override
        public boolean accept(final Object node) {    //fetch leaves
          final InspectionTreeNode n = (InspectionTreeNode)node;
          if (n instanceof SuppressableInspectionTreeNode &&
              ((SuppressableInspectionTreeNode)n).canSuppress() &&
              !((SuppressableInspectionTreeNode)n).isAlreadySuppressedFromView()) {
            result.add((SuppressableInspectionTreeNode)n);
          }
          return true;
        }
      });
    }
    return result;
  }
}
