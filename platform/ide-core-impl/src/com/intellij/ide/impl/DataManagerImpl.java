// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.impl.dataRules.GetDataRule;
import com.intellij.ide.ui.IdeUiService;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.KeyedExtensionCollector;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.FloatingDecoratorMarker;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

public class DataManagerImpl extends DataManager {
  private static final Logger LOG = Logger.getInstance(DataManagerImpl.class);

  private static final ThreadLocal<int[]> ourGetDataLevel = ThreadLocal.withInitial(() -> new int[1]);

  private final Map<Pair<String, GetDataRuleType>, GetDataRule> myRulesCache = ConcurrentFactoryMap.createMap(o -> getDataRule(o.first, o.second));

  public DataManagerImpl() {
    Application app = ApplicationManager.getApplication();
    ExtensionPoint<KeyedLazyInstance<GetDataRule>> dataRuleEP = app.getExtensionArea()
      .getExtensionPointIfRegistered(GetDataRule.EP_NAME.getName());
    if (dataRuleEP != null) {
      dataRuleEP.addChangeListener(() -> myRulesCache.clear(), app);
    }
  }

  @ApiStatus.Internal
  public @NotNull List<DataKey<?>> keysForRuleType(@Nullable GetDataRuleType ruleType) {
    boolean includeFast = ruleType == GetDataRuleType.PROVIDER;
    List<DataKey<?>> result = null;
    for (KeyedLazyInstance<GetDataRule> bean : GetDataRule.EP_NAME.getExtensionsIfPointIsRegistered()) {
      GetDataRuleType type = ((GetDataRuleBean)bean).type;
      if (type != ruleType && !(includeFast && type == GetDataRuleType.FAST)) continue;
      if (result == null) result = new ArrayList<>();
      result.add(DataKey.create(((GetDataRuleBean)bean).key));
    }
    return result == null ? Collections.emptyList() : result;
  }

  @ApiStatus.Internal
  public @Nullable Object getDataFromProviderAndRules(@NotNull String dataId,
                                                      @Nullable GetDataRuleType ruleType,
                                                      @NotNull DataProvider provider) {
    return getDataFromProviderAndRulesInner(dataId, ruleType, null, provider);
  }

  @ApiStatus.Internal
  public @Nullable Object getDataFromRules(@NotNull String dataId,
                                           @NotNull GetDataRuleType ruleType,
                                           @NotNull DataProvider provider) {
    return getDataFromRulesInner(dataId, ruleType, null, provider);
  }

  private @Nullable Object getDataFromProviderAndRulesInner(@NotNull String dataId,
                                                            @Nullable GetDataRuleType ruleType,
                                                            @Nullable Set<String> alreadyComputedIds,
                                                            @NotNull DataProvider provider) {
    ProgressManager.checkCanceled();
    if (alreadyComputedIds != null && alreadyComputedIds.contains(dataId)) {
      return null;
    }
    int[] depth = ourGetDataLevel.get();
    try {
      depth[0]++;
      Object data = getDataFromProviderInner(dataId, provider);
      if (data != null) {
        return data;
      }
      return ruleType == null ? null : getDataFromRulesInner(dataId, ruleType, alreadyComputedIds, provider);
    }
    finally {
      depth[0]--;
      if (alreadyComputedIds != null) alreadyComputedIds.remove(dataId);
    }
  }

  private @Nullable Object getDataFromRulesInner(@NotNull String dataId,
                                                 @NotNull GetDataRuleType ruleType,
                                                 @Nullable Set<String> alreadyComputedIds,
                                                 @NotNull DataProvider provider) {
    GetDataRule rule = myRulesCache.get(Pair.create(dataId, ruleType));
    if (rule == null) return null;
    return getDataFromRuleInner(rule, dataId, ruleType, alreadyComputedIds, provider);
  }

  private @Nullable Object getDataFromRuleInner(@NotNull GetDataRule rule,
                                                @NotNull String dataId,
                                                @NotNull GetDataRuleType ruleType,
                                                @Nullable Set<String> alreadyComputedIds,
                                                @NotNull DataProvider provider) {
    int[] depth = ourGetDataLevel.get();
    try {
      depth[0]++;
      Set<String> ids = alreadyComputedIds == null ? new HashSet<>() : alreadyComputedIds;
      ids.add(dataId);
      Object data = rule.getData(id -> {
        Object o = getDataFromProviderAndRulesInner(id, ruleType, ids, provider);
        return o == CustomizedDataContext.EXPLICIT_NULL ? null : o;
      });
      return data == null ? null :
             data == CustomizedDataContext.EXPLICIT_NULL ? data :
             DataValidators.validOrNull(data, dataId, rule);
    }
    finally {
      depth[0]--;
    }
  }

