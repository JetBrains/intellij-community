/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.ui.actions;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.SuppressUtil;
import com.intellij.codeInsight.daemon.impl.actions.AddNoInspectionDocTagAction;
import com.intellij.codeInsight.daemon.impl.actions.AddSuppressWarningsAnnotationAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.codeInspection.ui.InspectionTree;
import com.intellij.codeInspection.ui.InspectionTreeNode;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.util.List;
import java.util.Set;

/**
 * User: anna
 * Date: 11-Jan-2006
 */
public class SuppressInspectionToolbarAction extends AnAction {
  private InspectionResultsView myView;
  private static final Logger LOG = Logger.getInstance("com.intellij.codeInspection.ui.actions.SuppressInspectionToolbarAction");

  public SuppressInspectionToolbarAction(final InspectionResultsView view) {
    super(InspectionsBundle.message("suppress.inspection.family"), InspectionsBundle.message("suppress.inspection.family"),
          IconLoader.getIcon("/general/inspectionsOff.png"));
    myView = view;
  }

  public void actionPerformed(AnActionEvent e) {
    final InspectionTool selectedTool = myView.getTree().getSelectedTool();
    assert selectedTool != null;
    getSuppressAction(selectedTool, myView.getTree().getSelectionPaths(), HighlightDisplayKey.find(selectedTool.getShortName()).getID())
      .actionPerformed(e);
  }

  public void update(AnActionEvent e) {
    if (!myView.isSingleToolInSelection()) {
      e.getPresentation().setEnabled(false);
      return;
    }
    final InspectionTool selectedTool = myView.getTree().getSelectedTool();
    assert selectedTool != null;
    final HighlightDisplayKey key = HighlightDisplayKey.find(selectedTool.getShortName());
    if (key == null) {
      e.getPresentation().setEnabled(false);
      return;
    }
    getSuppressAction(selectedTool, myView.getTree().getSelectionPaths(), HighlightDisplayKey.find(selectedTool.getShortName()).getID())
      .update(e);
  }


