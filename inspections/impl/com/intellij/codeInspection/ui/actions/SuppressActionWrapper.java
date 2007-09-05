/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

/*
 * User: anna
 * Date: 29-Jan-2007
 */
package com.intellij.codeInspection.ui.actions;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.SuppressUtil;
import com.intellij.codeInsight.daemon.impl.actions.AddNoInspectionDocTagFix;
import com.intellij.codeInsight.daemon.impl.actions.AddSuppressWarningsAnnotationFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.ui.InspectionTreeNode;
import com.intellij.codeInspection.ui.ProblemDescriptionNode;
import com.intellij.codeInspection.ui.RefElementNode;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

class SuppressActionWrapper extends AnAction {
  private boolean myAvailable = true;

  private String myID;
  private boolean myClassToSuppress;

  private Project myProject;
  private InspectionManagerEx myManager;
  private Set<InspectionTreeNode> myNodesToSuppress;
  private InspectionTool myTool;
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.actions.SuppressActionWrapper");

  protected SuppressActionWrapper(final String text,
                                  final Project project,
                                  final InspectionTool tool,
                                  final Set<InspectionTreeNode> nodesToSuppress,
                                  final boolean classToSuppress) {
    super(text);
    myProject = project;
    myManager = (InspectionManagerEx)InspectionManager.getInstance(myProject);
    myNodesToSuppress = nodesToSuppress;
    myTool = tool;
    myID = HighlightDisplayKey.find(tool.getShortName()).getID();
    myClassToSuppress = classToSuppress;
  }

  public void appendAction(List<AnAction> result) {
    update(null);
    if (myAvailable) {
      result.add(this);
    }
  }

  public void actionPerformed(AnActionEvent e) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
          public void run() {
            for (InspectionTreeNode node : myNodesToSuppress) {
              final Pair<RefElement, CommonProblemDescriptor> content = getContentToSuppress(node);
              if (content.first == null) break;
              final PsiElement element = content.first.getElement();
              if (!suppress(element, getAction(content.second, element))) break;
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

  private boolean suppress(final PsiElement element, final IntentionAction action) {
    final PsiModificationTracker tracker = PsiManager.getInstance(myProject).getModificationTracker();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        PsiDocumentManager.getInstance(myProject).commitAllDocuments();
        try {
          final long startModificationCount = tracker.getModificationCount();
          action.invoke(myProject, null, element.getContainingFile());
          if (startModificationCount != tracker.getModificationCount()) {
            final Set<GlobalInspectionContextImpl> globalInspectionContexts = myManager.getRunningContexts();
            for (GlobalInspectionContextImpl context : globalInspectionContexts) {
              context.ignoreElement(myTool, element);
              if (myClassToSuppress) {
                context.ignoreElement(myTool, getParentClass(element));
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

  public void update(final AnActionEvent e) {
    Presentation presentation = null;
    if (e != null) {
      presentation = e.getPresentation();
      presentation.setEnabled(false);
    }
    for (InspectionTreeNode node : myNodesToSuppress) {
      final Pair<RefElement, CommonProblemDescriptor> content = getContentToSuppress(node);
      if (content.first == null) {
        return;
      }
      final PsiElement element = content.first.getElement();
      if (element == null || !getAction(content.second, element).isAvailable(myProject, null, element.getContainingFile())) {
        myAvailable = false;
        return;
      }
    }
    if (e != null) {
      presentation.setEnabled(true);
    }
  }

  private static Pair<RefElement, CommonProblemDescriptor> getContentToSuppress(InspectionTreeNode node) {
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
    return Pair.create(refElement, descriptor);
  }

  private IntentionAction getAction(CommonProblemDescriptor descriptor, PsiElement context) {
    if (descriptor instanceof ProblemDescriptor) {
      if (myTool instanceof LocalInspectionToolWrapper) {
        final LocalInspectionToolWrapper wrapper = (LocalInspectionToolWrapper)myTool;
        final LocalInspectionTool localTool = wrapper.getTool();
        if (localTool instanceof CustomSuppressableInspectionTool) {
          final IntentionAction[] customActions =
            ((CustomSuppressableInspectionTool)localTool).getSuppressActions(context);
          if (customActions != null && customActions.length > 0) return customActions[0];
        }
      }
    }

    if (SuppressUtil.canHave15Suppressions(context.getContainingFile())) {
      if (!(context instanceof PsiDocCommentOwner && SuppressUtil.alreadyHas14Suppressions((PsiDocCommentOwner)context))) {
        return myClassToSuppress ? new AddSuppressWarningsAnnotationFix(myID, context) {
          @Nullable
          protected PsiModifierListOwner getContainer() {
            return (PsiModifierListOwner)getParentClass(super.getContainer());
          }
        } : new AddSuppressWarningsAnnotationFix(myID, context);
      }
    }
    return myClassToSuppress ? new AddNoInspectionDocTagFix(myID, context) {
      @Nullable
      protected PsiDocCommentOwner getContainer() {
        return (PsiDocCommentOwner)getParentClass(super.getContainer());
      }
    } : new AddNoInspectionDocTagFix(myID, context);
  }

  @Nullable
  private static PsiElement getParentClass(PsiElement container) {
    while (container != null) {
      final PsiClass parentClass = PsiTreeUtil.getParentOfType(container, PsiClass.class);
      if (parentClass == null && container instanceof PsiClass) {
        return container;
      }
      container = parentClass;
    }
    return container;
  }
}