  /** @deprecated Most components now implement {@link UiDataProvider} */
  @Deprecated
  public static @Nullable DataProvider getDataProviderEx(@Nullable Object component) {
    DataProvider dataProvider = null;
    if (component instanceof DataProvider) {
      dataProvider = (DataProvider)component;
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

  @Override
  public @Nullable Object getCustomizedData(@NotNull String dataId, @NotNull DataContext dataContext, @NotNull DataProvider provider) {
    Object data = getDataFromProviderAndRules(dataId, GetDataRuleType.CONTEXT, id -> {
      Object o = getDataFromProviderAndRules(id, GetDataRuleType.PROVIDER, provider);
      if (o != null) return o;
      return dataContext.getData(id);
    });
    return data == CustomizedDataContext.EXPLICIT_NULL ? null : data;
  }

  @Override
  public @NotNull DataContext customizeDataContext(@NotNull DataContext context, @NotNull Object provider) {
    DataProvider p = provider instanceof DataProvider o ? o :
                     provider instanceof UiDataProvider o ? (EdtNoGetDataProvider)sink -> sink.uiDataSnapshot(o) :
                     provider instanceof DataSnapshotProvider o ? (EdtNoGetDataProvider)sink -> sink.dataSnapshot(o) :
                     null;
    if (p == null) throw new AssertionError("Unexpected provider: " + provider.getClass().getName());
    return IdeUiService.getInstance().createCustomizedDataContext(context, p);
  }

  private static @Nullable GetDataRule getDataRule(@NotNull String dataId, @NotNull GetDataRuleType ruleType) {
    return switch (ruleType) {
      case FAST -> {
        List<GetDataRule> rules = rulesForKey(dataId, GetDataRuleType.FAST);
        yield rules == null ? null : rules.size() == 1 ? rules.get(0) : dataProvider -> getRulesData(dataId, rules, dataProvider);
      }
      case PROVIDER -> getDataRuleInner(dataId, GetDataRuleType.PROVIDER);
      case CONTEXT -> getDataRuleInner(dataId, GetDataRuleType.CONTEXT);
    };
  }

  private static @Nullable GetDataRule getDataRuleInner(@NotNull String dataId, @NotNull GetDataRuleType ruleType) {
    String uninjectedId = InjectedDataKeys.uninjectedId(dataId);
    GetDataRule slowRule = ruleType == GetDataRuleType.PROVIDER &&
                           !PlatformCoreDataKeys.BGT_DATA_PROVIDER.is(dataId) ?
                           dataProvider -> getSlowData(dataId, dataProvider) : null;
    List<GetDataRule> rules1 = rulesForKey(dataId, ruleType);
    List<GetDataRule> rules2 = uninjectedId == null ? null : rulesForKey(uninjectedId, ruleType);
    if (rules1 == null && rules2 == null) return slowRule;
    return dataProvider -> {
      Object data = slowRule == null ? null : slowRule.getData(dataProvider);
      data = data != null ? data : rules1 == null ? null : getRulesData(dataId, rules1, dataProvider);
      data = data != null ? data : rules2 == null ? null : getRulesData(dataId, rules2, id -> {
        String injectedId = InjectedDataKeys.injectedId(id);
        return injectedId != null ? dataProvider.getData(injectedId) : null;
      });
      return data;
    };
  }

  private static @Nullable List<GetDataRule> rulesForKey(@NotNull String dataId, @NotNull GetDataRuleType ruleType) {
    boolean includeFast = ruleType == GetDataRuleType.PROVIDER;
    List<GetDataRule> result = null;
    for (KeyedLazyInstance<GetDataRule> bean : GetDataRule.EP_NAME.getExtensionsIfPointIsRegistered()) {
      if (!Objects.equals(dataId, bean.getKey())) continue;
      GetDataRuleType type = ((GetDataRuleBean)bean).type;
      if (type != ruleType && !(includeFast && type == GetDataRuleType.FAST)) continue;
      GetDataRule rule = KeyedExtensionCollector.instantiate(bean);
      if (rule == null) continue;
      if (result == null) result = new SmartList<>();
      result.add(rule);
    }
    return result;
  }

  private static @Nullable Object getRulesData(@NotNull String dataId, @NotNull List<? extends GetDataRule> rules, @NotNull DataProvider provider) {
    for (GetDataRule rule : rules) {
      try {
        Object data = rule.getData(provider);
        if (data != null) {
          return data == CustomizedDataContext.EXPLICIT_NULL ? data :
                 DataValidators.validOrNull(data, dataId, rule);
        }
      }
      catch (IndexNotReadyException ignore) {
      }
    }
    return null;
  }

  private static @Nullable Object getSlowData(@NotNull String dataId, @NotNull DataProvider dataProvider) {
    DataProvider bgtProvider = PlatformCoreDataKeys.BGT_DATA_PROVIDER.getData(dataProvider);
    if (bgtProvider != null) {
      Object data = getDataFromProviderInner(dataId, bgtProvider);
      if (data != null) return data;
    }
    return null;
  }

  private static @Nullable Object getDataFromProviderInner(@NotNull String dataId, @NotNull DataProvider provider) {
    if (!(provider instanceof CompositeDataProvider)) {
      try {
        Object data = provider.getData(dataId);
        if (data != null) {
          return data == CustomizedDataContext.EXPLICIT_NULL ? data :
                 DataValidators.validOrNull(data, dataId, provider);
        }
      }
      catch (IndexNotReadyException ignore) {
      }
    }
    else {
      for (DataProvider p : ((CompositeDataProvider)provider).getDataProviders()) {
        Object data = getDataFromProviderInner(dataId, p);
        if (data != null) return data;
      }
    }
    return null;
  }

  @Override
  public @NotNull DataContext getDataContext(Component component) {
    ThreadingAssertions.assertEventDispatchThread();
    if (ourGetDataLevel.get()[0] > 0) {
      LOG.error("DataContext shall not be created and queried inside another getData() call.");
    }
    if (component instanceof DependentTransientComponent) {
      LOG.assertTrue(getDataProviderEx(component) == null, "DependentTransientComponent must not yield DataProvider");
    }
    Component adjusted = component instanceof DependentTransientComponent ?
                         ((DependentTransientComponent)component).getPermanentComponent() : component;
    return IdeUiService.getInstance().createUiDataContext(adjusted);
  }

  @Override
  public @NotNull DataContext getDataContext(@NotNull Component component, int x, int y) {
    if (x < 0 || x >= component.getWidth() || y < 0 || y >= component.getHeight()) {
      throw new IllegalArgumentException("wrong point: x=" + x + "; y=" + y);
    }

    // Point inside JTabbedPane has special meaning. If point is inside tab bounds then
    // we construct DataContext by the component which corresponds to the (x, y) tab.
    if (component instanceof JTabbedPane tabbedPane) {
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
    if (Registry.is("actionSystem.getContextByRecentMouseEvent", false)) {
      component = IdeUiService.getInstance().getComponentFromRecentMouseEvent();
    }
    return getDataContext(component != null ? component : getFocusedComponent());
  }

  @Override
  public @NotNull Promise<DataContext> getDataContextFromFocusAsync() {
    AsyncPromise<DataContext> result = new AsyncPromise<>();
    IdeFocusManager.getGlobalInstance()
                   .doWhenFocusSettlesDown(() -> result.setResult(getDataContext()), ModalityState.defaultModalityState());
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
    if (!(dataContext instanceof UserDataHolder holder)) return;
    holder.putUserData(dataKey, data);
  }

  @Override
  public @Nullable <T> T loadFromDataContext(@NotNull DataContext dataContext, @NotNull Key<T> dataKey) {
    return dataContext instanceof UserDataHolder ? ((UserDataHolder)dataContext).getUserData(dataKey) : null;
  }

  public static @Nullable Editor validateEditor(Editor editor, Component contextComponent) {
    if (contextComponent instanceof JComponent jComponent) {
      if (jComponent.getClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY) != null) return null;
    }

    return editor;
  }
}
