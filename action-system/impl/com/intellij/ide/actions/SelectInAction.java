package com.intellij.ide.actions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.*;
import com.intellij.ide.impl.ProjectViewSelectInTarget;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SelectInAction extends AnAction {
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
    } else {
      presentation.setEnabled(true);
      presentation.setVisible(true);
    }
  }

  private static void invoke(DataContext dataContext, SelectInContext context) {
    final List<SelectInTarget> targetVector = getTargets(context.getProject());
    ListPopup popup;
    if (targetVector.size() == 0) {
      DefaultActionGroup group = new DefaultActionGroup();
      group.add(new NoTargetsAction());
      popup = JBPopupFactory.getInstance().createActionGroupPopup(IdeBundle.message("title.popup.select.target"), group,
                                                                  dataContext, JBPopupFactory.ActionSelectionAid.MNEMONICS, true);
    }
    else {
      popup = JBPopupFactory.getInstance().createWizardStep(new SelectActionStep(targetVector, context, context.getVirtualFile()));
    }

    popup.showInBestPositionFor(dataContext);
  }

  private static class SelectActionStep extends BaseListPopupStep<SelectInTarget> {
    private final List<SelectInTarget> myTargets;
    private final SelectInContext mySelectInContext;
    private final VirtualFile myVirtualFile;

    public SelectActionStep(final List<SelectInTarget> targetVector, SelectInContext selectInContext, VirtualFile virtualFile) {
      myTargets = targetVector;
      mySelectInContext = selectInContext;
      myVirtualFile = virtualFile;
      init(IdeBundle.message("title.popup.select.target"),myTargets, null);
    }

    @NotNull
    public String getTextFor(final SelectInTarget value) {
      String text = value.toString();
      int n = myTargets.indexOf(value);
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

    public PopupStep onChosen(final SelectInTarget target, final boolean finalChoice) {
      if (finalChoice) {
        target.selectIn(mySelectInContext, true);
        return FINAL_CHOICE;
      }
      if (hasSubstep(target)) {
        return createSubStep((ProjectViewSelectInTarget)target,myVirtualFile);
      }
      return FINAL_CHOICE;
    }

    private PopupStep createSubStep(final ProjectViewSelectInTarget target, final VirtualFile virtualFile) {
      class SelectSubIdAction extends AnAction {
        private final String mySubId;
        public SelectSubIdAction(String subId, String presentableName) {
          super(presentableName);
          mySubId = subId;
        }

        public void update(AnActionEvent e) {
          e.getPresentation().setEnabled(target.isSubIdSelectable(mySubId, virtualFile));
        }

        public void actionPerformed(AnActionEvent e) {
          target.setSubId(mySubId);
          target.selectIn(mySelectInContext, true);
        }
      }
      DefaultActionGroup group = new DefaultActionGroup();
      for (String subId : target.getSubIds()) {
        SelectSubIdAction action = new SelectSubIdAction(subId, target.getSubIdPresentableName(subId));
        group.add(action);
      }
      DataContext dataContext = DataManager.getInstance().getDataContext();
      final Component component = (Component)dataContext.getData(DataConstants.CONTEXT_COMPONENT);

      String subTitle = IdeBundle.message("title.popup.select.subtarget", target.toString());
      return JBPopupFactory.getInstance().createActionsStep(group, dataContext, false, true, subTitle, component, true);
    }

    public boolean hasSubstep(final SelectInTarget selectedValue) {
      if (!(selectedValue instanceof ProjectViewSelectInTarget)) return false;
      final ProjectViewSelectInTarget target = (ProjectViewSelectInTarget)selectedValue;
      return target.getSubIds().length != 0;
    }

    public boolean isSelectable(final SelectInTarget target) {
      return target.canSelect(mySelectInContext);
    }

    public boolean isMnemonicsNavigationEnabled() {
      return true;
    }
  }

  private static List<SelectInTarget> getTargets(final Project project) {
    ArrayList<SelectInTarget> result = new ArrayList<SelectInTarget>(Arrays.asList(getTargetsFor(project)));

    if (result.size() > 1) {
      rearrangeTargetList(project, result);
    }

    return result;
  }

  private static SelectInTarget[] getTargetsFor(final Project project) {
    return getSelectInManager(project).getTargets();
  }

  private static SelectInManager getSelectInManager(Project project) {
    return SelectInManager.getInstance(project);
  }

  private static void rearrangeTargetList(final Project project, final ArrayList<SelectInTarget> result) {
    final String activeToolWindowId = ToolWindowManager.getInstance(project).getActiveToolWindowId();
    if (activeToolWindowId != null) {
      SelectInTarget firstTarget = result.get(0);
      if (activeToolWindowId.equals(firstTarget.getToolWindowId())) {
        boolean shouldMoveToBottom = true;
        if (ToolWindowId.PROJECT_VIEW.equals(activeToolWindowId)) {
          final String currentMinorViewId = ProjectView.getInstance(project).getCurrentViewId();
          shouldMoveToBottom = currentMinorViewId != null && currentMinorViewId.equals(firstTarget.getMinorViewId());
        }
        if (shouldMoveToBottom) {
          result.remove(0);
          result.add(firstTarget);
        }
      }
    }
  }

  private static class NoTargetsAction extends AnAction {
    public NoTargetsAction() {
      super(IdeBundle.message("message.no.targets.available"));
    }

    public void actionPerformed(AnActionEvent e) {
    }
  }
}