/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package com.intellij.codeInspection.ui.actions;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ModifiableModel;
import com.intellij.codeInspection.actions.RunInspectionIntention;
import com.intellij.codeInspection.ex.DisableInspectionToolAction;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.codeInspection.ui.InspectionTree;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.CommonBundle;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.io.IOException;

/**
 * User: anna
 * Date: 11-Jan-2006
 */
public class InspectionsOptionsToolbarAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("com.intellij.codeInspection.ui.actions.SuppressInspectionToolbarAction");
  private InspectionResultsView myView;

  public InspectionsOptionsToolbarAction(final InspectionResultsView view) {
    super(getToolOptions(null), getToolOptions(null), IconLoader.getIcon("/general/inspectionsOff.png"));
    myView = view;    
  }

  public void actionPerformed(AnActionEvent e) {
    final DefaultActionGroup options = new DefaultActionGroup();
    final List<AnAction> actions = createActions();
    for (AnAction action : actions) {
      options.add(action);
    }
    final DataContext dataContext = e.getDataContext();
    final ListPopup popup = JBPopupFactory.getInstance()
      .createActionGroupPopup(getSelectedTool().getDisplayName(), options, dataContext,
                              JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false);
    InspectionResultsView.showPopup(e, popup);
  }

  @Nullable
  private InspectionTool getSelectedTool() {
    return myView.getTree().getSelectedTool();
  }

  public void update(AnActionEvent e) {
    if (!myView.isSingleToolInSelection()) {
      e.getPresentation().setEnabled(false);
      return;
    }
    final InspectionTool selectedTool = getSelectedTool();
    assert selectedTool != null;
    final HighlightDisplayKey key = HighlightDisplayKey.find(selectedTool.getShortName());
    if (key == null) {
      e.getPresentation().setEnabled(false);
    }
    e.getPresentation().setEnabled(true);
    final String text = getToolOptions(selectedTool);
    e.getPresentation().setText(text);
    e.getPresentation().setDescription(text);
  }

  private static String getToolOptions(@Nullable final InspectionTool selectedTool) {
    return InspectionsBundle.message("inspections.view.options.title", selectedTool != null ? selectedTool.getDisplayName() : "");
  }

  public List<AnAction> createActions() {
    final List<AnAction> result = new ArrayList<AnAction>();
    final InspectionTree tree = myView.getTree();
    final InspectionTool tool = tree.getSelectedTool();
    LOG.assertTrue(tool != null);
    final HighlightDisplayKey key = HighlightDisplayKey.find(tool.getShortName());
    LOG.assertTrue(key != null);

    result.add(new DisableInspectionAction(key));

    final RunInspectionIntention runInspectionIntention = new RunInspectionIntention(key);
    result.add(new AnAction(runInspectionIntention.getText()) {
      public void actionPerformed(final AnActionEvent e) {
        runInspectionIntention.rerunInspection((InspectionManagerEx)InspectionManagerEx.getInstance(myView.getProject()), myView.getScope());
      }
    });

    final AnAction[] actions = new SuppressActionsProvider(myView).getSuppressActions();
    if (actions != null) {
      result.addAll(Arrays.asList(actions));
    }

    return result;
  }

  private class DisableInspectionAction extends AnAction {
    private final HighlightDisplayKey myKey;

    public DisableInspectionAction(final HighlightDisplayKey key) {
      super(DisableInspectionToolAction.NAME);
      myKey = key;
    }

    public void actionPerformed(final AnActionEvent e) {
      try {
        if (myView.isProfileDefined()) {
          final ModifiableModel model = myView.getCurrentProfile().getModifiableModel();
          model.disableTool(myKey.toString());
          model.commit();
          myView.updateCurrentProfile();
        } else {
          final RefEntity[] selectedElements = myView.getTree().getSelectedElements();
          final Set<InspectionProfile> files = new HashSet<InspectionProfile>();
          final Project project = myView.getProject();
          final InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(project);
          for (RefEntity selectedElement : selectedElements) {
            if (selectedElement instanceof RefElement) {
              final PsiElement element = ((RefElement)selectedElement).getElement();
              files.add(profileManager.getInspectionProfile(element.getContainingFile()));
            }
          }
          for (InspectionProfile inspectionProfile : files) {
            ModifiableModel model = inspectionProfile.getModifiableModel();
            model.disableTool(myKey.toString());
            model.commit();
          }
          DaemonCodeAnalyzer.getInstance(project).restart();
        }
      }
      catch (IOException e1) {
        Messages.showErrorDialog(myView.getProject(), e1.getMessage(), CommonBundle.getErrorTitle());
      }
    }
  }
}