// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.ide.ui.customization.CustomisedActionGroup;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.InputEvent;

/**
 * @author Konstantin Bulenkov
 */
public class NewElementAction extends DumbAwareAction implements PopupAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    createPopupHandler(e).showPopup();
  }

  @ApiStatus.Internal
  protected @NotNull PopupHandler createPopupHandler(@NotNull AnActionEvent e) {
    return isProjectView(e) ? new ProjectViewPopupHandler(e) : new GenericPopupHandler(e);
  }

  private static boolean isProjectView(@NotNull AnActionEvent e) {
    var toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW);
    if (toolWindow == null) return false;
    return ToolWindowId.PROJECT_VIEW.equals(toolWindow.getId());
  }

  protected @Nullable JBPopupFactory.ActionSelectionAid getActionSelectionAid() {
    return null;
  }

  protected int getMaxRowCount() {
    return -1;
  }

  protected @Nullable Condition<AnAction> getPreselectActionCondition(DataContext dataContext) {
    return LangDataKeys.PRESELECT_NEW_ACTION_CONDITION.getData(dataContext);
  }

  protected @Nullable Runnable getDisposeCallback() {
    return null;
  }

  protected boolean isShowDisabledActions() {
    return false;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getProject();
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    if (!isEnabled(e)) {
      presentation.setEnabled(false);
      return;
    }
    if (isProjectView(e)) {
      presentation.setIcon(LayeredIcon.ADD_WITH_DROPDOWN);
      if (ArrayUtil.isEmpty(e.getData(PlatformCoreDataKeys.SELECTED_ITEMS))) {
        presentation.setEnabled(false);
        return;
      }
    }

    presentation.setEnabled(!ActionGroupUtil.isGroupEmpty(getGroup(e.getDataContext()), e));
  }

  protected boolean isEnabled(@NotNull AnActionEvent e) {
    if (Boolean.TRUE.equals(e.getData(LangDataKeys.NO_NEW_ACTION))) {
      return false;
    }
    return true;
  }

  protected ActionGroup getGroup(DataContext dataContext) {
    var result = getCustomizedGroup();
    if (result == null) {
      result = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_WEIGHING_NEW);
    }
    return result;
  }

  private static ActionGroup getCustomizedGroup() {
    // We can't just get a customized GROUP_WEIGHING_NEW because it's only customized as
    // a part of the Project View popup customization, so we get that and dig for the New subgroup.
    // There has to be a better way to do it...
    var projectViewPopupGroup = CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_PROJECT_VIEW_POPUP);
    if (!(projectViewPopupGroup instanceof ActionGroup group)) {
      return null;
    }
    AnAction[] actions = group instanceof DefaultActionGroup o ? o.getChildren(ActionManager.getInstance()) :
                         group instanceof CustomisedActionGroup o && o.getDelegate() instanceof DefaultActionGroup oo ? oo.getChildren(ActionManager.getInstance()) :
                         group.getChildren(null);
    for (AnAction child : actions) {
       if (child instanceof ActionGroup childGroup && isNewElementGroup(childGroup)) {
         return childGroup;
       }
    }
    return null;
  }

  private static boolean isNewElementGroup(ActionGroup group) {
    if (group instanceof WeighingNewActionGroup) {
      return true;
    }
    if (group instanceof ActionGroupWrapper actionGroupWrapper) {
      return isNewElementGroup(actionGroupWrapper.getDelegate());
    }
    return false;
  }

  protected @NotNull String getPlace() {
    return ActionPlaces.getActionGroupPopupPlace(IdeActions.GROUP_WEIGHING_NEW);
  }

  @ApiStatus.Internal
  protected abstract class PopupHandler {
    protected final @NotNull AnActionEvent event;

    protected PopupHandler(@NotNull AnActionEvent e) {
      event = e;
    }

    void showPopup() {
      var popup = createPopup();
      if (popup == null) return;
      customize(popup);
      show(popup);
    }

    protected @Nullable ListPopup createPopup() {
      var dataContext = event.getDataContext();
      return JBPopupFactory.getInstance().createActionGroupPopup(
        getTitle(),
        getGroup(dataContext),
        dataContext,
        getActionSelectionAid(),
        isShowDisabledActions(),
        getDisposeCallback(),
        getMaxRowCount(),
        getPreselectActionCondition(dataContext),
        getPlace());
    }


    protected @Nullable @NlsContexts.PopupTitle String getTitle() {
      return IdeBundle.message("title.popup.new.element");
    }

    protected void customize(@NotNull ListPopup popup) { }

    protected abstract void show(@NotNull ListPopup popup);
  }

  private class GenericPopupHandler extends PopupHandler {
    GenericPopupHandler(@NotNull AnActionEvent e) {
      super(e);
    }

    @Override
    protected void show(@NotNull ListPopup popup) {
      popup.showInBestPositionFor(event.getDataContext());
    }
  }

  private class ProjectViewPopupHandler extends PopupHandler {
    private static final String EMPTY_TEXT_LINK_PLACE = "NewElementInProjectViewPopupEmptyTextLink";

    ProjectViewPopupHandler(@NotNull AnActionEvent e) {
      super(e);
    }

    @Override
    protected @Nullable @NlsContexts.PopupTitle String getTitle() {
      return null;
    }

    @Override
    protected void customize(@NotNull ListPopup popup) {
      if (popup instanceof AbstractPopup abstractPopup) {
        abstractPopup.setSpeedSearchAlwaysShown();
        abstractPopup.setSpeedSearchEmptyText(IdeBundle.message("new.file.popup.search.hint"));
      }
      if (popup instanceof PopupFactoryImpl.ActionGroupPopup listPopup && listPopup.getList() instanceof JBList<?> list) {
        var emptyText = list.getEmptyText();
        emptyText.clear();
        emptyText.appendLine(IdeBundle.message("popup.new.element.empty.text.1"));
        emptyText.appendLine(
          IdeBundle.message("popup.new.element.empty.text.2"),
          SimpleTextAttributes.LINK_ATTRIBUTES,
          linkActionEvent -> {
            Disposer.dispose(popup);
            var component = event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
            if (component != null) {
              var inputEvent = linkActionEvent.getSource() instanceof InputEvent linkInputEvent ? linkInputEvent : null;
              var actionManager = ActionManager.getInstance();
              actionManager.tryToExecute(actionManager.getAction("NewFile"), inputEvent, component, EMPTY_TEXT_LINK_PLACE, true);
            }
          }
        );
        // The capitalization is wrong here because this line continues the previous one.
        //noinspection DialogTitleCapitalization
        emptyText.appendLine(IdeBundle.message("popup.new.element.empty.text.3"));
      }
    }

    @Override
    protected void show(@NotNull ListPopup popup) {
      @Nullable Component showUnderneathComponent = null;
      var inputEvent = event.getInputEvent();
      if (inputEvent != null) {
        var inputComponent = inputEvent.getComponent();
        if (inputComponent instanceof ActionButton) {
          showUnderneathComponent = inputComponent;
        }
      }
      if (showUnderneathComponent != null) {
        popup.showUnderneathOf(showUnderneathComponent);
      }
      else {
        popup.showInBestPositionFor(event.getDataContext());
      }
    }
  }
}
