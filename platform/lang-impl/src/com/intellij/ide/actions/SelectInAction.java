// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.DirtyUI;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class SelectInAction extends DumbAwareAction implements PerformWithDocumentsCommitted, ActionRemoteBehaviorSpecification.Frontend {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    SelectInContext context = SelectInContextImpl.createContext(e);
    if (context == null) return;
    invoke(e.getDataContext(), context);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();

    if (SelectInContextImpl.createContext(event) == null) {
      presentation.setEnabledAndVisible(false);
    }
    else {
      presentation.setEnabledAndVisible(true);
    }
  }

  private static void invoke(@NotNull DataContext dataContext, @NotNull SelectInContext context) {
    List<SelectInTarget> targetVector = SelectInManager.getInstance(context.getProject()).getTargetList();
    ListPopup popup;
    if (Registry.is("ide.selectIn.experimental.popup", true)) {
      popup = createActionPopup(dataContext, context, targetVector);
    }
    else {
      popup = createLegacyPopup(dataContext, context, targetVector);
    }
    popup.showInBestPositionFor(dataContext);
  }

  private static @NotNull ListPopup createLegacyPopup(@NotNull DataContext dataContext, @NotNull SelectInContext context, List<SelectInTarget> targetVector) {
    ListPopup popup;
    if (targetVector.isEmpty()) {
      DefaultActionGroup group = new DefaultActionGroup();
      group.add(new NoTargetsAction());
      popup = JBPopupFactory.getInstance().createActionGroupPopup(IdeBundle.message("title.popup.select.target"), group, dataContext,
                                                                  JBPopupFactory.ActionSelectionAid.MNEMONICS, true);
    }
    else {
      popup = JBPopupFactory.getInstance().createListPopup(new SelectInActionsStep(targetVector, context));
    }
    return popup;
  }

  private static @NotNull ListPopup createActionPopup(@NotNull DataContext dataContext, @NotNull SelectInContext context, List<SelectInTarget> targetVector) {
    DefaultActionGroup group = new DefaultActionGroup();
    if (targetVector.isEmpty()) {
      group.add(new NoTargetsAction());
    }
    else {
      for (int i = 0; i < targetVector.size(); i++) {
        SelectInTarget target = targetVector.get(i);
        group.add(SelectInTargetActionKt.createSelectInTargetAction(target, context));
      }
    }
    return JBPopupFactory.getInstance().createActionGroupPopup(
      IdeBundle.message("title.popup.select.target"),
      group,
      dataContext,
      JBPopupFactory.ActionSelectionAid.ALPHA_NUMBERING,
      true
    );
  }

  private static final class SelectInActionsStep extends BaseListPopupStep<SelectInTarget> {
    private final SelectInContext mySelectInContext;
    private final List<SelectInTarget> myVisibleTargets;

    SelectInActionsStep(@NotNull Collection<SelectInTarget> targetVector, @NotNull SelectInContext selectInContext) {
      mySelectInContext = selectInContext;
      myVisibleTargets = new ArrayList<>(targetVector);
      List<Icon> icons = fillInIcons(targetVector, selectInContext);
      init(IdeBundle.message("title.popup.select.target"), myVisibleTargets, icons);
    }

    private static @NotNull List<Icon> fillInIcons(@NotNull Collection<? extends SelectInTarget> targets, @NotNull SelectInContext selectInContext) {
      ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(selectInContext.getProject());
      List<Icon> list = new ArrayList<>();
      for (SelectInTarget target : targets) {
        String id = target.getMinorViewId() == null ? target.getToolWindowId() : null;
        ToolWindow toolWindow = id == null ? null : toolWindowManager.getToolWindow(id);
        Icon icon = toolWindow != null ? toolWindow.getIcon() : EmptyIcon.ICON_13;
        list.add(icon);
      }
      return list;
    }

    @Override
    public @NotNull String getTextFor(final SelectInTarget value) {
      String text = value.toString();
      String id = value.getMinorViewId() == null ? value.getToolWindowId() : null;
      ToolWindow toolWindow = id == null ? null : ToolWindowManager.getInstance(mySelectInContext.getProject()).getToolWindow(id);
      if (toolWindow != null) {
        //this code is left for compatibility with external plugins; plugins from intellij project return proper localized text from SelectInTarget::toString
        //noinspection HardCodedStringLiteral
        text = text.replace(value.getToolWindowId(), toolWindow.getStripeTitle());
      }
      int n = myVisibleTargets.indexOf(value);
      return numberingText(n, text);
    }

    @DirtyUI
    @Override
    public PopupStep<?> onChosen(final SelectInTarget target, final boolean finalChoice) {
      if (finalChoice) {
        PsiDocumentManager.getInstance(mySelectInContext.getProject()).commitAllDocuments();
        target.selectIn(mySelectInContext, true);
        return FINAL_CHOICE;
      }
      if (target instanceof CompositeSelectInTarget) {
        final ArrayList<SelectInTarget> subTargets = new ArrayList<>(((CompositeSelectInTarget)target).getSubTargets(mySelectInContext));
        if (!subTargets.isEmpty()) {
          subTargets.sort(new SelectInManager.SelectInTargetComparator());
          return new SelectInActionsStep(subTargets, mySelectInContext);
        }
      }
      return FINAL_CHOICE;
    }

    @Override
    public boolean hasSubstep(final SelectInTarget selectedValue) {
      return selectedValue instanceof CompositeSelectInTarget &&
             ((CompositeSelectInTarget)selectedValue).getSubTargets(mySelectInContext).size() > 1;
    }

    @DirtyUI
    @Override
    public boolean isSelectable(final SelectInTarget target) {
      if (target == null || !DumbService.getInstance(mySelectInContext.getProject()).isUsableInCurrentContext(target)) {
        return false;
      }
      return target.canSelect(mySelectInContext);
    }

    @Override
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

  private static final class NoTargetsAction extends AnAction {
    NoTargetsAction() {
      //noinspection DialogTitleCapitalization
      super(IdeBundle.messagePointer("message.no.targets.available"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
    }
  }
}