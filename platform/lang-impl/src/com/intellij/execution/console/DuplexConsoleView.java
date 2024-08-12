// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.console;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.actions.ClearConsoleAction;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ObservableConsoleView;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.actions.ScrollToTheEndToolbarAction;
import com.intellij.openapi.editor.actions.ToggleUseSoftWrapsToolbarAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

public class DuplexConsoleView<S extends ConsoleView, T extends ConsoleView> extends JPanel
  implements ConsoleView, ObservableConsoleView, UiCompatibleDataProvider {

  private static final String PRIMARY_CONSOLE_PANEL = "PRIMARY_CONSOLE_PANEL";
  private static final String SECONDARY_CONSOLE_PANEL = "SECONDARY_CONSOLE_PANEL";

  private final @NotNull S myPrimaryConsoleView;
  private final @NotNull T mySecondaryConsoleView;
  private final @Nullable String myStateStorageKey;

  private boolean myPrimary;
  private @Nullable ProcessHandler myProcessHandler;
  private final @NotNull SwitchDuplexConsoleViewAction mySwitchConsoleAction;
  private boolean myDisableSwitchConsoleActionOnProcessEnd = true;
  private final Collection<DuplexConsoleListener> myListeners = new CopyOnWriteArraySet<>();

  public DuplexConsoleView(@NotNull S primaryConsoleView, @NotNull T secondaryConsoleView) {
    this(primaryConsoleView, secondaryConsoleView, null);
  }

  public DuplexConsoleView(@NotNull S primaryConsoleView, @NotNull T secondaryConsoleView, @Nullable String stateStorageKey) {
    super(new CardLayout());
    myPrimaryConsoleView = primaryConsoleView;
    mySecondaryConsoleView = secondaryConsoleView;
    myStateStorageKey = stateStorageKey;

    add(myPrimaryConsoleView.getComponent(), PRIMARY_CONSOLE_PANEL);
    add(mySecondaryConsoleView.getComponent(), SECONDARY_CONSOLE_PANEL);

    mySwitchConsoleAction = new SwitchDuplexConsoleViewAction();

    myPrimary = true;
    enableConsole(getStoredState());

    Disposer.register(this, myPrimaryConsoleView);
    Disposer.register(this, mySecondaryConsoleView);
  }

  public static <S extends ConsoleView, T extends ConsoleView> DuplexConsoleView<S, T> create(@NotNull S primary,
                                                                                              @NotNull T secondary,
                                                                                              @Nullable String stateStorageKey) {
    return new DuplexConsoleView<>(primary, secondary, stateStorageKey);
  }

  private void setStoredState(boolean primary) {
    if (myStateStorageKey != null) {
      PropertiesComponent.getInstance().setValue(myStateStorageKey, primary);
    }
  }

  private boolean getStoredState() {
    if (myStateStorageKey == null) {
      return false;
    }
    return PropertiesComponent.getInstance().getBoolean(myStateStorageKey);
  }

  public void enableConsole(boolean primary) {
    if (primary == myPrimary) {
      // nothing to do
      return;
    }

    CardLayout cl = (CardLayout)(getLayout());
    cl.show(this, primary ? PRIMARY_CONSOLE_PANEL : SECONDARY_CONSOLE_PANEL);

    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(getSubConsoleView(primary).getComponent(), true));

    myPrimary = primary;

    for (DuplexConsoleListener listener : myListeners) {
      listener.consoleEnabled(primary);
    }
  }

  public void addSwitchListener(@NotNull DuplexConsoleListener listener, @NotNull Disposable parent) {
    myListeners.add(listener);
    Disposer.register(parent, () -> myListeners.remove(listener));
  }

  public boolean isPrimaryConsoleEnabled() {
    return myPrimary;
  }

  public @NotNull S getPrimaryConsoleView() {
    return myPrimaryConsoleView;
  }

  public @NotNull T getSecondaryConsoleView() {
    return mySecondaryConsoleView;
  }

  public ConsoleView getSubConsoleView(boolean primary) {
    return primary ? getPrimaryConsoleView() : getSecondaryConsoleView();
  }

  @Override
  public void print(@NotNull String s, @NotNull ConsoleViewContentType contentType) {
    myPrimaryConsoleView.print(s, contentType);
    mySecondaryConsoleView.print(s, contentType);
  }

  @Override
  public void clear() {
    myPrimaryConsoleView.clear();
    mySecondaryConsoleView.clear();
  }

  @Override
  public void scrollTo(int offset) {
    myPrimaryConsoleView.scrollTo(offset);
    mySecondaryConsoleView.scrollTo(offset);
  }

  @Override
  public void attachToProcess(@NotNull ProcessHandler processHandler) {
    myProcessHandler = processHandler;

    myPrimaryConsoleView.attachToProcess(processHandler);
    mySecondaryConsoleView.attachToProcess(processHandler);
  }

  @Override
  public void setOutputPaused(boolean value) {
    myPrimaryConsoleView.setOutputPaused(value);
    mySecondaryConsoleView.setOutputPaused(value);
  }

  @Override
  public boolean isOutputPaused() {
    return false;
  }

  @Override
  public boolean hasDeferredOutput() {
    return myPrimaryConsoleView.hasDeferredOutput() && mySecondaryConsoleView.hasDeferredOutput();
  }

  @Override
  public void performWhenNoDeferredOutput(@NotNull Runnable runnable) {
  }

  @Override
  public void setHelpId(@NotNull String helpId) {
    myPrimaryConsoleView.setHelpId(helpId);
    mySecondaryConsoleView.setHelpId(helpId);
  }

  @Override
  public void addMessageFilter(@NotNull Filter filter) {
    myPrimaryConsoleView.addMessageFilter(filter);
    mySecondaryConsoleView.addMessageFilter(filter);
  }

  @Override
  public void printHyperlink(@NotNull String hyperlinkText, HyperlinkInfo info) {
    myPrimaryConsoleView.printHyperlink(hyperlinkText, info);
    mySecondaryConsoleView.printHyperlink(hyperlinkText, info);
  }

  @Override
  public int getContentSize() {
    return myPrimaryConsoleView.getContentSize();
  }

  @Override
  public boolean canPause() {
    return false;
  }


  @Override
  public AnAction @NotNull [] createConsoleActions() {
    List<AnAction> actions = new ArrayList<>();
    actions.addAll(mergeConsoleActions(Arrays.asList(myPrimaryConsoleView.createConsoleActions()),
                                       Arrays.asList(mySecondaryConsoleView.createConsoleActions())));
    actions.add(mySwitchConsoleAction);

    LanguageConsoleView langConsole = ContainerUtil.findInstance(Arrays.asList(myPrimaryConsoleView, mySecondaryConsoleView), LanguageConsoleView.class);
    ConsoleHistoryController controller = langConsole != null ? ConsoleHistoryController.getController(langConsole) : null;
    if (controller != null) actions.add(controller.getBrowseHistory());

    return actions.toArray(AnAction.EMPTY_ARRAY);
  }

  @Override
  public void allowHeavyFilters() {
    myPrimaryConsoleView.allowHeavyFilters();
  }

  @Override
  public @NotNull JComponent getComponent() {
    return this;
  }

  @Override
  public JComponent getPreferredFocusableComponent() {
    return this;
  }

  @Override
  public void dispose() {
    // registered children in constructor
  }

  @Override
  public void addChangeListener(@NotNull ChangeListener listener, @NotNull Disposable parent) {
    if (myPrimaryConsoleView instanceof ObservableConsoleView) {
      ((ObservableConsoleView)myPrimaryConsoleView).addChangeListener(listener, parent);
    }
    if (mySecondaryConsoleView instanceof ObservableConsoleView) {
      ((ObservableConsoleView)mySecondaryConsoleView).addChangeListener(listener, parent);
    }
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    ConsoleView consoleView = getSubConsoleView(isPrimaryConsoleEnabled());
    DataSink.uiDataSnapshot(sink, consoleView);
  }

  public @NotNull Presentation getSwitchConsoleActionPresentation() {
    return mySwitchConsoleAction.getTemplatePresentation();
  }

  public void setDisableSwitchConsoleActionOnProcessEnd(boolean disableSwitchConsoleActionOnProcessEnd) {
    myDisableSwitchConsoleActionOnProcessEnd = disableSwitchConsoleActionOnProcessEnd;
  }

  private @NotNull List<AnAction> mergeConsoleActions(@NotNull List<? extends AnAction> actions1, @NotNull Collection<? extends AnAction> actions2) {
    return ContainerUtil.map(actions1, action1 -> {
      final AnAction action2 = ContainerUtil.find(actions2, action -> action1.getClass() == action.getClass()
                                                                      && StringUtil.equals(action1.getTemplatePresentation().getText(),
                                                                                           action.getTemplatePresentation().getText()));
      if (action2 instanceof ToggleUseSoftWrapsToolbarAction) {
        return new MergedWrapTextAction(((ToggleUseSoftWrapsToolbarAction)action1), (ToggleUseSoftWrapsToolbarAction)action2);
      }
      else if (action2 instanceof ClearConsoleAction || action2 instanceof ScrollToTheEndToolbarAction) {
        return new MergedAction(action1, action2);
      }
      else {
        return action1;
      }
    });
  }

  private final class MergedWrapTextAction extends MergedToggleAction {

    private MergedWrapTextAction(@NotNull ToggleUseSoftWrapsToolbarAction action1, @NotNull ToggleUseSoftWrapsToolbarAction action2) {
      super(action1, action2);
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      super.setSelected(e, state);
      DuplexConsoleView.this.getComponent().revalidate();
    }
  }

  private final class SwitchDuplexConsoleViewAction extends ToggleAction implements DumbAware {

    SwitchDuplexConsoleViewAction() {
      super(ExecutionBundle.messagePointer("run.configuration.show.command.line.action.name"), AllIcons.Debugger.Console);
    }

    @Override
    public boolean isSelected(final @NotNull AnActionEvent event) {
      return !isPrimaryConsoleEnabled();
    }

    @Override
    public void setSelected(final @NotNull AnActionEvent event, final boolean flag) {
      enableConsole(!flag);
      setStoredState(!flag);
      ApplicationManager.getApplication().invokeLater(() -> update(event));
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
      super.update(event);
      if(!myDisableSwitchConsoleActionOnProcessEnd) return;

      final Presentation presentation = event.getPresentation();
      final boolean isRunning = myProcessHandler != null && !myProcessHandler.isProcessTerminated();
      if (isRunning) {
        presentation.setEnabled(true);
      }
      else {
        enableConsole(true);
        Toggleable.setSelected(presentation, false);
        presentation.setEnabled(false);
      }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
  }

  private static class MergedToggleAction extends ToggleAction implements DumbAware {
    private final @NotNull ToggleAction myAction1;
    private final @NotNull ToggleAction myAction2;

    private MergedToggleAction(@NotNull ToggleAction action1, @NotNull ToggleAction action2) {
      myAction1 = action1;
      myAction2 = action2;
      copyFrom(action1);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myAction1.isSelected(e);
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myAction1.setSelected(e, state);
      myAction2.setSelected(e, state);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
  }

  private static final class MergedAction extends AnAction implements DumbAware {
    private final @NotNull AnAction myAction1;
    private final @NotNull AnAction myAction2;

    private MergedAction(@NotNull AnAction action1, @NotNull AnAction action2) {
      myAction1 = action1;
      myAction2 = action2;
      copyFrom(action1);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myAction1.actionPerformed(e);
      myAction2.actionPerformed(e);
    }
  }

}
