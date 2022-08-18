// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.CommonBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.DialogTitle;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * The DialogBuilder is a simpler alternative to {@link DialogWrapper}.
 * There is no need to create a subclass (which is needed in the DialogWrapper), which can be nice for simple dialogs.
 */
public class DialogBuilder implements Disposable {

  @NonNls public static final String REQUEST_FOCUS_ENABLED = "requestFocusEnabled";

  private JComponent myCenterPanel;
  private JComponent myNorthPanel;
  private @DialogTitle String myTitle;
  private JComponent myPreferedFocusComponent;
  private @NonNls String myDimensionServiceKey;
  private ArrayList<ActionDescriptor> myActions = null;
  private final MyDialogWrapper myDialogWrapper;
  private Runnable myCancelOperation = null;
  private Runnable myOkOperation = null;

  public int show() {
    return showImpl(true).getExitCode();
  }

  public boolean showAndGet() {
    return showImpl(true).isOK();
  }

  public void showNotModal() {
    showImpl(false);
  }

  public DialogBuilder(@Nullable Project project) {
    myDialogWrapper = new MyDialogWrapper(project, true);
    Disposer.register(myDialogWrapper.getDisposable(), this);
  }

  public DialogBuilder(@Nullable Component parent) {
    myDialogWrapper = new MyDialogWrapper(parent, true);
    Disposer.register(myDialogWrapper.getDisposable(), this);
  }

  public DialogBuilder() {
    this((Project)null);
  }

  @Override
  public void dispose() {
  }

  private MyDialogWrapper showImpl(boolean isModal) {
    myDialogWrapper.setTitle(myTitle);
    myDialogWrapper.init();
    myDialogWrapper.setModal(isModal);
    myDialogWrapper.show();
    if (isModal) {
      myDialogWrapper.dispose();
    }
    return myDialogWrapper;
  }

  public void setCenterPanel(JComponent centerPanel) {
    myCenterPanel = centerPanel;
  }

  @NotNull
  public DialogBuilder centerPanel(@NotNull JComponent centerPanel) {
    myCenterPanel = centerPanel;
    return this;
  }

  @NotNull
  public DialogBuilder setNorthPanel(@NotNull JComponent northPanel) {
    myNorthPanel = northPanel;
    return this;
  }

  public void setTitle(@DialogTitle String title) {
    myTitle = title;
  }

  @NotNull
  public DialogBuilder title(@NotNull @DialogTitle String title) {
    myTitle = title;
    return this;
  }

  public void setPreferredFocusComponent(JComponent component) {
    myPreferedFocusComponent = component;
  }

  public void setDimensionServiceKey(@NonNls String dimensionServiceKey) {
    myDimensionServiceKey = dimensionServiceKey;
  }

  public DialogBuilder dimensionKey(@NotNull @NlsSafe String dimensionServiceKey) {
    myDimensionServiceKey = dimensionServiceKey;
    return this;
  }

  public void addAction(Action action) {
    addActionDescriptor(new CustomActionDescriptor(action));
  }

  public <T extends ActionDescriptor> T addActionDescriptor(T actionDescriptor) {
    getActionDescriptors().add(actionDescriptor);
    return actionDescriptor;
  }

  private ArrayList<ActionDescriptor> getActionDescriptors() {
    if (myActions == null) removeAllActions();
    return myActions;
  }

  public void setActionDescriptors(ActionDescriptor... descriptors) {
    removeAllActions();
    ContainerUtil.addAll(myActions, descriptors);
  }

  public void removeAllActions() {
    myActions = new ArrayList<>();
  }

  public Window getWindow() {
    return myDialogWrapper.getWindow();
  }

  public CustomizableAction addOkAction() {
    return addActionDescriptor(new OkActionDescriptor());
  }

  public CustomizableAction addCancelAction() {
    return addActionDescriptor(new CancelActionDescriptor());
  }

  public CustomizableAction addCloseButton() {
    CustomizableAction closeAction = addOkAction();
    closeAction.setText(CommonBundle.getCloseButtonText());
    return closeAction;
  }

  public void addDisposable(@NotNull Disposable disposable) {
    Disposer.register(this, disposable);
  }

