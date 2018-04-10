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

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.ComponentContainer;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Experimental
public class CompositeView<T extends ComponentContainer> extends JPanel implements ComponentContainer, DataProvider {
  private final Map<String, T> myViewMap = ContainerUtil.newConcurrentMap();
  private final String mySelectionStateKey;
  private final AtomicReference<String> myEnabledViewRef = new AtomicReference<>();
  @NotNull
  private final SwitchViewAction mySwitchViewAction;

  public CompositeView(String selectionStateKey) {
    super(new CardLayout());
    mySelectionStateKey = selectionStateKey;
    mySwitchViewAction = new SwitchViewAction();
  }

  public void addView(T view, String viewName, boolean enable) {
    T oldView = getView(viewName);
    if (oldView != null) {
      remove(oldView.getComponent());
      Disposer.dispose(oldView);
    }
    myViewMap.put(viewName, view);
    add(view.getComponent(), viewName);

    String storedState = getStoredState();
    if ((storedState != null && storedState.equals(viewName)) || storedState == null && enable) {
      enableView(viewName);
      setStoredState(viewName);
    }
    Disposer.register(this, view);
  }

  public void enableView(@NotNull String viewName) {
    if (!StringUtil.equals(viewName, myEnabledViewRef.get())) {
      myEnabledViewRef.set(viewName);
      CardLayout cl = (CardLayout)(getLayout());
      cl.show(this, viewName);
    }
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
      ComponentContainer view = getView(viewName);
      if (view != null) {
        IdeFocusManager.getGlobalInstance().requestFocus(view.getPreferredFocusableComponent(), true);
      }
    });
  }

  public boolean isViewEnabled(String viewName) {
    return StringUtil.equals(myEnabledViewRef.get(), viewName);
  }

  public T getView(@NotNull String viewName) {
    return myViewMap.get(viewName);
  }

  @Nullable
  public <U> U getView(@NotNull String viewName, @NotNull Class<U> viewClass) {
    T view = getView(viewName);
    return viewClass.isInstance(view) ? viewClass.cast(view) : null;
  }

  @NotNull
  public AnAction[] createConsoleActions() {
    return AnAction.EMPTY_ARRAY;
  }

  @NotNull
  public AnAction[] getSwitchActions() {
    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.addSeparator();
    actionGroup.add(mySwitchViewAction);
    return new AnAction[]{actionGroup};
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
    String enabledViewName = myEnabledViewRef.get();
    if (enabledViewName != null) {
      T enabledView = getView(enabledViewName);
      if (enabledView instanceof DataProvider) {
        Object data = ((DataProvider)enabledView).getData(dataId);
        if (data != null) return data;
      }
    }
    return null;
  }

  private void setStoredState(String viewName) {
    if (mySelectionStateKey != null) {
      PropertiesComponent.getInstance().setValue(mySelectionStateKey, viewName);
    }
  }

  @Nullable
  private String getStoredState() {
    return mySelectionStateKey == null ? null : PropertiesComponent.getInstance().getValue(mySelectionStateKey);
  }

  private class SwitchViewAction extends ToggleAction implements DumbAware {
    public SwitchViewAction() {
      super("Toggle view", null,
            AllIcons.Actions.ChangeView);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      final Presentation presentation = e.getPresentation();
      if (myViewMap.size() <= 1) {
        presentation.setEnabled(false);
      }
      else {
        presentation.setEnabled(true);
        presentation.putClientProperty(SELECTED_PROPERTY, isSelected(e));
      }
    }

    @Override
    public boolean isSelected(final AnActionEvent event) {
      String enabledViewName = myEnabledViewRef.get();
      if (enabledViewName == null) return true;
      Set<String> viewNames = myViewMap.keySet();
      return viewNames.isEmpty() || enabledViewName.equals(viewNames.iterator().next());
    }

    @Override
    public void setSelected(final AnActionEvent event, final boolean flag) {
      if (myViewMap.size() > 1) {
        List<String> names = new ArrayList<>(myViewMap.keySet());
        String viewName = flag ? names.get(0) : names.get(1);
        enableView(viewName);
        setStoredState(viewName);
        ApplicationManager.getApplication().invokeLater(() -> update(event));
      }
    }
  }
}
