// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.impl.dataRules.GetDataRule;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.EdtDataContext;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.KeyedExtensionCollector;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.FloatingDecorator;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.SwingHelper;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.ide.impl.DataValidators.validOrNull;

public class DataManagerImpl extends DataManager {
  private static final Logger LOG = Logger.getInstance(DataManagerImpl.class);

  private static final ThreadLocal<int[]> ourGetDataLevel = ThreadLocal.withInitial(() -> new int[1]);

  private final KeyedExtensionCollector<GetDataRule, String> myDataRuleCollector = new KeyedExtensionCollector<>(GetDataRule.EP_NAME);

  public DataManagerImpl() {
  }

  @ApiStatus.Internal
  public @Nullable Object getDataFromProvider(final @NotNull DataProvider provider, @NotNull String dataId, @Nullable Set<String> alreadyComputedIds) {
    return getDataFromProvider(provider, dataId, alreadyComputedIds, getDataRule(dataId));
  }

  @ApiStatus.Internal
  public @Nullable Object getDataFromProvider(@NotNull DataProvider provider,
                                               @NotNull String dataId,
                                               @Nullable Set<String> alreadyComputedIds,
                                               @Nullable GetDataRule dataRule) {
    ProgressManager.checkCanceled();
    if (alreadyComputedIds != null && alreadyComputedIds.contains(dataId)) {
      return null;
    }
    int[] depth = ourGetDataLevel.get();
    try {
      depth[0]++;
      Object data = provider.getData(dataId);
      if (data != null) return validOrNull(data, dataId, provider);

      if (dataRule != null) {
        final Set<String> ids = alreadyComputedIds == null ? new HashSet<>() : alreadyComputedIds;
        ids.add(dataId);
        data = dataRule.getData(id -> getDataFromProvider(provider, id, ids));

        if (data != null) return validOrNull(data, dataId, provider);
      }

      return null;
    }
    finally {
      depth[0]--;
      if (alreadyComputedIds != null) alreadyComputedIds.remove(dataId);
    }
  }

  public static @Nullable DataProvider getDataProviderEx(@Nullable Object component) {
    DataProvider dataProvider = null;
    if (component instanceof DataProvider) {
      dataProvider = (DataProvider)component;
    }
    else if (component instanceof TypeSafeDataProvider) {
      dataProvider = new TypeSafeDataProviderAdapter((TypeSafeDataProvider) component);
    }
    else if (component instanceof JComponent) {
      dataProvider = getDataProvider((JComponent)component);
    }

    if (dataProvider instanceof BackgroundableDataProvider) {
      dataProvider = ((BackgroundableDataProvider)dataProvider).createBackgroundDataProvider();
    }

    return dataProvider;
  }

  public @Nullable GetDataRule getDataRule(@NotNull String dataId) {
    String uninjectedId = InjectedDataKeys.uninjectedId(dataId);
    GetDataRule slowRule = dataProvider -> getSlowData(dataId, dataProvider);
    List<GetDataRule> rules1 = ContainerUtil.nullize(myDataRuleCollector.forKey(dataId));
    List<GetDataRule> rules2 = uninjectedId == null ? null : ContainerUtil.nullize(myDataRuleCollector.forKey(uninjectedId));
    if (rules1 == null && rules2 == null) return slowRule;
    return dataProvider -> {
      Object data = slowRule.getData(dataProvider);
      if (data != null) return data;
      if (rules1 != null) {
        for (GetDataRule rule : rules1) {
          data = rule.getData(dataProvider);
          if (data != null) return data;
        }
      }
      if (rules2 != null) {
        for (GetDataRule rule : rules2) {
          data = rule.getData(id -> {
            String injectedId = InjectedDataKeys.injectedId(id);
            return injectedId != null ? dataProvider.getData(injectedId) : null;
          });
          if (data != null) return data;
        }
      }
      return null;
    };
  }

  private static @Nullable Object getSlowData(@NotNull String dataId, @NotNull DataProvider dataProvider) {
    Iterable<DataProvider> asyncProviders = PlatformDataKeys.SLOW_DATA_PROVIDERS.getData(dataProvider);
    if (asyncProviders == null) return null;
    for (DataProvider provider : asyncProviders) {
      Object data = provider.getData(dataId);
      if (data != null) {
        return data;
      }
    }
    return null;
  }

  @Override
  public @NotNull DataContext getDataContext(Component component) {
    if (Registry.is("actionSystem.dataContextAssertions")) {
      ApplicationManager.getApplication().assertIsDispatchThread();
      if (ourGetDataLevel.get()[0] > 0) {
        LOG.error("DataContext shall not be created and queried inside another getData() call.");
      }
    }
    return new MyDataContext(component);
  }

