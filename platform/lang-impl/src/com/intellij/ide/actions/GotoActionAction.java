/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.ide.actions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.DataManager;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.ide.util.gotoByName.ChooseByNamePopup;
import com.intellij.ide.util.gotoByName.GotoActionItemProvider;
import com.intellij.ide.util.gotoByName.GotoActionModel;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Set;

public class GotoActionAction extends GotoActionBase implements DumbAware {

  @Override
  public void gotoActionPerformed(@NotNull final AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final Component component = e.getData(PlatformDataKeys.CONTEXT_COMPONENT);
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    PsiFile file = e.getData(CommonDataKeys.PSI_FILE);

    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.popup.action");
    GotoActionModel model = new GotoActionModel(project, component, editor, file);
    GotoActionCallback<Object> callback = new GotoActionCallback<Object>() {
      @Override
      public void elementChosen(@NotNull ChooseByNamePopup popup, @NotNull Object element) {
        String enteredText = popup.getTrimmedText();
        openOptionOrPerformAction(((GotoActionModel.MatchedValue)element).value, enteredText, project, component, e);
      }
    };

    Pair<String, Integer> start = getInitialText(false, e);
    showNavigationPopup(callback, null, createPopup(project, model, start.first, start.second, component, e), false);
  }

  @Nullable
  private static ChooseByNamePopup createPopup(@Nullable Project project,
                                               @NotNull final GotoActionModel model,
                                               String initialText,
                                               int initialIndex,
                                               final Component component, 
                                               final AnActionEvent e) {
    ChooseByNamePopup oldPopup = project == null ? null : project.getUserData(ChooseByNamePopup.CHOOSE_BY_NAME_POPUP_IN_PROJECT_KEY);
    if (oldPopup != null) {
      oldPopup.close(false);
    }
    final ChooseByNamePopup popup = new ChooseByNamePopup(project, model, new GotoActionItemProvider(model), oldPopup, initialText, false, initialIndex) {
      @Override
      protected void initUI(Callback callback, ModalityState modalityState, boolean allowMultipleSelection) {
        super.initUI(callback, modalityState, allowMultipleSelection);
        myList.addListSelectionListener(new ListSelectionListener() {
          @Override
          public void valueChanged(ListSelectionEvent e) {
            Object value = myList.getSelectedValue();
            String text = getText(value);
            if (text != null && myDropdownPopup != null) {
              myDropdownPopup.setAdText(text, SwingConstants.LEFT);
            }
          }

          @Nullable
          private String getText(@Nullable Object o) {
            if (o instanceof GotoActionModel.MatchedValue) {
              GotoActionModel.MatchedValue mv = (GotoActionModel.MatchedValue)o;
              if (mv.value instanceof BooleanOptionDescription ||
                  mv.value instanceof GotoActionModel.ActionWrapper && ((GotoActionModel.ActionWrapper)mv.value).getAction() instanceof ToggleAction) {
                return "Press " + KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)) + " to toggle option";
              }
            }
            return getAdText();
          }
        });
      }

      @NotNull
      @Override
      protected Set<Object> filter(@NotNull Set<Object> elements) {
        return super.filter(model.sort(elements));
      }

      @Override
      protected boolean closeForbidden(boolean ok) {
        if (!ok) return false;
        Object element = getChosenElement();
        return element instanceof GotoActionModel.MatchedValue && processOptionInplace(((GotoActionModel.MatchedValue)element).value, this, component, e)
               || super.closeForbidden(true);
      }
    };

    if (project != null) {
      project.putUserData(ChooseByNamePopup.CHOOSE_BY_NAME_POPUP_IN_PROJECT_KEY, popup);
    }
    popup.addMouseClickListener(new MouseAdapter() {
      @Override
      public void mouseClicked(@NotNull MouseEvent me) {
        Object element = popup.getSelectionByPoint(me.getPoint());
        if (element instanceof GotoActionModel.MatchedValue) {
          if (processOptionInplace(((GotoActionModel.MatchedValue)element).value, popup, component, e)) {
            me.consume();
          }
        }
      }
    });
    return popup;
  }

  private static boolean processOptionInplace(Object value, ChooseByNamePopup popup, Component component, AnActionEvent e) {
    if (value instanceof BooleanOptionDescription) {
      BooleanOptionDescription option = (BooleanOptionDescription)value;
      option.setOptionState(!option.isOptionEnabled());
      repaint(popup);
      return true;
    }
    else if (value instanceof GotoActionModel.ActionWrapper) {
      AnAction action = ((GotoActionModel.ActionWrapper)value).getAction();
      if (action instanceof ToggleAction) {
        performAction(action, component, e);
        repaint(popup);
        return true;
      }
    }
    return false;
  }

  private static void repaint(@Nullable ChooseByNamePopup popup) {
    if (popup != null) {
      popup.repaintList();
    }
  }

  public static void openOptionOrPerformAction(@NotNull Object element,
                                               final String enteredText,
                                               final Project project,
                                               Component component,
                                               @Nullable AnActionEvent e) {
    if (element instanceof OptionDescription) {
      final String configurableId = ((OptionDescription)element).getConfigurableId();
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          ShowSettingsUtilImpl.showSettingsDialog(project, configurableId, enteredText);
        }
      });
    }
    else {
      performAction(element, component, e);
    }
  }

  public static void performAction(Object element, @Nullable final Component component, @Nullable final AnActionEvent e) {
    // element could be AnAction (SearchEverywhere)
    final AnAction action = element instanceof AnAction ? (AnAction)element : ((GotoActionModel.ActionWrapper)element).getAction();
    if (action != null) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          if (component == null) return;
          DataManager instance = DataManager.getInstance();
          DataContext context = instance != null ? instance.getDataContext(component) : DataContext.EMPTY_CONTEXT;
          InputEvent inputEvent = e == null ? null : e.getInputEvent();
          AnActionEvent event = AnActionEvent.createFromAnAction(action, inputEvent, ActionPlaces.ACTION_SEARCH, context);

          if (ActionUtil.lastUpdateAndCheckDumb(action, event, false)) {
            if (action instanceof ActionGroup && ((ActionGroup)action).getChildren(event).length > 0) {
              ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
                event.getPresentation().getText(), (ActionGroup)action, context, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false);
              Window window = SwingUtilities.getWindowAncestor(component);
              if (window != null) {
                popup.showInCenterOf(window);
              }
              else {
                popup.showInFocusCenter();
              }
            } 
            else {
              ActionUtil.performActionDumbAware(action, event);
            }
          }
        }
      }, ModalityState.NON_MODAL);
    }
  }

  @Override
  protected boolean requiresProject() {
    return false;
  }
}