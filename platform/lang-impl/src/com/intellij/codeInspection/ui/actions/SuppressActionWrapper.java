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

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.ui.InspectionTreeNode;
import com.intellij.codeInspection.ui.ProblemDescriptionNode;
import com.intellij.codeInspection.ui.RefElementNode;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Separator;
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
import java.util.Comparator;
import java.util.Set;

public class SuppressActionWrapper extends ActionGroup {
  private final Project myProject;
  private final InspectionManagerEx myManager;
  private final Set<InspectionTreeNode> myNodesToSuppress = new HashSet<InspectionTreeNode>();
  private final InspectionToolWrapper myToolWrapper;
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.actions.SuppressActionWrapper");

  public SuppressActionWrapper(@NotNull final Project project,
                               @NotNull final InspectionToolWrapper toolWrapper,
                               @NotNull final TreePath... paths) {
    super(InspectionsBundle.message("suppress.inspection.problem"), false);
    myProject = project;
    myManager = (InspectionManagerEx)InspectionManager.getInstance(myProject);
    for (TreePath path : paths) {
      final Object node = path.getLastPathComponent();
      if (!(node instanceof TreeNode)) continue;
      TreeUtil.traverse((TreeNode)node, new TreeUtil.Traverse() {
        @Override
        public boolean accept(final Object node) {    //fetch leaves
          final InspectionTreeNode n = (InspectionTreeNode)node;
          if (n.isLeaf()) {
            myNodesToSuppress.add(n);
          }
          return true;
        }
      });
    }
    myToolWrapper = toolWrapper;
  }

  @Override
  @NotNull
  public AnAction[] getChildren(@Nullable final AnActionEvent e) {
    final SuppressIntentionAction[] suppressActions = InspectionManagerEx.getSuppressActions(myToolWrapper);
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

  private boolean suppress(@NotNull final PsiElement element,
                           final CommonProblemDescriptor descriptor,
                           final SuppressIntentionAction action,
                           final RefEntity refEntity) {
    if (action instanceof SuppressIntentionActionFromFix && !(descriptor instanceof ProblemDescriptor)) {
      LOG.info("local suppression fix for specific problem descriptor:  " + myToolWrapper.getTool().getClass().getName());
    }
    final PsiModificationTracker tracker = PsiManager.getInstance(myProject).getModificationTracker();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        PsiDocumentManager.getInstance(myProject).commitAllDocuments();
        try {
          final long startModificationCount = tracker.getModificationCount();

          PsiElement container = null;
          if (action instanceof SuppressIntentionActionFromFix) {
            container = ((SuppressIntentionActionFromFix)action).getContainer(element);
          }
          if (container == null) {
            container = element;
          }

          if (action.isAvailable(myProject, null, element)) {
            action.invoke(myProject, null, element);
          }
          if (startModificationCount != tracker.getModificationCount()) {
            final Set<GlobalInspectionContextImpl> globalInspectionContexts = myManager.getRunningContexts();
            for (GlobalInspectionContextImpl context : globalInspectionContexts) {
              context.ignoreElement(myToolWrapper.getTool(), container);
              if (descriptor != null) {
                context.getPresentation(myToolWrapper).ignoreCurrentElementProblem(refEntity, descriptor);
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

  @Override
  public void update(final AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(InspectionManagerEx.getSuppressActions(myToolWrapper) != null);
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

  public class SuppressTreeAction extends AnAction {
    private final SuppressIntentionAction mySuppressAction;

    public SuppressTreeAction(final SuppressIntentionAction suppressAction) {
      super(suppressAction.getText());
      mySuppressAction = suppressAction;
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
            @Override
            public void run() {
              for (InspectionTreeNode node : myNodesToSuppress) {
                final Pair<PsiElement, CommonProblemDescriptor> content = getContentToSuppress(node);
                if (content.first == null) break;
                final PsiElement element = content.first;
                RefEntity refEntity = null;
                if (node instanceof RefElementNode) {
                  refEntity = ((RefElementNode)node).getElement();
                } else if (node instanceof ProblemDescriptionNode) {
                  refEntity = ((ProblemDescriptionNode)node).getElement();
                }
                if (!suppress(element, content.second, mySuppressAction, refEntity)) break;
              }
              final Set<GlobalInspectionContextImpl> globalInspectionContexts = myManager.getRunningContexts();
              for (GlobalInspectionContextImpl context : globalInspectionContexts) {
                context.refreshViews();
              }
              CommandProcessor.getInstance().markCurrentCommandAsGlobal(myProject);
            }
          }, getTemplatePresentation().getText(), null);
        }
      });
    }

    @Override
    public void update(final AnActionEvent e) {
      super.update(e);
      if (!isAvailable()) e.getPresentation().setVisible(false);
    }

    public boolean isAvailable() {
      for (InspectionTreeNode node : myNodesToSuppress) {
        final Pair<PsiElement, CommonProblemDescriptor> content = getContentToSuppress(node);
        if (content.first == null) continue;
        final PsiElement element = content.first;
        if (mySuppressAction.isAvailable(myProject, null, element)) {
          return true;
        }
      }
      return false;
    }

    public boolean isSuppressAll() {
      return mySuppressAction.isSuppressAll();
    }
  }
}
