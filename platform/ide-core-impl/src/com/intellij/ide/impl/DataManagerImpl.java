// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.impl.dataRules.GetDataRule;
import com.intellij.ide.ui.IdeUiService;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.KeyedExtensionCollector;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.FloatingDecoratorMarker;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.KeyedLazyInstanceEP;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.xmlb.annotations.Attribute;
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

public class DataManagerImpl extends DataManager {
  private static final Logger LOG = Logger.getInstance(DataManagerImpl.class);

  private static final ThreadLocal<int[]> ourGetDataLevel = ThreadLocal.withInitial(() -> new int[1]);

  private final KeyedExtensionCollector<GetDataRule, String> myDataRuleCollector = new KeyedExtensionCollector<>(GetDataRule.EP_NAME);

  private static class GetDataRuleBean extends KeyedLazyInstanceEP<GetDataRule> {
    @Attribute("injectedContext")
    public boolean injectedContext;
  }

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
      if (data != null) return DataValidators.validOrNull(data, dataId, provider);

      if (dataRule != null) {
        final Set<String> ids = alreadyComputedIds == null ? new HashSet<>() : alreadyComputedIds;
        ids.add(dataId);
        data = dataRule.getData(id -> getDataFromProvider(provider, id, ids));

        if (data != null) return DataValidators.validOrNull(data, dataId, dataRule);
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

  static {
    for (KeyedLazyInstance<GetDataRule> instance : GetDataRule.EP_NAME.getExtensionsIfPointIsRegistered()) {
      // initialize data-key instances for rules
      DataKey<?> dataKey = DataKey.create(instance.getKey());
      if (!((GetDataRuleBean)instance).injectedContext) continue;
      // add "injected" data-key for rules like "usageTarget"
      InjectedDataKeys.injectedKey(dataKey);
    }
  }

  public @Nullable GetDataRule getDataRule(@NotNull String dataId) {
    String uninjectedId = InjectedDataKeys.uninjectedId(dataId);
    GetDataRule slowRule = dataProvider -> getSlowData(dataId, dataProvider);
    List<GetDataRule> rules1 = ContainerUtil.nullize(myDataRuleCollector.forKey(dataId));
    List<GetDataRule> rules2 = uninjectedId == null ? null : ContainerUtil.nullize(myDataRuleCollector.forKey(uninjectedId));
    if (rules1 == null && rules2 == null) return slowRule;
    return dataProvider -> {
      Object data = slowRule.getData(dataProvider);
      data = data != null ? data : rules1 == null ? null : getRulesData(dataId, rules1, dataProvider);
      data = data != null ? data : rules2 == null ? null : getRulesData(dataId, rules2, id -> {
        String injectedId = InjectedDataKeys.injectedId(id);
        return injectedId != null ? dataProvider.getData(injectedId) : null;
      });
      return data;
    };
  }

  private static @Nullable Object getRulesData(@NotNull String dataId, @NotNull List<GetDataRule> rules, @NotNull DataProvider provider) {
    for (GetDataRule rule : rules) {
      try {
        Object data = rule.getData(provider);
        if (data != null) return DataValidators.validOrNull(data, dataId, rule);
      }
      catch (IndexNotReadyException ignore) {
      }
    }
    return null;
  }

  private static @Nullable Object getSlowData(@NotNull String dataId, @NotNull DataProvider dataProvider) {
    Iterable<DataProvider> asyncProviders = PlatformCoreDataKeys.SLOW_DATA_PROVIDERS.getData(dataProvider);
    if (asyncProviders == null) return null;
    for (DataProvider provider : asyncProviders) {
      try {
        Object data = provider.getData(dataId);
        if (data != null) {
          return DataValidators.validOrNull(data, dataId, provider);
        }
      }
      catch (IndexNotReadyException ignore) {
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
    return IdeUiService.getInstance().createUiDataContext(component);
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
      component = IdeUiService.getInstance().getComponentFromRecentMouseEvent();
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

    Window activeWindow = windowManager.getMostRecentFocusedWindow();
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
    if (activeWindow instanceof FloatingDecoratorMarker) {
      IdeFocusManager ideFocusManager = IdeFocusManager.findInstanceByComponent(activeWindow);
      IdeFrame lastFocusedFrame = ideFocusManager.getLastFocusedFrame();
      JComponent frameComponent = lastFocusedFrame != null ? lastFocusedFrame.getComponent() : null;
      Window lastFocusedWindow = frameComponent != null ? SwingUtilities.getWindowAncestor(frameComponent) : null;
      boolean toolWindowIsNotFocused = windowManager.getFocusedComponent(activeWindow) == null;
      if (toolWindowIsNotFocused && lastFocusedWindow != null) {
        activeWindow = lastFocusedWindow;
      }
    }

    // try to find first parent window that has focus
    Window window = activeWindow;
    Component focusedComponent = null;
    while (window != null) {
      focusedComponent = windowManager.getFocusedComponent(window);
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
    if (dataContext instanceof UserDataHolder &&
        !((dataContext instanceof FreezingDataContext) && ((FreezingDataContext)dataContext).isFrozenDataContext())) {
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
}