  @Override
  public @NotNull DataContext getDataContext(@NotNull Component component, int x, int y) {
    if (x < 0 || x >= component.getWidth() || y < 0 || y >= component.getHeight()) {
      throw new IllegalArgumentException("wrong point: x=" + x + "; y=" + y);
    }

    // Point inside JTabbedPane has special meaning. If point is inside tab bounds then
    // we construct DataContext by the component which corresponds to the (x, y) tab.
    if (component instanceof JTabbedPane) {
      JTabbedPane tabbedPane = (JTabbedPane)component;
      int index = tabbedPane.getUI().tabForCoordinate(tabbedPane, x, y);
      return getDataContext(index != -1 ? tabbedPane.getComponentAt(index) : tabbedPane);
    }
    else {
      return getDataContext(component);
    }
  }

  @Override
  public @NotNull DataContext getDataContext() {
    Component component = null;
    if (Registry.is("actionSystem.getContextByRecentMouseEvent")) {
      component = SwingHelper.getComponentFromRecentMouseEvent();
    }
    return getDataContext(component != null ? component : getFocusedComponent());
  }

  @Override
  public @NotNull Promise<DataContext> getDataContextFromFocusAsync() {
    AsyncPromise<DataContext> result = new AsyncPromise<>();
    IdeFocusManager.getGlobalInstance()
                   .doWhenFocusSettlesDown(() -> result.setResult(getDataContext()), ModalityState.any());
    return result;
  }

  private static @Nullable Component getFocusedComponent() {
    WindowManager windowManager = WindowManager.getInstance();
    if (!(windowManager instanceof WindowManagerEx)) {
      return null;
    }

    WindowManagerEx windowManagerEx = (WindowManagerEx)windowManager;
    Window activeWindow = windowManagerEx.getMostRecentFocusedWindow();
    if (activeWindow == null) {
      activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
      if (activeWindow == null) {
        activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
        if (activeWindow == null) return null;
      }
    }

    // In case we have an active floating toolwindow and some component in another window focused,
    // we want this other component to receive key events.
    // Walking up the window ownership hierarchy from the floating toolwindow would have led us to the main IdeFrame
    // whereas we want to be able to type in other frames as well.
    if (activeWindow instanceof FloatingDecorator) {
      IdeFocusManager ideFocusManager = IdeFocusManager.findInstanceByComponent(activeWindow);
      IdeFrame lastFocusedFrame = ideFocusManager.getLastFocusedFrame();
      JComponent frameComponent = lastFocusedFrame != null ? lastFocusedFrame.getComponent() : null;
      Window lastFocusedWindow = frameComponent != null ? SwingUtilities.getWindowAncestor(frameComponent) : null;
      boolean toolWindowIsNotFocused = windowManagerEx.getFocusedComponent(activeWindow) == null;
      if (toolWindowIsNotFocused && lastFocusedWindow != null) {
        activeWindow = lastFocusedWindow;
      }
    }

    // try to find first parent window that has focus
    Window window = activeWindow;
    Component focusedComponent = null;
    while (window != null) {
      focusedComponent = windowManagerEx.getFocusedComponent(window);
      if (focusedComponent != null) {
        break;
      }
      window = window.getOwner();
    }
    if (focusedComponent == null) {
      focusedComponent = activeWindow;
    }

    return focusedComponent;
  }

  @Override
  public <T> void saveInDataContext(DataContext dataContext, @NotNull Key<T> dataKey, @Nullable T data) {
    if (dataContext instanceof UserDataHolder && !Utils.isFrozenDataContext(dataContext)) {
      ((UserDataHolder)dataContext).putUserData(dataKey, data);
    }
  }

  @Override
  public @Nullable <T> T loadFromDataContext(@NotNull DataContext dataContext, @NotNull Key<T> dataKey) {
    return dataContext instanceof UserDataHolder ? ((UserDataHolder)dataContext).getUserData(dataKey) : null;
  }

  public static @Nullable Editor validateEditor(Editor editor, Component contextComponent) {
    if (contextComponent instanceof JComponent) {
      final JComponent jComponent = (JComponent)contextComponent;
      if (jComponent.getClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY) != null) return null;
    }

    return editor;
  }

  /**
   * todo make private in 2020
   * @see DataManager#loadFromDataContext(DataContext, Key)
   * @see DataManager#saveInDataContext(DataContext, Key, Object)
   * @deprecated use {@link DataManager#getDataContext(Component)} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  public static class MyDataContext extends EdtDataContext {
    public MyDataContext(@Nullable Component component) {
      super(component);
    }
  }
}
