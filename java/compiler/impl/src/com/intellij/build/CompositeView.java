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
package com.intellij.build;

import com.intellij.execution.console.ConsoleHistoryController;
import com.intellij.execution.console.LanguageConsoleView;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.ComponentContainer;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

/**
 * @author Vladislav.Soroka
 */
public class CompositeView<S extends ComponentContainer, T extends ComponentContainer> extends JPanel
  implements ComponentContainer, DataProvider {
  private final static String PRIMARY_PANEL = "PRIMARY_PANEL";
  private final static String SECONDARY_PANEL = "SECONDARY_PANEL";

  @NotNull
  private final S myPrimaryView;
  @NotNull
  private final T mySecondaryView;
  private final String mySelectionStateKey;
  private final boolean myPrimaryViewEnabledByDefault;
  private boolean myPrimary;
  @NotNull
  private final SwitchViewAction mySwitchViewAction;

  public CompositeView(@NotNull S primaryView, @NotNull T secondaryView, String selectionStateKey, boolean isPrimaryViewEnabledByDefault) {
    super(new CardLayout());
    myPrimaryView = primaryView;
    mySecondaryView = secondaryView;
    mySelectionStateKey = selectionStateKey;

    add(myPrimaryView.getComponent(), PRIMARY_PANEL);
    add(mySecondaryView.getComponent(), SECONDARY_PANEL);

    mySwitchViewAction = new SwitchViewAction();

    myPrimary = true;
    myPrimaryViewEnabledByDefault = isPrimaryViewEnabledByDefault;
    enableView(getStoredState());

    Disposer.register(this, myPrimaryView);
    Disposer.register(this, mySecondaryView);
  }

  public void enableView(boolean primary) {
    if (primary == myPrimary) return;

    CardLayout cl = (CardLayout)(getLayout());
    cl.show(this, primary ? PRIMARY_PANEL : SECONDARY_PANEL);
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
      IdeFocusManager.getGlobalInstance().requestFocus(getView(primary).getPreferredFocusableComponent(), true);
    });
    myPrimary = primary;
  }

  public boolean isPrimaryConsoleEnabled() {
    return myPrimary;
  }

  @NotNull
  public S getPrimaryView() {
    return myPrimaryView;
  }

  @NotNull
  public T getSecondaryView() {
    return mySecondaryView;
  }

  public ComponentContainer getView(boolean primary) {
    return primary ? getPrimaryView() : getSecondaryView();
  }

  @NotNull
  public AnAction[] createConsoleActions() {
    List<AnAction> actions = ContainerUtil.newArrayList();
    actions.add(mySwitchViewAction);
    LanguageConsoleView langConsole =
      ContainerUtil.findInstance(Arrays.asList(myPrimaryView, mySecondaryView), LanguageConsoleView.class);
    ConsoleHistoryController controller = langConsole != null ? ConsoleHistoryController.getController(langConsole) : null;
    if (controller != null) actions.add(controller.getBrowseHistory());

    return ArrayUtil.toObjectArray(actions, AnAction.class);
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
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    final ComponentContainer consoleView = getView(isPrimaryConsoleEnabled());
    return consoleView instanceof DataProvider ? ((DataProvider)consoleView).getData(dataId) : null;
  }

  private void setStoredState(boolean primary) {
    if (mySelectionStateKey != null) {
      PropertiesComponent.getInstance().setValue(mySelectionStateKey, primary, myPrimaryViewEnabledByDefault);
    }
  }

  private boolean getStoredState() {
    if (mySelectionStateKey == null) {
      return false;
    }
    return PropertiesComponent.getInstance().getBoolean(mySelectionStateKey, myPrimaryViewEnabledByDefault);
  }

  private class SwitchViewAction extends ToggleAction implements DumbAware {
    public SwitchViewAction() {
      super("Toggle view", null,
            AllIcons.Actions.ChangeView);
    }

    @Override
    public boolean isSelected(final AnActionEvent event) {
      return !isPrimaryConsoleEnabled();
    }

    @Override
    public void setSelected(final AnActionEvent event, final boolean flag) {
      enableView(!flag);
      setStoredState(!flag);
      ApplicationManager.getApplication().invokeLater(() -> update(event));
    }
  }
}