  private AnAction getSuppressAction(final InspectionTool tool, final TreePath[] selectionPaths, final String id) {
    final Project project = myView.getProject();
    final InspectionManagerEx managerEx = (InspectionManagerEx)InspectionManagerEx.getInstance(project);
    return new AnAction(InspectionsBundle.message("inspection.quickfix.suppress", tool.getDisplayName())) {
      public void actionPerformed(AnActionEvent e) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            CommandProcessor.getInstance().executeCommand(project, new Runnable() {
              public void run() {
                final CustomSuppressableInspectionTool suppresableTool = extractSuppressableTool(tool);
                for (TreePath treePath : selectionPaths) {
                  final InspectionTreeNode node = (InspectionTreeNode)treePath.getLastPathComponent();
                  if (suppresableTool != null) {
                    for (final ProblemDescriptor descriptor : myView.getTree().getSelectedDescriptors()) {
                      final IntentionAction[] actions = suppresableTool.getSuppressActions(descriptor);
                      if (actions.length > 0) {
                        if (!suppress(descriptor.getPsiElement(), actions[0], tool, project)) break;
                      }
                    }
                  }
                  else {
                    final List<RefElement> elementsToSuppress = InspectionTree.getElementsToSuppressInSubTree(node);
                    for (final RefElement refElement : elementsToSuppress) {
                      final PsiElement element = refElement.getElement();
                      final IntentionAction action = getCorrectIntentionAction(tool, id, null, element);
                      if (!suppress(element, action, tool, project)) break;
                    }
                  }
                  refreshViews(managerEx);
                }
              }
            }, InspectionsBundle.message("inspection.quickfix.suppress"), null);
          }
        });
      }

      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(true);
        final Project project = myView.getProject();
        final CustomSuppressableInspectionTool suppresableTool = extractSuppressableTool(tool);
        if (suppresableTool != null) {
          for (ProblemDescriptor descriptor : myView.getTree().getSelectedDescriptors()) {
            for (IntentionAction action : suppresableTool.getSuppressActions(descriptor)) {
              if (action.isAvailable(project, null, descriptor.getPsiElement().getContainingFile())) {
                return;
              }
            }
          }
        }

        for (TreePath treePath : selectionPaths) {
          final InspectionTreeNode node = (InspectionTreeNode)treePath.getLastPathComponent();
          final List<RefElement> elementsToSuppress = InspectionTree.getElementsToSuppressInSubTree(node);
          for (RefElement refElement : elementsToSuppress) {
            final PsiElement element = refElement.getElement();
            if (element instanceof PsiFile) continue;
            if (element == null || !element.isValid()) continue;
            final PsiFile file = element.getContainingFile();
            final IntentionAction action = getCorrectIntentionAction(tool, id, null, element);
            if (action.isAvailable(project, null, file)) {
              return;
            }
          }
        }
        e.getPresentation().setEnabled(false);
      }
    };
  }

  private static boolean suppress(final PsiElement element,
                                  final IntentionAction action,
                                  final InspectionTool tool,
                                  final Project project) {
    final boolean[] needToRefresh = new boolean[]{true};
    final PsiModificationTracker tracker = PsiManager.getInstance(project).getModificationTracker();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        try {
          final long startModificationCount = tracker.getModificationCount();
          action.invoke(project, null, element.getContainingFile());
          if (startModificationCount != tracker.getModificationCount()) {
            final Set<GlobalInspectionContextImpl> globalInspectionContexts =
              ((InspectionManagerEx)InspectionManagerEx.getInstance(project)).getRunningContexts();
            for (GlobalInspectionContextImpl context : globalInspectionContexts) {
              context.ignoreElement(tool, element);
            }
          }
        }
        catch (IncorrectOperationException e1) {
          LOG.error(e1);
        }
      }
    });
    return needToRefresh[0];
  }

  private static void refreshViews(final InspectionManagerEx managerEx) {
    final Set<GlobalInspectionContextImpl> globalInspectionContexts = managerEx.getRunningContexts();
    for (GlobalInspectionContextImpl context : globalInspectionContexts) {
      context.refreshViews();
    }
  }

  private static class SuppressWarningAction extends AddSuppressWarningsAnnotationAction {
    public SuppressWarningAction(final String ID, final PsiElement context) {
      super(ID, context);
    }

    @Nullable
    protected PsiModifierListOwner getContainer() {
      if (!(myContext.getContainingFile().getLanguage() instanceof JavaLanguage) || myContext instanceof PsiFile) {
        return null;
      }
      PsiElement container = myContext;
      while (container instanceof PsiClassInitializer || container instanceof PsiAnonymousClass || container instanceof PsiTypeParameter) {
        container = PsiTreeUtil.getParentOfType(container, PsiMember.class);
      }
      return (PsiModifierListOwner)container;
    }
  }

  private static class SuppressDocCommentAction extends AddNoInspectionDocTagAction {

    public SuppressDocCommentAction(final String ID, final PsiElement context) {
      super(ID, context);
    }

    @Nullable
    protected PsiDocCommentOwner getContainer() {
      if (!(myContext.getContainingFile().getLanguage() instanceof JavaLanguage) || myContext instanceof PsiFile) {
        return null;
      }
      PsiElement container = myContext;
      while (container instanceof PsiTypeParameter) {
        container = PsiTreeUtil.getParentOfType(container, PsiDocCommentOwner.class);
      }
      return (PsiDocCommentOwner)container;
    }
  }

  @Nullable
  private static CustomSuppressableInspectionTool extractSuppressableTool(InspectionTool tool) {
    if (tool instanceof LocalInspectionToolWrapper) {
      final LocalInspectionToolWrapper wrapper = (LocalInspectionToolWrapper)tool;
      final LocalInspectionTool localTool = wrapper.getTool();
      if (localTool instanceof CustomSuppressableInspectionTool) return (CustomSuppressableInspectionTool)localTool;
    }
    return null;
  }

  private static IntentionAction getCorrectIntentionAction(InspectionTool tool,
                                                           String id,
                                                           CommonProblemDescriptor descriptor,
                                                           PsiElement context) {
    CustomSuppressableInspectionTool customSuppresableInspectionTool = extractSuppressableTool(tool);
    if (customSuppresableInspectionTool != null && descriptor instanceof ProblemDescriptor) {
      final IntentionAction[] customActions = customSuppresableInspectionTool.getSuppressActions((ProblemDescriptor)descriptor);
      if (customActions.length > 0) return customActions[0];
    }

    if (SuppressUtil.canHave15Suppressions(context.getContainingFile())) {
      if (!(context instanceof PsiDocCommentOwner && SuppressUtil.alreadyHas14Suppressions((PsiDocCommentOwner)context))) {
        return new SuppressWarningAction(id, context);
      }
    }
    return new SuppressDocCommentAction(id, context);
  }

  @Nullable
  public static AnAction getSuppressAction(final RefElement refElement,
                                           final InspectionTool tool,
                                           final CommonProblemDescriptor descriptor,
                                           final InspectionResultsView view) {
    final HighlightDisplayKey key = HighlightDisplayKey.find(tool.getShortName());
    if (key != null) {
      final PsiElement psiElement = refElement.getElement();
      if (psiElement != null && psiElement.isValid()) {
        final IntentionAction action = SuppressInspectionToolbarAction.getCorrectIntentionAction(tool, key.getID(), descriptor, psiElement);
        final PsiFile file = psiElement.getContainingFile();
        final Project project = view.getProject();
        if (action.isAvailable(project, null, file)) {
          return new AnAction(action.getText()) {
            public void actionPerformed(AnActionEvent e) {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                  CommandProcessor.getInstance().executeCommand(view.getProject(), new Runnable() {
                    public void run() {
                      final InspectionTreeNode treeNode = (InspectionTreeNode)view.getTree().getSelectionPath().getLastPathComponent();
                      final List<RefElement> elementsToSuppress = InspectionTree.getElementsToSuppressInSubTree(treeNode);
                      final InspectionManagerEx managerEx = (InspectionManagerEx)InspectionManagerEx.getInstance(project);
                      for (RefElement element : elementsToSuppress) {                        
                        if (!suppress(element.getElement(), action, tool, project)) break;
                      }
                      refreshViews(managerEx);
                    }
                  }, action.getText(), null);
                }
              });
            }
          };
        }
      }
    }
    return null;
  }
}