  /**
   * @deprecated Dialog action buttons should be right-aligned.
   */
  @Deprecated(forRemoval = true)
  public void setButtonsAlignment(@MagicConstant(intValues = {SwingConstants.CENTER, SwingConstants.RIGHT}) int alignment) {
    myDialogWrapper.setButtonsAlignment(alignment);
  }

  public DialogWrapper getDialogWrapper() {
    return myDialogWrapper;
  }

  public void showModal(boolean modal) {
    if (modal) {
      show();
    }
    else {
      showNotModal();
    }
  }

  public void setHelpId(@NonNls String helpId) {
    myDialogWrapper.setHelpId(helpId);
  }

  public void setCancelOperation(Runnable runnable) {
    myCancelOperation = runnable;
  }

  public void setOkOperation(Runnable runnable) {
    myOkOperation = runnable;
  }

  public void setOkActionEnabled(final boolean isEnabled) {
    myDialogWrapper.setOKActionEnabled(isEnabled);
  }

  @NotNull
  public DialogBuilder okActionEnabled(boolean isEnabled) {
    myDialogWrapper.setOKActionEnabled(isEnabled);
    return this;
  }

  @NotNull
  public DialogBuilder resizable(boolean resizable) {
    myDialogWrapper.setResizable(resizable);
    return this;
  }

  public CustomizableAction getOkAction() {
    return get(getActionDescriptors(), OkActionDescriptor.class);
  }

  private static CustomizableAction get(final List<? extends ActionDescriptor> actionDescriptors, final Class aClass) {
    for (ActionDescriptor actionDescriptor : actionDescriptors) {
      if (actionDescriptor.getClass().isAssignableFrom(aClass)) return (CustomizableAction)actionDescriptor;
    }
    return null;
  }

  public CustomizableAction getCancelAction() {
    return get(getActionDescriptors(), CancelActionDescriptor.class);
  }

  public Component getCenterPanel() {
    return myCenterPanel;
  }

  public interface ActionDescriptor {
    Action getAction(DialogWrapper dialogWrapper);
  }

  public abstract static class DialogActionDescriptor implements ActionDescriptor {
    private final @NlsActions.ActionText String myName;
    private final Object myMnemonicChar;
    private boolean myIsDefault = false;

    protected DialogActionDescriptor(@NlsActions.ActionText String name, int mnemonicChar) {
      myName = name;
      myMnemonicChar = mnemonicChar == -1 ? null : Integer.valueOf(mnemonicChar);
    }

    @Override
    public Action getAction(DialogWrapper dialogWrapper) {
      Action action = createAction(dialogWrapper);
      action.putValue(Action.NAME, myName);
      if (myMnemonicChar != null) action.putValue(Action.MNEMONIC_KEY, myMnemonicChar);
      if (myIsDefault) action.putValue(Action.DEFAULT, Boolean.TRUE);
      return action;
    }

    public void setDefault(boolean isDefault) {
      myIsDefault = isDefault;
    }

    protected abstract Action createAction(DialogWrapper dialogWrapper);
  }

  public static class CloseDialogAction extends DialogActionDescriptor {
    private final int myExitCode;

    public CloseDialogAction() {
      this(CommonBundle.getCloseButtonText(), -1, DialogWrapper.CLOSE_EXIT_CODE);
    }

    public CloseDialogAction(@NlsActions.ActionText String name, int mnemonicChar, int exitCode) {
      super(name, mnemonicChar);
      myExitCode = exitCode;
    }

    public static CloseDialogAction createDefault(String name, int mnemonicChar, int exitCode) {
      CloseDialogAction closeDialogAction = new CloseDialogAction(name, mnemonicChar, exitCode);
      closeDialogAction.setDefault(true);
      return closeDialogAction;
    }

    @Override
    protected Action createAction(final DialogWrapper dialogWrapper) {
      return new AbstractAction(){
        @Override
        public void actionPerformed(@NotNull ActionEvent e) {
          dialogWrapper.close(myExitCode);
        }
      };
    }
  }

  public interface CustomizableAction {
    void setText(@NlsActions.ActionText String text);
  }

  public static class CustomActionDescriptor implements ActionDescriptor {
    private final Action myAction;

    public CustomActionDescriptor(Action action) {
      myAction = action;
    }

    @Override
    public Action getAction(DialogWrapper dialogWrapper) {
      return myAction;
    }
  }

