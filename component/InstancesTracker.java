package org.jetbrains.debugger.memory.component;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.memory.event.InstancesTrackerListener;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@State(name = "InstancesTracker", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class InstancesTracker extends AbstractProjectComponent
    implements PersistentStateComponent<InstancesTracker.MyState> {
  private final EventDispatcher<InstancesTrackerListener> myDispatcher =
      EventDispatcher.create(InstancesTrackerListener.class);
  private MyState myState = new MyState();

  public InstancesTracker(Project project) {
    super(project);
  }

  public static InstancesTracker getInstance(@NotNull Project project) {
    return project.getComponent(InstancesTracker.class);
  }

  public enum TrackingType {
    IDENTITY, HASH, RETAIN
  }

  public boolean isTracked(@NotNull String className) {
    return myState.classes.containsKey(className);
  }

  @Nullable
  public TrackingType getTrackingType(@NotNull String className) {
    return myState.classes.getOrDefault(className, null);
  }


  public Map<String, TrackingType> getTrackingClasses() {
    Map<String, TrackingType> copy = new HashMap<>(myState.classes);
    return Collections.unmodifiableMap(copy);
  }

  public void add(@NotNull ReferenceType ref, @NotNull TrackingType type) {
    String name = ref.name();
    if(type.equals(myState.classes.getOrDefault(name, null))) {
      return;
    }

    myState.classes.put(ref.name(), type);
    myDispatcher.getMulticaster().classChanged(name, type);
  }

  public boolean remove(@NotNull ReferenceType ref) {
    String name = ref.name();
    TrackingType removed = myState.classes.remove(name);
    if(removed != null) {
      myDispatcher.getMulticaster().classRemoved(name);
      return true;
    }

    return false;
  }

  public void addTrackerListener(@NotNull InstancesTrackerListener listener, @NotNull Disposable parentDisposable) {
    myDispatcher.addListener(listener, parentDisposable);
  }

  @Nullable
  @Override
  public MyState getState() {
    return new MyState(myState);
  }

  @Override
  public void loadState(MyState state) {
    myState = new MyState(state);
  }

  static class MyState {
    @NotNull
    @AbstractCollection(surroundWithTag = false, elementTypes = {Map.Entry.class})
    final Map<String, TrackingType> classes = new ConcurrentHashMap<>();

    MyState() {
    }

    MyState(@NotNull MyState state) {
      for (Map.Entry<String, TrackingType> classState : state.classes.entrySet()) {
        classes.put(classState.getKey(), classState.getValue());
      }
    }
  }
}
