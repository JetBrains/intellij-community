/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.impl.ActionShortcutRestrictions;
import com.intellij.openapi.keymap.impl.KeymapManagerImpl;
import com.intellij.openapi.keymap.impl.ui.KeymapPanel;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.HeldDownKeyListener;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.util.Set;

import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;

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
        if (project != null) {
          // if the chosen action displays another popup, don't populate it automatically with the text from this popup
          project.putUserData(ChooseByNamePopup.CHOOSE_BY_NAME_POPUP_IN_PROJECT_KEY, null);
        }
        String enteredText = popup.getTrimmedText();
        openOptionOrPerformAction(((GotoActionModel.MatchedValue)element).value, enteredText, project, component, e);
      }
    };

    Pair<String, Integer> start = getInitialText(false, e);
    showNavigationPopup(callback, null, createPopup(project, model, start.first, start.second, component, e), false);
  }

  @NotNull
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
    final Disposable disposable = Disposer.newDisposable();
    final ChooseByNamePopup popup = new ChooseByNamePopup(project, model, new GotoActionItemProvider(model), oldPopup, initialText, false, initialIndex) {
      private boolean myPaintInternalInfo;

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

            String description = getValueDescription(value);
            ActionMenu.showDescriptionInStatusBar(true, myList, description);
          }

          @Nullable
          private String getText(@Nullable Object o) {
            if (o instanceof GotoActionModel.MatchedValue) {
              GotoActionModel.MatchedValue mv = (GotoActionModel.MatchedValue)o;

              if (myPaintInternalInfo) {
                if (mv.value instanceof GotoActionModel.ActionWrapper) {
                  AnAction action = ((GotoActionModel.ActionWrapper)mv.value).getAction();
                  String actionId = ActionManager.getInstance().getId(action);
                  return StringUtil.notNullize(actionId, "class: " + action.getClass().getName());
                }
              }

              if (mv.value instanceof BooleanOptionDescription ||
                  mv.value instanceof GotoActionModel.ActionWrapper && ((GotoActionModel.ActionWrapper)mv.value).getAction() instanceof ToggleAction) {
                return "Press " + KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)) + " to toggle option";
              }
            }
            return getAdText();
          }
        });
        myList.addMouseMotionListener(new MouseMotionAdapter() {
          @Override
          public void mouseMoved(MouseEvent e) {
            int index = myList.locationToIndex(e.getPoint());
            if (index == -1) return;
            Object value = myList.getModel().getElementAt(index);
            String description = getValueDescription(value);
            ActionMenu.showDescriptionInStatusBar(true, myList, description);
          }
        });

        if (Registry.is("show.configurables.ids.in.settings")) {
          new HeldDownKeyListener() {
            @Override
            protected void heldKeyTriggered(JComponent component, boolean pressed) {
              myPaintInternalInfo = pressed;
              // an easy way to repaint the AdText
              ListSelectionEvent event = new ListSelectionEvent(this, -1, -1, false);
              for (ListSelectionListener listener : myList.getListSelectionListeners()) {
                listener.valueChanged(event);
              }
            }
          }.installOn(myTextField);
        }
      }

      @Nullable
      private String getValueDescription(@Nullable Object value) {
        if (value instanceof GotoActionModel.MatchedValue) {
          GotoActionModel.MatchedValue mv = (GotoActionModel.MatchedValue)value;
          if (mv.value instanceof GotoActionModel.ActionWrapper) {
            AnAction action = ((GotoActionModel.ActionWrapper)mv.value).getAction();
            return action.getTemplatePresentation().getDescription();
          }
        }
        return null;
      }

      @NotNull
      @Override
      protected Set<Object> filter(@NotNull Set<Object> elements) {
        return super.filter(model.sortItems(elements));
      }

      @Override
      protected boolean closeForbidden(boolean ok) {
        if (!ok) return false;
        Object element = getChosenElement();
        return element instanceof GotoActionModel.MatchedValue && processOptionInplace(((GotoActionModel.MatchedValue)element).value, this, component, e)
               || super.closeForbidden(true);
      }

      @Override
      public void setDisposed(boolean disposedFlag) {
        super.setDisposed(disposedFlag);
        Disposer.dispose(disposable);

        ActionMenu.showDescriptionInStatusBar(true, myList, null);

        for (ListSelectionListener listener : myList.getListSelectionListeners()) {
          myList.removeListSelectionListener(listener);
        }
        UIUtil.dispose(myList);
      }
    };

    ApplicationManager.getApplication().getMessageBus().connect(disposable).subscribe(ProgressWindow.TOPIC, pw -> Disposer.register(pw, new Disposable() {
      @Override
      public void dispose() {
        if (!popup.checkDisposed()) {
          popup.repaintList();
        }
      }
    }));

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

    ShortcutSet shortcutSet = getActiveKeymapShortcuts(IdeActions.ACTION_SHOW_INTENTION_ACTIONS);

    new DumbAwareAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        Object o = popup.getChosenElement();
        if (o instanceof GotoActionModel.MatchedValue) {
          Comparable value = ((GotoActionModel.MatchedValue)o).value;
          if (value instanceof GotoActionModel.ActionWrapper) {
            GotoActionModel.ActionWrapper aw = (GotoActionModel.ActionWrapper)value;
            boolean available = aw.isAvailable();
            if (available) {
              AnAction action = aw.getAction();
              String id = ActionManager.getInstance().getId(action);
              KeymapManagerImpl km = ((KeymapManagerImpl)KeymapManager.getInstance());
              Keymap k = km.getActiveKeymap();
              if (k == null || !k.canModify()) return;
              KeymapPanel.addKeyboardShortcut(id, ActionShortcutRestrictions.getInstance().getForActionId(id), k, component);
              popup.repaintListImmediate();
            }
          }
        }
      }
    }.registerCustomShortcutSet(shortcutSet, popup.getTextField(), disposable);
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
        performAction(action, component, e, () -> repaint(popup));
        return true;
      }
    }
    return false;
  }

  private static void repaint(@Nullable ChooseByNamePopup popup) {
    if (popup != null) {
      popup.repaintListImmediate();
    }
  }

  public static void openOptionOrPerformAction(@NotNull Object element,
                                               final String enteredText,
                                               @Nullable final Project project,
                                               Component component,
                                               @Nullable AnActionEvent e) {
    if (element instanceof OptionDescription) {
      OptionDescription optionDescription = (OptionDescription)element;
      final String configurableId = optionDescription.getConfigurableId();
      Disposable disposable = project != null ? project : ApplicationManager.getApplication();
      TransactionGuard guard = TransactionGuard.getInstance();
      if (optionDescription.hasExternalEditor()) {
        guard.submitTransactionLater(disposable, () -> optionDescription.invokeInternalEditor());
      } else {
        guard.submitTransactionLater(disposable, () -> ShowSettingsUtilImpl.showSettingsDialog(project, configurableId, enteredText));
      }
    }
    else {
      ApplicationManager.getApplication().invokeLater(() -> IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(
        () -> performAction(element, component, e)));
    }
  }

  public static void performAction(Object element, @Nullable final Component component, @Nullable final AnActionEvent e) {
    performAction(element, component, e, null);
  }

  public static void performAction(Object element,
                                   @Nullable final Component component,
                                   @Nullable final AnActionEvent e,
                                   @Nullable final Runnable callback) {
    // element could be AnAction (SearchEverywhere)
    if (component == null) return;
    final AnAction action = element instanceof AnAction ? (AnAction)element : ((GotoActionModel.ActionWrapper)element).getAction();
    TransactionGuard.getInstance().submitTransactionLater(ApplicationManager.getApplication(), () -> {
        DataManager instance = DataManager.getInstance();
        DataContext context = instance != null ? instance.getDataContext(component) : DataContext.EMPTY_CONTEXT;
        InputEvent inputEvent = e == null ? null : e.getInputEvent();
        AnActionEvent event = AnActionEvent.createFromAnAction(action, inputEvent, ActionPlaces.ACTION_SEARCH, context);

        if (ActionUtil.lastUpdateAndCheckDumb(action, event, false)) {
          if (action instanceof ActionGroup && ((ActionGroup)action).getChildren(event).length > 0) {
            ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
              event.getPresentation().getText(), (ActionGroup)action, context, false, callback, -1);
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
            if (callback != null) callback.run();
          }
        }
    });
  }

  @Override
  protected boolean requiresProject() {
    return false;
  }
}