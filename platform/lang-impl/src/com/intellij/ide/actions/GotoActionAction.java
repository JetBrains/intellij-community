// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.DataManager;
import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor;
import com.intellij.ide.actions.searcheverywhere.statistics.SearchFieldStatisticsCollector;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.ide.util.gotoByName.GotoActionModel;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.wm.IdeFocusManager;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;

import static com.intellij.openapi.actionSystem.ex.ActionUtil.POPUP_HANDLER;

public class GotoActionAction extends SearchEverywhereBaseAction implements DumbAware, LightEditCompatible {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    e = SearchFieldStatisticsCollector.wrapEventWithActionStartData(e);
    var event = e;
    String tabID = ActionSearchEverywhereContributor.class.getSimpleName();
    WriteIntentReadAction.run((Runnable)() -> {
      showInSearchEverywherePopup(tabID, event, false, true);
    });
  }

  public static void openOptionOrPerformAction(@NotNull Object element,
                                               String enteredText,
                                               @Nullable Project project,
                                               @Nullable Component component,
                                               @JdkConstants.InputEventMask int modifiers,
                                               @Nullable Computable<DataContext> dataContextProvider) {
    // invoke later to let the Goto Action popup close completely before the action is performed
    // and avoid focus issues if the action shows complicated popups itself
    ApplicationManager.getApplication().invokeLater(() -> {
      if (project != null && project.isDisposed()) return;

      if (element instanceof OptionDescription optionDescription) {
        if (optionDescription.hasExternalEditor()) {
          optionDescription.invokeInternalEditor();
        }
        else {
          ShowSettingsUtilImpl.showSettingsDialog(project, optionDescription.getConfigurableId(), enteredText);
        }
      }
      else {
        IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(() -> performAction(element, component, null, modifiers, dataContextProvider));
      }
    });
  }

  /** @deprecated Use {@link ActionManager#tryToExecute(AnAction, InputEvent, Component, String, boolean)} instead */
  @Deprecated(forRemoval = true)
  public static void performAction(@NotNull Object element, @Nullable Component component, @Nullable AnActionEvent e) {
    performAction(element, component, e, 0, null);
  }

  private static void performAction(@NotNull Object element,
                                    @Nullable Component component,
                                    @Nullable AnActionEvent e,
                                    @JdkConstants.InputEventMask int modifiers,
                                    @Nullable Computable<DataContext> dataContextProvider) {
    // element could be AnAction (SearchEverywhere)
    if (component == null) return;
    ApplicationManager.getApplication().invokeLater(() -> performActionImpl(element, component, e, modifiers, dataContextProvider));
  }

  private static void performActionImpl(@NotNull Object element,
                                        @NotNull Component component,
                                        @Nullable AnActionEvent e,
                                        @JdkConstants.InputEventMask int modifiers,
                                        @Nullable Computable<DataContext> dataContextProvider) {
    GotoActionModel.ActionWrapper wrapper = element instanceof AnAction ? null : (GotoActionModel.ActionWrapper)element;
    AnAction action = element instanceof AnAction ? (AnAction)element : wrapper.getAction();
    Presentation presentation = wrapper != null ? wrapper.getPresentation() :
                                action.getTemplatePresentation().clone();
    InputEvent inputEvent = e != null ? e.getInputEvent() : null;
    DataContext context = dataContextProvider == null ? null : dataContextProvider.get();
    if (context == null) {
      DataManager dataManager = DataManager.getInstance();
      context = dataManager != null ? dataManager.getDataContext(component) : DataContext.EMPTY_CONTEXT;
    }
    AnActionEvent event = new AnActionEvent(
      context, presentation, ActionPlaces.ACTION_SEARCH,
      ActionUiKind.SEARCH_POPUP, inputEvent, modifiers, ActionManager.getInstance());
    event.setInjectedContext(action.isInInjectedContext());
    Window window = SwingUtilities.getWindowAncestor(component);
    event.getPresentation().putClientProperty(POPUP_HANDLER, popup -> {
      if (window != null) {
        popup.showInCenterOf(window);
      }
      else {
        popup.showInFocusCenter();
      }
    });
    ActionUtil.performAction(action, event);
  }

  @Override
  protected boolean requiresProject() {
    return false;
  }
}
