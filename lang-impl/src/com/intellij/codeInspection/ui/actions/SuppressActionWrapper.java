/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

/*
 * User: anna
 * Date: 29-Jan-2007
 */
package com.intellij.codeInspection.ui.actions;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.ui.InspectionTreeNode;
import com.intellij.codeInspection.ui.ProblemDescriptionNode;
import com.intellij.codeInspection.ui.RefElementNode;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
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
import java.util.Set;

public class SuppressActionWrapper extends ActionGroup {
  private Project myProject;
  private InspectionManagerEx myManager;
  private Set<InspectionTreeNode> myNodesToSuppress = new HashSet<InspectionTreeNode>();
  private InspectionTool myTool;
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.actions.SuppressActionWrapper");

  public SuppressActionWrapper(final Project project,
                                  final InspectionTool tool,
                                  final TreePath[] paths) {
    super(InspectionsBundle.message("suppress.inspection.problem"), false);
    myProject = project;
    myManager = (InspectionManagerEx)InspectionManager.getInstance(myProject);
    for (TreePath path : paths) {
      final Object node = path.getLastPathComponent();
      if (!(node instanceof TreeNode)) continue;
      TreeUtil.traverse((TreeNode)node, new TreeUtil.Traverse() {
        public boolean accept(final Object node) {    //fetch leaves
          final InspectionTreeNode n = (InspectionTreeNode)node;
          if (n.isLeaf()) {
            myNodesToSuppress.add(n);
          }
          return true;
        }
      });
    }
    myTool = tool;
  }

  @NotNull
  public AnAction[] getChildren(@Nullable final AnActionEvent e) {
    final SuppressIntentionAction[] suppressActions = myTool.getSuppressActions();
    if (suppressActions == null || suppressActions.length == 0) return AnAction.EMPTY_ARRAY;
    final AnAction[] actions = new AnAction[suppressActions.length];
    for (int i = 0; i < suppressActions.length; i++) {
      final SuppressIntentionAction suppressAction = suppressActions[i];
      actions[i] = new AnAction(suppressAction.getText()) {
        public void actionPerformed(final AnActionEvent e) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
                public void run() {
                  for (InspectionTreeNode node : myNodesToSuppress) {
                    final Pair<PsiElement, CommonProblemDescriptor> content = getContentToSuppress(node);
                    if (content.first == null) break;
                    final PsiElement element = content.first;
                    if (!suppress(element, suppressAction)) break;
                  }
                  final Set<GlobalInspectionContextImpl> globalInspectionContexts = myManager.getRunningContexts();
                  for (GlobalInspectionContextImpl context : globalInspectionContexts) {
                    context.refreshViews();
                  }
                }
              }, getTemplatePresentation().getText(), null);
            }
          });
        }
      };
    }
    return actions;
  }

  private boolean suppress(final PsiElement element, final SuppressIntentionAction action) {
    final PsiModificationTracker tracker = PsiManager.getInstance(myProject).getModificationTracker();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        PsiDocumentManager.getInstance(myProject).commitAllDocuments();
        try {
          final long startModificationCount = tracker.getModificationCount();
          if (action.isAvailable(myProject, null, element)) {
            action.invoke(myProject, null, element);
          }
          if (startModificationCount != tracker.getModificationCount()) {
            final Set<GlobalInspectionContextImpl> globalInspectionContexts = myManager.getRunningContexts();
            for (GlobalInspectionContextImpl context : globalInspectionContexts) {
              context.ignoreElement(myTool, element);
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

  public void update(final AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(myTool != null && myTool.getSuppressActions() != null);
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
}