  private abstract static class BuiltinAction implements ActionDescriptor, CustomizableAction {
    protected @NlsActions.ActionText String myText = null;

    @Override
    public void setText(String text) {
      myText = text;
    }

    @Override
    public Action getAction(DialogWrapper dialogWrapper) {
      Action builtinAction = getBuiltinAction((MyDialogWrapper)dialogWrapper);
      if (myText != null) builtinAction.putValue(Action.NAME, myText);
      return builtinAction;
    }

    protected abstract Action getBuiltinAction(MyDialogWrapper dialogWrapper);
  }

  public static class OkActionDescriptor extends BuiltinAction {
    @Override
    protected Action getBuiltinAction(MyDialogWrapper dialogWrapper) {
      return dialogWrapper.getOKAction();
    }
  }

  public static class CancelActionDescriptor extends BuiltinAction {
    @Override
    protected Action getBuiltinAction(MyDialogWrapper dialogWrapper) {
      return dialogWrapper.getCancelAction();
    }
  }

  private final class MyDialogWrapper extends DialogWrapper {
    private @NonNls String myHelpId = null;
    private MyDialogWrapper(@Nullable Project project, boolean canBeParent) {
      super(project, canBeParent);
    }

    private MyDialogWrapper(Component parent, boolean canBeParent) {
      super(parent, canBeParent);
    }

    public void setHelpId(@NonNls String helpId) {
      myHelpId = helpId;
    }

    @Nullable
    @Override
    protected String getHelpId() {
      return myHelpId;
    }

    @Override
    public void init() { super.init(); }
    @Override
    @NotNull
    public Action getOKAction() { return super.getOKAction(); } // Make it public
    @Override
    @NotNull
    public Action getCancelAction() { return super.getCancelAction(); } // Make it public

    @Override
    protected JComponent createCenterPanel() { return myCenterPanel; }

    @Override
    protected JComponent createNorthPanel() { return myNorthPanel; }

    @Override
    public void dispose() {
      myPreferedFocusComponent = null;
      super.dispose();
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      if (myPreferedFocusComponent != null) return myPreferedFocusComponent;
      FocusTraversalPolicy focusTraversalPolicy = null;
      Container container = myCenterPanel;
      while (container != null && (focusTraversalPolicy = container.getFocusTraversalPolicy()) == null && !(container instanceof Window)) {
        container = container.getParent();
      }
      if (focusTraversalPolicy == null) return null;
      Component component = focusTraversalPolicy.getDefaultComponent(myCenterPanel);
      while (!(component instanceof JComponent) && component != null) {
        component = focusTraversalPolicy.getComponentAfter(myCenterPanel, component);
      }
      return (JComponent)component;
    }

    @Override
    protected String getDimensionServiceKey() {
      return myDimensionServiceKey;
    }

    @Override
    protected JButton createJButtonForAction(Action action) {
      JButton button = super.createJButtonForAction(action);
      Object value = action.getValue(REQUEST_FOCUS_ENABLED);
      if (value instanceof Boolean) button.setRequestFocusEnabled(((Boolean)value).booleanValue());
      return button;
    }

    @Override
    public void doCancelAction() {
      if (!getCancelAction().isEnabled()) return;
      if (myCancelOperation != null) {
        myCancelOperation.run();
      }
      else {
        super.doCancelAction();
      }
    }

    @Override
    protected void doOKAction() {
      if (myOkOperation != null) {
        myOkOperation.run();
      }
      else {
        super.doOKAction();
      }
    }

    @Override
    protected Action @NotNull [] createActions() {
      if (myActions == null) return super.createActions();
      ArrayList<Action> actions = new ArrayList<>(myActions.size());
      for (ActionDescriptor actionDescriptor : myActions) {
        actions.add(actionDescriptor.getAction(this));
      }
      if (myHelpId != null) actions.add(getHelpAction());
      return actions.toArray(new Action[0]);
    }
  }

  public void setErrorText(@NlsContexts.DialogMessage @Nullable final String text) {
    myDialogWrapper.setErrorText(text);
  }

  public void setErrorText(@NlsContexts.DialogMessage @Nullable final String text, @Nullable JComponent component) {
    myDialogWrapper.setErrorText(text, component);
  }
}
