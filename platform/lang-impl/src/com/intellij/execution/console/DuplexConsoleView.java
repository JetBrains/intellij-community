package com.intellij.execution.console;

import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ObservableConsoleView;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

public class DuplexConsoleView<S extends ConsoleView, T extends ConsoleView> extends JPanel implements ConsoleView, ObservableConsoleView {
  private final static String PRIMARY_CONSOLE_PANEL = "PRIMARY_CONSOLE_PANEL";
  private final static String SECONDARY_CONSOLE_PANEL = "SECONDARY_CONSOLE_PANEL";

  @NotNull
  private final S myPrimaryConsoleView;
  @NotNull
  private final T mySecondaryConsoleView;
  @Nullable
  private final String myStateStorageKey;

  private boolean myPrimary;
  @Nullable
  private ProcessHandler myProcessHandler;
  @NotNull
  private final SwitchDuplexConsoleViewAction mySwitchConsoleAction;


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
    return new DuplexConsoleView<S, T>(primary, secondary, stateStorageKey);
  }

  private void setStoredState(boolean primary) {
    if (myStateStorageKey != null) {
      PropertiesComponent.getInstance().setValue(myStateStorageKey, String.valueOf(primary));
    }
  }

  private boolean getStoredState() {
    if (myStateStorageKey == null) {
      return false;
    }
    return PropertiesComponent.getInstance().getBoolean(myStateStorageKey, false);
  }

  public void enableConsole(boolean primary) {
    if (primary == myPrimary) {
      // nothing to do
      return;
    }

    CardLayout cl = (CardLayout)(getLayout());
    cl.show(this, primary ? PRIMARY_CONSOLE_PANEL : SECONDARY_CONSOLE_PANEL);

    getSubConsoleView(primary).getComponent().requestFocus();

    myPrimary = primary;
  }

  public boolean isPrimaryConsoleEnabled() {
    return myPrimary;
  }

  @NotNull
  public S getPrimaryConsoleView() {
    return myPrimaryConsoleView;
  }

  @NotNull
  public T getSecondaryConsoleView() {
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
  public void attachToProcess(ProcessHandler processHandler) {
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
  public void performWhenNoDeferredOutput(Runnable runnable) {
  }

  @Override
  public void setHelpId(String helpId) {
    myPrimaryConsoleView.setHelpId(helpId);
    mySecondaryConsoleView.setHelpId(helpId);
  }

  @Override
  public void addMessageFilter(Filter filter) {
    myPrimaryConsoleView.addMessageFilter(filter);
    mySecondaryConsoleView.addMessageFilter(filter);
  }

  @Override
  public void printHyperlink(String hyperlinkText, HyperlinkInfo info) {
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


  @NotNull
  @Override
  public AnAction[] createConsoleActions() {
    List<AnAction> actions = Lists.newArrayList();
    actions.addAll(Arrays.asList(myPrimaryConsoleView.createConsoleActions()));
    actions.add(mySwitchConsoleAction);

    LanguageConsoleView langConsole = ContainerUtil.findInstance(Arrays.asList(myPrimaryConsoleView, mySecondaryConsoleView), LanguageConsoleView.class);
    ConsoleHistoryController controller = langConsole != null ? ConsoleHistoryController.getController(langConsole) : null;
    if (controller != null) actions.add(controller.getBrowseHistory());

    return ArrayUtil.toObjectArray(actions, AnAction.class);
  }

  @Override
  public void allowHeavyFilters() {
    myPrimaryConsoleView.allowHeavyFilters();
  }

  @Override
  public JComponent getComponent() {
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

  @NotNull
  public Presentation getSwitchConsoleActionPresentation() {
    return mySwitchConsoleAction.getTemplatePresentation();
  }

  private class SwitchDuplexConsoleViewAction extends ToggleAction implements DumbAware {

    public SwitchDuplexConsoleViewAction() {
      super(ExecutionBundle.message("run.configuration.show.command.line.action.name"), null,
            AllIcons.Debugger.ToolConsole);
    }

    @Override
    public boolean isSelected(final AnActionEvent event) {
      return !isPrimaryConsoleEnabled();
    }

    @Override
    public void setSelected(final AnActionEvent event, final boolean flag) {
      enableConsole(!flag);
      setStoredState(!flag);
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          update(event);
        }
      });
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
      super.update(event);
      final Presentation presentation = event.getPresentation();
      final boolean isRunning = myProcessHandler != null && !myProcessHandler.isProcessTerminated();
      if (isRunning) {
        presentation.setEnabled(true);
      }
      else {
        enableConsole(true);
        presentation.putClientProperty(SELECTED_PROPERTY, false);
        presentation.setEnabled(false);
      }
    }
  }
}
