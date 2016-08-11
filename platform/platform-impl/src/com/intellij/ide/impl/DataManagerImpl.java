/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.impl.dataRules.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.FloatingDecorator;
import com.intellij.reference.SoftReference;
import com.intellij.util.KeyedLazyInstanceEP;
import com.intellij.util.containers.WeakValueHashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DataManagerImpl extends DataManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.impl.DataManagerImpl");
  private final ConcurrentMap<String, GetDataRule> myDataConstantToRuleMap = new ConcurrentHashMap<>();
  private WindowManagerEx myWindowManager;

  public DataManagerImpl() {
    registerRules();
  }

  @Nullable
  private Object getData(@NotNull String dataId, final Component focusedComponent) {
    for (Component c = focusedComponent; c != null; c = c.getParent()) {
      final DataProvider dataProvider = getDataProviderEx(c);
      if (dataProvider == null) continue;
      Object data = getDataFromProvider(dataProvider, dataId, null);
      if (data != null) return data;
    }
    return null;
  }

  @Nullable
  private Object getDataFromProvider(@NotNull final DataProvider provider, @NotNull String dataId, @Nullable Set<String> alreadyComputedIds) {
    if (alreadyComputedIds != null && alreadyComputedIds.contains(dataId)) {
      return null;
    }
    try {
      Object data = provider.getData(dataId);
      if (data != null) return validated(data, dataId, provider);

      GetDataRule dataRule = getDataRule(dataId);
      if (dataRule != null) {
        final Set<String> ids = alreadyComputedIds == null ? new THashSet<>() : alreadyComputedIds;
        ids.add(dataId);
        data = dataRule.getData(new DataProvider() {
          @Override
          public Object getData(String dataId) {
            return getDataFromProvider(provider, dataId, ids);
          }
        });

        if (data != null) return validated(data, dataId, provider);
      }

      return null;
    }
    finally {
      if (alreadyComputedIds != null) alreadyComputedIds.remove(dataId);
    }
  }

  @Nullable
  public static DataProvider getDataProviderEx(Object component) {
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
    
    return dataProvider;
  }

  @Nullable
  public GetDataRule getDataRule(@NotNull String dataId) {
    GetDataRule rule = getRuleFromMap(dataId);
    if (rule != null) {
      return rule;
    }

    final GetDataRule plainRule = getRuleFromMap(AnActionEvent.uninjectedId(dataId));
    if (plainRule != null) {
      return new GetDataRule() {
        @Override
        public Object getData(final DataProvider dataProvider) {
          return plainRule.getData(new DataProvider() {
            @Override
            @Nullable
            public Object getData(@NonNls String dataId) {
              return dataProvider.getData(AnActionEvent.injectedId(dataId));
            }
          });
        }
      };
    }

    return null;
  }

  @Nullable
  private GetDataRule getRuleFromMap(@NotNull String dataId) {
    GetDataRule rule = myDataConstantToRuleMap.get(dataId);
    if (rule == null && !myDataConstantToRuleMap.containsKey(dataId)) {
      final KeyedLazyInstanceEP<GetDataRule>[] eps = Extensions.getExtensions(GetDataRule.EP_NAME);
      for(KeyedLazyInstanceEP<GetDataRule> ruleEP: eps) {
        if (ruleEP.key.equals(dataId)) {
          rule = ruleEP.getInstance();
        }
      }
      if (rule != null) {
        myDataConstantToRuleMap.putIfAbsent(dataId, rule);
      }
    }
    return rule;
  }

  @Nullable
  private static Object validated(@NotNull Object data, @NotNull String dataId, @NotNull Object dataSource) {
    Object invalidData = DataValidator.findInvalidData(dataId, data, dataSource);
    if (invalidData != null) {
      return null;
      /*
      LOG.assertTrue(false, "Data isn't valid. " + dataId + "=" + invalidData + " Provided by: " + dataSource.getClass().getName() + " (" +
                            dataSource.toString() + ")");
      */
    }
    return data;
  }

  @Override
  public DataContext getDataContext(Component component) {
    return new MyDataContext(component);
  }

  @Override
  public DataContext getDataContext(@NotNull Component component, int x, int y) {
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

  public void setWindowManager(WindowManagerEx windowManager) {
    myWindowManager = windowManager;
  }

  @Override
  @NotNull
  public DataContext getDataContext() {
    return getDataContext(getFocusedComponent());
  }

  @Override
  public AsyncResult<DataContext> getDataContextFromFocus() {
    final AsyncResult<DataContext> context = new AsyncResult<>();

    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> context.setDone(getDataContext()));

    return context;
  }

  public DataContext getDataContextTest(Component component) {
    DataContext dataContext = getDataContext(component);
    if (myWindowManager == null) {
      return dataContext;
    }
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    Component focusedComponent = myWindowManager.getFocusedComponent(project);
    if (focusedComponent != null) {
      dataContext = getDataContext(focusedComponent);
    }
    return dataContext;
  }

  @Nullable
  private Component getFocusedComponent() {
    if (myWindowManager == null) {
      return null;
    }
    Window activeWindow = myWindowManager.getMostRecentFocusedWindow();
    if (activeWindow == null) {
      activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
      if (activeWindow == null) {
        activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
        if (activeWindow == null) return null;
      }
    }

    if (Registry.is("actionSystem.noContextComponentWhileFocusTransfer")) {
      IdeFocusManager fm = IdeFocusManager.findInstanceByComponent(activeWindow);
      if (fm.isFocusBeingTransferred()) {
        return null;
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
      boolean toolWindowIsNotFocused = myWindowManager.getFocusedComponent(activeWindow) == null;
      if (toolWindowIsNotFocused && lastFocusedWindow != null) {
        activeWindow = lastFocusedWindow;
      }
    }

    // try to find first parent window that has focus
    Window window = activeWindow;
    Component focusedComponent = null;
    while (window != null) {
      focusedComponent = myWindowManager.getFocusedComponent(window);
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

  private void registerRules() {
    myDataConstantToRuleMap.put(PlatformDataKeys.COPY_PROVIDER.getName(), new CopyProviderRule());
    myDataConstantToRuleMap.put(PlatformDataKeys.CUT_PROVIDER.getName(), new CutProviderRule());
    myDataConstantToRuleMap.put(PlatformDataKeys.PASTE_PROVIDER.getName(), new PasteProviderRule());
    myDataConstantToRuleMap.put(PlatformDataKeys.FILE_TEXT.getName(), new FileTextRule());
    myDataConstantToRuleMap.put(PlatformDataKeys.FILE_EDITOR.getName(), new FileEditorRule());
    myDataConstantToRuleMap.put(CommonDataKeys.NAVIGATABLE_ARRAY.getName(), new NavigatableArrayRule());
    myDataConstantToRuleMap.put(CommonDataKeys.EDITOR_EVEN_IF_INACTIVE.getName(), new InactiveEditorRule());
  }

  @Override
  public <T> void saveInDataContext(DataContext dataContext, @NotNull Key<T> dataKey, @Nullable T data) {
    if (dataContext instanceof UserDataHolder) {
      ((UserDataHolder)dataContext).putUserData(dataKey, data);
    }
  }

  @Override
  @Nullable
  public <T> T loadFromDataContext(@NotNull DataContext dataContext, @NotNull Key<T> dataKey) {
    return dataContext instanceof UserDataHolder ? ((UserDataHolder)dataContext).getUserData(dataKey) : null;
  }

  @Nullable
  public static Editor validateEditor(Editor editor) {
    Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (focusOwner instanceof JComponent) {
      final JComponent jComponent = (JComponent)focusOwner;
      if (jComponent.getClientProperty("AuxEditorComponent") != null) return null; // Hack for EditorSearchComponent
    }

    return editor;
  }

  private static class NullResult {
    public static final NullResult INSTANCE = new NullResult();
  }
  
  private static final Set<String> ourSafeKeys = new HashSet<>(Arrays.asList(
    CommonDataKeys.PROJECT.getName(),
    CommonDataKeys.EDITOR.getName(),
    PlatformDataKeys.IS_MODAL_CONTEXT.getName(),
    PlatformDataKeys.CONTEXT_COMPONENT.getName(),
    PlatformDataKeys.MODALITY_STATE.getName()
  ));

  public static class MyDataContext implements DataContext, UserDataHolder {
    private int myEventCount;
    // To prevent memory leak we have to wrap passed component into
    // the weak reference. For example, Swing often remembers menu items
    // that have DataContext as a field.
    private final Reference<Component> myRef;
    private Map<Key, Object> myUserData;
    private final Map<String, Object> myCachedData = new WeakValueHashMap<>();

    public MyDataContext(final Component component) {
      myEventCount = -1;
      myRef = component == null ? null : new WeakReference<>(component);
    }

    public void setEventCount(int eventCount, Object caller) {
      assert caller instanceof IdeKeyEventDispatcher : "This method might be accessible from " + IdeKeyEventDispatcher.class.getName() + " only";
      myCachedData.clear();
      myEventCount = eventCount;
    }

    @Override
    public Object getData(String dataId) {
      if (dataId == null) return null;
      int currentEventCount = IdeEventQueue.getInstance().getEventCount();
      if (myEventCount != -1 && myEventCount != currentEventCount) {
        LOG.error("cannot share data context between Swing events; initial event count = " + myEventCount + "; current event count = " +
                  currentEventCount);
        return doGetData(dataId);
      }

      if (ourSafeKeys.contains(dataId)) {
        Object answer = myCachedData.get(dataId);
        if (answer == null) {
          answer = doGetData(dataId);
          myCachedData.put(dataId, answer == null ? NullResult.INSTANCE : answer);
        }
        return answer != NullResult.INSTANCE ? answer : null;
      }
      else {
        return doGetData(dataId);
      }
    }

    @Nullable
    private Object doGetData(@NotNull String dataId) {
      Component component = SoftReference.dereference(myRef);
      if (PlatformDataKeys.IS_MODAL_CONTEXT.is(dataId)) {
        if (component == null) {
          return null;
        }
        return IdeKeyEventDispatcher.isModalContext(component);
      }
      if (PlatformDataKeys.CONTEXT_COMPONENT.is(dataId)) {
        return component;
      }
      if (PlatformDataKeys.MODALITY_STATE.is(dataId)) {
        return component != null ? ModalityState.stateForComponent(component) : ModalityState.NON_MODAL;
      }
      if (CommonDataKeys.EDITOR.is(dataId)) {
        Editor editor = (Editor)((DataManagerImpl)DataManager.getInstance()).getData(dataId, component);
        return validateEditor(editor);
      }
      return ((DataManagerImpl)DataManager.getInstance()).getData(dataId, component);
    }

    @NonNls
    public String toString() {
      return "component=" + SoftReference.dereference(myRef);
    }

    @Override
    public <T> T getUserData(@NotNull Key<T> key) {
      //noinspection unchecked
      return (T)getOrCreateMap().get(key);
    }

    @Override
    public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
      getOrCreateMap().put(key, value);
    }

    @NotNull
    private Map<Key, Object> getOrCreateMap() {
      Map<Key, Object> userData = myUserData;
      if (userData == null) {
        myUserData = userData = new WeakValueHashMap<>();
      }
      return userData;
    }
  }
}
