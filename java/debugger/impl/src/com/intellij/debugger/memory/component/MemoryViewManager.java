package org.jetbrains.debugger.memory.component;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.memory.event.MemoryViewManagerListener;
import org.jetbrains.debugger.memory.toolwindow.MemoryViewToolWindowFactory;

@State(name = "MemoryViewSettings", storages = @Storage("memory.view.xml"))
public class MemoryViewManager extends ApplicationComponent.Adapter
    implements PersistentStateComponent<MemoryViewManagerState> {
  private final EventDispatcher<MemoryViewManagerListener> myDispatcher =
      EventDispatcher.create(MemoryViewManagerListener.class);
  private MemoryViewManagerState myState = new MemoryViewManagerState();

  public static MemoryViewManager getInstance() {
    return ApplicationManager.getApplication().getComponent(MemoryViewManager.class);
  }

  @NotNull
  @Override
  public MemoryViewManagerState getState() {
    return new MemoryViewManagerState(myState);
  }

  @Override
  public void loadState(MemoryViewManagerState state) {
    if (state == null) {
      state = new MemoryViewManagerState();
    }

    myState = state;
    fireStateChanged();
  }

  public void setShowDiffOnly(boolean value) {
    if (myState.isShowWithDiffOnly != value) {
      myState.isShowWithDiffOnly = value;
      fireStateChanged();
    }
  }

  public void setShowWithInstancesOnly(boolean value) {
    if (myState.isShowWithInstancesOnly != value) {
      myState.isShowWithInstancesOnly = value;
      fireStateChanged();
    }
  }

  public void setShowTrackedOnly(boolean value) {
    if (myState.isShowTrackedOnly != value) {
      myState.isShowTrackedOnly = value;
      fireStateChanged();
    }
  }

  public boolean isNeedShowDiffOnly() {
    return myState.isShowWithDiffOnly;
  }

  public boolean isNeedShowInstancesOnly() {
    return myState.isShowWithInstancesOnly;
  }

  public boolean isNeedShowTrackedOnly() {
    return myState.isShowTrackedOnly;
  }

  public void addMemoryViewManagerListener(MemoryViewManagerListener listener, @NotNull Disposable parentDisposable) {
    myDispatcher.addListener(listener, parentDisposable);
  }

  @Nullable
  public ToolWindow getToolWindow(Project project) {
    return ToolWindowManager.getInstance(project).getToolWindow(MemoryViewToolWindowFactory.TOOL_WINDOW_ID);
  }

  private void fireStateChanged() {
    myDispatcher.getMulticaster().stateChanged(new MemoryViewManagerState(myState));
  }
}
