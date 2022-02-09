// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.DataManager;
import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereTabDescriptor;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.ide.util.gotoByName.GotoActionModel;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;

public class GotoActionAction extends SearchEverywhereBaseAction implements DumbAware, LightEditCompatible {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    String tabID = Registry.is("search.everywhere.group.contributors.by.type")
                   ? SearchEverywhereTabDescriptor.IDE.getId()
                   : ActionSearchEverywhereContributor.class.getSimpleName();
    showInSearchEverywherePopup(tabID, e, false, true);
  }

  /** @deprecated please use {@link #openOptionOrPerformAction(Object, String, Project, Component, int)} instead */
  @Deprecated(forRemoval = true)
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  public static void openOptionOrPerformAction(@NotNull Object element,
                                               String enteredText,
                                               @Nullable Project project,
                                               @Nullable Component component) {
    openOptionOrPerformAction(element, enteredText, project, component, 0);
  }

  public static void openOptionOrPerformAction(@NotNull Object element,
                                               String enteredText,
                                               @Nullable Project project,
                                               @Nullable Component component,
                                               @JdkConstants.InputEventMask int modifiers) {
    // invoke later to let the Goto Action popup close completely before the action is performed
    // and avoid focus issues if the action shows complicated popups itself
    ApplicationManager.getApplication().invokeLater(() -> {
      if (project != null && project.isDisposed()) return;

      if (element instanceof OptionDescription) {
        OptionDescription optionDescription = (OptionDescription)element;
        if (optionDescription.hasExternalEditor()) {
          optionDescription.invokeInternalEditor();
        }
        else {
          ShowSettingsUtilImpl.showSettingsDialog(project, optionDescription.getConfigurableId(), enteredText);
        }
      }
      else {
        IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(() -> performAction(element, component, null, modifiers));
      }
    });
  }

  public static void performAction(@NotNull Object element, @Nullable Component component, @Nullable AnActionEvent e) {
    performAction(element, component, e, 0);
  }

  private static void performAction(@NotNull Object element,
                                    @Nullable Component component,
                                    @Nullable AnActionEvent e,
                                    @JdkConstants.InputEventMask int modifiers) {
    // element could be AnAction (SearchEverywhere)
    if (component == null) return;
    ApplicationManager.getApplication().invokeLater(() -> performActionImpl(element, component, e, modifiers));
  }

  private static void performActionImpl(@NotNull Object element,
                                        @NotNull Component component,
                                        @Nullable AnActionEvent e,
                                        @JdkConstants.InputEventMask int modifiers) {
    GotoActionModel.ActionWrapper wrapper = element instanceof AnAction ? null : (GotoActionModel.ActionWrapper)element;
    AnAction action = element instanceof AnAction ? (AnAction)element : wrapper.getAction();
    Presentation presentation = wrapper != null && wrapper.hasPresentation() ? wrapper.getPresentation() :
                                action.getTemplatePresentation().clone();
    InputEvent inputEvent = e != null ? e.getInputEvent() : null;
    DataManager dataManager = DataManager.getInstance();
    DataContext context = dataManager != null ? dataManager.getDataContext(component) : DataContext.EMPTY_CONTEXT;
    AnActionEvent event = new AnActionEvent(
      inputEvent, context, ActionPlaces.ACTION_SEARCH, presentation, ActionManager.getInstance(),
      inputEvent == null ? modifiers : inputEvent.getModifiers());
    event.setInjectedContext(action.isInInjectedContext());
    if (ActionUtil.lastUpdateAndCheckDumb(action, event, false)) {
      if (action instanceof ActionGroup &&
          !(event.getPresentation().isPerformGroup() || ((ActionGroup)action).canBePerformed(context))) {
        ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
          event.getPresentation().getText(), (ActionGroup)action, context,
          JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false, null, -1, null, ActionPlaces.ACTION_SEARCH_INDUCED_POPUP);
        Window window = SwingUtilities.getWindowAncestor(component);
        if (window != null) {
          popup.showInCenterOf(window);
        }
        else {
          popup.showInFocusCenter();
        }
      }
      else {
        ActionUtil.performActionDumbAwareWithCallbacks(action, event);
      }
    }
  }

  @Override
  protected boolean requiresProject() {
    return false;
  }
}
