package com.intellij.ide.actions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.*;
import com.intellij.ide.impl.ProjectViewSelectInTarget;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.wm.FocusCommand;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.*;
import java.util.List;

public class SelectInAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.select.in");
    SelectInContext context = SelectInContextImpl.createContext(e);
    if (context == null) return;
    invoke(e.getDataContext(), context);
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();

    if (SelectInContextImpl.createContext(event) == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
    }
    else {
      presentation.setEnabled(true);
      presentation.setVisible(true);
    }
  }

  private static void invoke(DataContext dataContext, SelectInContext context) {
    final List<SelectInTarget> targetVector = Arrays.asList(getSelectInManager(context.getProject()).getTargets());
    ListPopup popup;
    if (targetVector.isEmpty()) {
      DefaultActionGroup group = new DefaultActionGroup();
      group.add(new NoTargetsAction());
      popup = JBPopupFactory.getInstance().createActionGroupPopup(IdeBundle.message("title.popup.select.target"), group, dataContext,
                                                                  JBPopupFactory.ActionSelectionAid.MNEMONICS, true);
    }
    else {
      popup = JBPopupFactory.getInstance().createListPopup(new TopLevelActionsStep(targetVector, context));
    }

    popup.showInBestPositionFor(dataContext);
  }

  private static class TopLevelActionsStep extends BaseListPopupStep<SelectInTarget> {
    private final SelectInContext mySelectInContext;
    private final List<SelectInTarget> myProjectViewTargets;
    private final List<SelectInTarget> myVisibleTargets;

    private final FakeProjectViewSelectInTarget PROJECT_VIEW_FAKE_TARGET = new FakeProjectViewSelectInTarget();

    private class FakeProjectViewSelectInTarget implements SelectInTarget {
      public boolean canSelect(SelectInContext context) {
        for (SelectInTarget projectViewTarget : myProjectViewTargets) {
          if (projectViewTarget.canSelect(mySelectInContext)) return true;
        }
        return false;
      }

      public void selectIn(final SelectInContext context, final boolean requestFocus) {
        ProjectView projectView = ProjectView.getInstance(context.getProject());
        Collection<SelectInTarget> targetsToCheck = new LinkedHashSet<SelectInTarget>();
        String currentId = projectView.getCurrentViewId();
        for (SelectInTarget projectViewTarget : myProjectViewTargets) {
          if (currentId.equals(projectViewTarget.getMinorViewId())) {
            targetsToCheck.add(projectViewTarget);
            break;
          }
        }
        targetsToCheck.addAll(myProjectViewTargets);
        for (final SelectInTarget target : targetsToCheck) {
          if (target.canSelect(context)) {
            if (requestFocus) {
              IdeFocusManager.getInstance(context.getProject()).requestFocus(new FocusCommand() {
                public ActionCallback run() {
                  target.selectIn(context, requestFocus);
                  return new ActionCallback.Done();
                }
              }, true);
            }
            else {
              target.selectIn(context, requestFocus);
            }
            break;
          }
        }
      }

      public String getToolWindowId() {
        return ToolWindowId.PROJECT_VIEW;
      }

      public String getMinorViewId() {
        return null;
      }

      public float getWeight() {
        return 0;
      }

      public String toString() {
        return IdeBundle.message("select.in.title.project.view");
      }
    }

    public TopLevelActionsStep(@NotNull final List<SelectInTarget> targetVector, SelectInContext selectInContext) {
      mySelectInContext = selectInContext;
      myProjectViewTargets = new ArrayList<SelectInTarget>();
      myVisibleTargets = new ArrayList<SelectInTarget>();
      for (SelectInTarget target : targetVector) {
        if (target instanceof ProjectViewSelectInTarget) {
          myProjectViewTargets.add(target);
        }
        else {
          myVisibleTargets.add(target);
        }
      }
      if (!myProjectViewTargets.isEmpty()) {
        myVisibleTargets.add(0, PROJECT_VIEW_FAKE_TARGET);
      }
      init(IdeBundle.message("title.popup.select.target"), myVisibleTargets, null);
    }

    @NotNull
    public String getTextFor(final SelectInTarget value) {
      String text = value.toString();
      int n = myVisibleTargets.indexOf(value);
      return numberingText(n, text);
    }

    public PopupStep onChosen(final SelectInTarget target, final boolean finalChoice) {
      if (finalChoice) {
        target.selectIn(mySelectInContext, true);
        return FINAL_CHOICE;
      }
      if (target == PROJECT_VIEW_FAKE_TARGET) {
        return createProjectViewsStep();
      }
      if (target instanceof CompositeSelectInTarget) {
        return createSubIdsStep((CompositeSelectInTarget)target, mySelectInContext);
      }
      return FINAL_CHOICE;
    }

    private PopupStep createProjectViewsStep() {
      return new SelectActionStep(myProjectViewTargets, mySelectInContext);
    }

    public boolean hasSubstep(final SelectInTarget selectedValue) {
      if (selectedValue == PROJECT_VIEW_FAKE_TARGET) return true;
      return selectedValue instanceof CompositeSelectInTarget &&
             ((CompositeSelectInTarget)selectedValue).getSubIds().length != 0;
    }

    public boolean isSelectable(final SelectInTarget target) {
      return target.canSelect(mySelectInContext);
    }

    public boolean isMnemonicsNavigationEnabled() {
      return true;
    }
  }

  private static String numberingText(final int n, String text) {
    if (n < 9) {
      text = "&" + (n + 1) + ". " + text;
    }
    else if (n == 9) {
      text = "&" + 0 + ". " + text;
    }
    else {
      text = "&" + (char)('A' + n - 10) + ". " + text;
    }
    return text;
  }

  private static class SelectActionStep extends BaseListPopupStep<SelectInTarget> {
    private final List<SelectInTarget> myTargets;

    private final SelectInContext mySelectInContext;

    public SelectActionStep(@NotNull final List<SelectInTarget> targetVector, SelectInContext selectInContext) {
      mySelectInContext = selectInContext;
      myTargets = targetVector;
      init(IdeBundle.message("select.in.title.project.view"), myTargets, null);
    }

    @NotNull
    public String getTextFor(final SelectInTarget value) {
      int n = myTargets.indexOf(value);
      String text = value.toString();
      return numberingText(n, text);
    }

    public PopupStep onChosen(final SelectInTarget target, final boolean finalChoice) {
      if (finalChoice) {
        target.selectIn(mySelectInContext, true);
        return FINAL_CHOICE;
      }
      if (hasSubstep(target)) {
        return createSubIdsStep((CompositeSelectInTarget)target, mySelectInContext);
      }
      return FINAL_CHOICE;
    }

    public boolean hasSubstep(final SelectInTarget selectedValue) {
      return selectedValue instanceof CompositeSelectInTarget && ((CompositeSelectInTarget)selectedValue).getSubIds().length != 0;
    }

    public boolean isSelectable(final SelectInTarget target) {
      final String activeToolWindowId = ToolWindowManager.getInstance(mySelectInContext.getProject()).getActiveToolWindowId();
      if (target instanceof ProjectViewSelectInTarget &&
          ToolWindowId.PROJECT_VIEW.equals(activeToolWindowId) &&
          Comparing.strEqual(ProjectView.getInstance(mySelectInContext.getProject()).getCurrentViewId(), target.getMinorViewId())) {
        return false;
      }

      return target.canSelect(mySelectInContext);
    }

    public boolean isMnemonicsNavigationEnabled() {
      return true;
    }
  }

  private static SelectInManager getSelectInManager(Project project) {
    return SelectInManager.getInstance(project);
  }

  private static PopupStep createSubIdsStep(final CompositeSelectInTarget target, final SelectInContext context) {
    class SelectSubIdAction extends AnAction {
      private final String mySubId;

      public SelectSubIdAction(String subId, String presentableName) {
        super(presentableName);
        mySubId = subId;
      }

      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(target.isSubIdSelectable(mySubId, context));
      }

      public void actionPerformed(AnActionEvent e) {
        target.setSubId(mySubId);
        target.selectIn(context, true);
      }
    }
    DefaultActionGroup group = new DefaultActionGroup();
    int i=0;
    for (String subId : target.getSubIds()) {
      SelectSubIdAction action = new SelectSubIdAction(subId, numberingText(i++, target.getSubIdPresentableName(subId)));
      group.add(action);
    }
    DataContext dataContext = DataManager.getInstance().getDataContext();
    final Component component = (Component)dataContext.getData(DataConstants.CONTEXT_COMPONENT);

    String subTitle = IdeBundle.message("title.popup.select.subtarget", target.toString());
    return JBPopupFactory.getInstance().createActionsStep(group, dataContext, false, true, subTitle, component, true);
  }

  private static class NoTargetsAction extends AnAction {
    public NoTargetsAction() {
      super(IdeBundle.message("message.no.targets.available"));
    }

    public void actionPerformed(AnActionEvent e) {
    }
  }
}