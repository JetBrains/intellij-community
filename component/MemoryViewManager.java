package org.jetbrains.debugger.memory.component;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.EventDispatcher;
import org.jetbrains.debugger.memory.event.MemoryViewManagerListener;
import org.jetbrains.debugger.memory.toolwindow.MemoryViewToolWindowFactory;
import org.jetbrains.annotations.NotNull;

@State(name = "MemoryViewSettings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class MemoryViewManager extends AbstractProjectComponent
    implements PersistentStateComponent<MemoryViewManagerState> {
  private final EventDispatcher<MemoryViewManagerListener> myDispatcher =
      EventDispatcher.create(MemoryViewManagerListener.class);
  private MemoryViewManagerState myState = new MemoryViewManagerState();

  public static MemoryViewManager getInstance(Project project) {
    return project.getComponent(MemoryViewManager.class);
  }

  protected MemoryViewManager(Project project) {
    super(project);
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

  public boolean isNeedShowDiffOnly() {
    return myState.isShowWithDiffOnly;
  }

  public boolean isNeedShowInstancesOnly() {
    return myState.isShowWithInstancesOnly;
  }

  public void addMemoryViewManagerListener(MemoryViewManagerListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeMemoryViewManagerListener(MemoryViewManagerListener listener) {
    myDispatcher.removeListener(listener);
  }

  public ToolWindow getToolWindow() {
    return ToolWindowManager.getInstance(myProject).getToolWindow(MemoryViewToolWindowFactory.TOOL_WINDOW_ID);
  }

  private void fireStateChanged() {
    myDispatcher.getMulticaster().stateChanged(new MemoryViewManagerState(myState));
  }
}
