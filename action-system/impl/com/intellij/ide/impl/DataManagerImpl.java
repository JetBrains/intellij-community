package com.intellij.ide.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.impl.dataRules.*;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.usages.UsageView;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DataManagerImpl extends DataManager implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.impl.DataManagerImpl");
  private Map<String, GetDataRule> myDataConstantToRuleMap = new THashMap<String, GetDataRule>();
  private WindowManagerEx myWindowManager;

  public DataManagerImpl() {
    registerRules();
  }

  public void initComponent() { }

  public void disposeComponent() { }

  private Object getData(String dataId, final Component focusedComponent) {
    for (Component c = focusedComponent; c != null; c = c.getParent()) {
      final DataProvider dataProvider = getDataProvider(c);
      if (dataProvider == null) continue;

      Object data = getDataFromProvider(dataProvider, dataId, new HashSet<String>());
      if (data != null) return data;
    }

    return null;
  }

  private Object getDataFromProvider(final DataProvider provider, String dataId, final Set<String> alreadyComputedIds) {
    if (alreadyComputedIds.contains(dataId)) return null;

    alreadyComputedIds.add(dataId);
    try {
      Object data = provider.getData(dataId);
      if (data != null) return validated(data, dataId, provider);

      GetDataRule dataRule = getDataRule(dataId);
      if (dataRule != null) {
        data = dataRule.getData(new DataProvider() {
          public Object getData(String dataId) {
            return getDataFromProvider(provider, dataId, alreadyComputedIds);
          }
        });

        if (data != null) return validated(data, dataId, provider);
      }

      return null;
    }
    finally {
      alreadyComputedIds.remove(dataId);
    }
  }

  private static DataProvider getDataProvider(Component c) {
    DataProvider dataProvider = null;
    if (c instanceof DataProvider) {
      dataProvider = (DataProvider)c;
    }
    return dataProvider;
  }

  public GetDataRule getDataRule(String dataId) {
    return myDataConstantToRuleMap.get(dataId);
  }

  private static Object validated(Object data, String dataId, Object dataSource) {
    Object invalidData = DataValidator.findInvalidData(dataId, data);
    if (invalidData != null) {
      LOG.assertTrue(false, "Data isn't valid. " + dataId + "=" + invalidData + " Provided by: " + dataSource.getClass().getName() + " (" + dataSource.toString()+")");
    }
    return data;
  }

  public DataContext getDataContext(Component component) {
    return new MyDataContext(component);
  }

  public DataContext getDataContext(@NotNull Component component, int x, int y) {
    if (
      x < 0 || x >= component.getWidth() ||
      y < 0 || y >= component.getHeight()
    ) {
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

  public DataContext getDataContext() {
    return getDataContext(getFocusedComponent());
  }

  public DataContext getDataContextTest(Component component) {
    DataContext dataContext = getDataContext(component);
    if (myWindowManager == null) {
      return dataContext;
    }
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    Component focusedComponent = myWindowManager.getFocusedComponent(project);
    if (focusedComponent != null) {
      dataContext = getDataContext(focusedComponent);
    }
    return dataContext;
  }

  private Component getFocusedComponent() {
    if (myWindowManager == null) {
      return null;
    }
    Window activeWindow = myWindowManager.getMostRecentFocusedWindow();
    if (activeWindow == null) {
      return null;
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
    myDataConstantToRuleMap.put(DataConstants.PSI_FILE, new PsiFileRule());
    myDataConstantToRuleMap.put(DataConstantsEx.PASTE_TARGET_PSI_ELEMENT, new PasteTargetRule());
    myDataConstantToRuleMap.put(DataConstants.COPY_PROVIDER, new CopyProviderRule());
    myDataConstantToRuleMap.put(DataConstants.CUT_PROVIDER, new CutProviderRule());
    myDataConstantToRuleMap.put(DataConstants.PASTE_PROVIDER, new PasteProviderRule());
    myDataConstantToRuleMap.put(DataConstantsEx.PROJECT_FILE_DIRECTORY, new ProjectFileDirectoryRule());
    myDataConstantToRuleMap.put(DataConstants.NAVIGATABLE, new NavigatableRule());
    myDataConstantToRuleMap.put(DataConstants.VIRTUAL_FILE_ARRAY, new VirtualFileArrayRule());
    myDataConstantToRuleMap.put(DataConstants.VIRTUAL_FILE, new VirtualFileRule());
    myDataConstantToRuleMap.put(DataConstants.FILE_TEXT, new FileTextRule());
    myDataConstantToRuleMap.put(DataConstants.FILE_EDITOR, new FileEditorRule());
    myDataConstantToRuleMap.put(DataConstants.FILE_EDITOR_NO_COMMIT, new FastFileEditorRule());
    myDataConstantToRuleMap.put(DataConstants.MODULE, new ModuleRule());
    myDataConstantToRuleMap.put(UsageView.USAGE_TARGETS, new UsageTargetsRule());
    myDataConstantToRuleMap.put(DataConstants.NAVIGATABLE_ARRAY, new NavigatableArrayRule());    
  }

  public String getComponentName() {
    return "DataManager";
  }

  public class MyDataContext implements DataContext {
    private int myEventCount;
    // To prevent memory leak we have to wrap passed component into
    // the weak reference. For example, Swing often remembers menu items
    // that have DataContext as a field.
    private final WeakReference<Component> myRef;

    public MyDataContext(final Component component) {
      myEventCount = -1;
      myRef = new WeakReference<Component>(component);
    }

    public void setEventCount(int eventCount) {
      myEventCount = eventCount;
    }

    public Object getData(String dataId) {
      int currentEventCount = IdeEventQueue.getInstance().getEventCount();
      if (myEventCount != -1 && myEventCount != currentEventCount) {
        /*
        throw new IllegalStateException(
          "cannot share data context between Swing events; initial event count = " +
            myEventCount + "; current event count = " + currentEventCount
        );
        */
        LOG.assertTrue(false,
                       "cannot share data context between Swing events; initial event count = " + myEventCount +
                       "; current event count = " +
                       currentEventCount);
      }

      Component _component = myRef.get();
      if (DataConstants.IS_MODAL_CONTEXT.equals(dataId)) {
        if (_component != null) {
          return IdeKeyEventDispatcher.isModalContext(_component) ? Boolean.TRUE : Boolean.FALSE;
        }
        else {
          return null;
        }
      }
      else if (DataConstants.CONTEXT_COMPONENT.equals(dataId)) {
        return _component;
      }
      else if (DataConstantsEx.MODALITY_STATE.equals(dataId)) {
        return _component != null ? ModalityState.stateForComponent(_component) : ModalityState.NON_MMODAL;
      }
      else{
        return DataManagerImpl.this.getData(dataId, _component);
      }
    }

    @NonNls
    public String toString() {
      return "component=" + String.valueOf(myRef.get());
    }
  }
}
