// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.actions;

import com.intellij.ide.actions.CopyElementAction;
import com.intellij.ide.actions.PopupInMainMenuActionGroup;
import com.intellij.ide.actions.QuickSwitchSchemeAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Condition;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class RefactoringQuickListPopupAction extends QuickSwitchSchemeAction {

  public RefactoringQuickListPopupAction() {
    setInjectedContext(true);
    myActionPlace = ActionPlaces.REFACTORING_QUICKLIST;
  }

  @Override
  public void fillActions(@Nullable Project project,
                          @NotNull DefaultActionGroup group,
                          @NotNull DataContext dataContext) {
    if (project == null) return;
    ActionManager actionManager = ActionManager.getInstance();
    ActionGroup refactoringGroup = (ActionGroup)actionManager.getAction(IdeActions.GROUP_REFACTOR);
    if (refactoringGroup == null) return;
    group.add(new MyGroup(refactoringGroup, actionManager));
  }

  private static boolean isRefactoringAction(@NotNull AnAction child, @NotNull ActionManagerImpl actionManager) {
    AnAction action = child instanceof OverridingAction ? actionManager.getBaseAction((OverridingAction)child) : child;
    return action instanceof BaseRefactoringAction ||
           action instanceof RenameElementAction ||
           action instanceof CopyElementAction;
  }


  @Override
  protected void showPopup(AnActionEvent e, ListPopup popup) {
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor != null) {
      popup.showInBestPositionFor(editor);
    }
    else {
      super.showPopup(e, popup);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    e.getPresentation().setVisible(
      ActionPlaces.isMainMenuOrActionSearch(e.getPlace())
      || ActionPlaces.ACTION_PLACE_QUICK_LIST_POPUP_ACTION.equals(e.getPlace())
      || ActionPlaces.TOUCHBAR_GENERAL.equals(e.getPlace())
    );
  }

  @Override
  protected String getPopupTitle(@NotNull AnActionEvent e) {
    return RefactoringBundle.message("refactor.this.title");
  }

  private static class MyGroup extends ActionGroup implements UpdateInBackground {
    final ActionGroup delegate;
    final ActionManager actionManager;

    private MyGroup(ActionGroup delegate, ActionManager actionManager) {
      this.delegate = delegate;
      this.actionManager = actionManager;
      getTemplatePresentation().copyFrom(delegate.getTemplatePresentation());
      setPopup(false);
    }

    @Override
    public boolean isUpdateInBackground() {
      return UpdateInBackground.isUpdateInBackground(delegate);
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      AnAction[] children = delegate.getChildren(e);
      String place = e == null ? ActionPlaces.REFACTORING_QUICKLIST : e.getPlace();
      Condition<AnAction> popupCondition = o ->
        o instanceof PopupInMainMenuActionGroup ||
        o instanceof ActionGroup && ((ActionGroup)o).isPopup(place);
      if (ContainerUtil.find(children, popupCondition) == null) {
        return children;
      }
      ArrayList<AnAction> actions = new ArrayList<>(2 * children.length);
      for (AnAction child : children) {
        if (!popupCondition.value(child)) {
          actions.add(child);
          continue;
        }
        AtomicReference<String> separatorText = new AtomicReference<>(child.getTemplateText());
        actions.add(new Separator(separatorText::get));
        actions.add(new MyGroup((ActionGroup)child, actionManager) {
          @Override
          public void update(@NotNull AnActionEvent e) {
            delegate.update(e);
            separatorText.set(e.getPresentation().getText());
          }
        });
        actions.add(Separator.getInstance());
      }
      return actions.toArray(EMPTY_ARRAY);
    }

    @Override
    public @NotNull List<AnAction> postProcessVisibleChildren(@NotNull List<AnAction> visibleChildren,
                                                              @NotNull UpdateSession updateSession) {
      boolean isRootGroup = getClass() == MyGroup.class;
      return ContainerUtil.filter(visibleChildren, o ->
        o instanceof Separator && (isRootGroup || ((Separator)o).getText() != null) ||
        isRefactoringAction(o, (ActionManagerImpl)actionManager) &&
        updateSession.presentation(o).isEnabledAndVisible());
    }
  }
}
