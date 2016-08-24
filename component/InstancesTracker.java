package org.jetbrains.debugger.memory.component;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Vector;

@State(name = "InstancesTracker", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class InstancesTracker extends AbstractProjectComponent
    implements PersistentStateComponent<InstancesTracker.MyState> {
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

  public void add(@NotNull ReferenceType ref, @NotNull TrackingType type) {
    myState.classes.add(new TrackingClassState(ref.name(), type));
  }

  @SuppressWarnings("unused")
  public boolean remove(@NotNull ReferenceType ref, @NotNull TrackingType type) {
    return myState.classes.removeIf(state -> state.className.equals(ref.name()) && type.equals(state.trackingType));
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
    @AbstractCollection(surroundWithTag = false, elementTypes = {TrackingClassState.class})
    final List<TrackingClassState> classes = new Vector<>();

    MyState() {
    }

    MyState(@NotNull MyState state) {
      for (TrackingClassState classState : state.classes) {
        TrackingClassState newState = new TrackingClassState();
        newState.className = classState.className;
        newState.trackingType = classState.trackingType;
        classes.add(newState);
      }
    }
  }

  @Tag("class-state")
  private static class TrackingClassState {
    @NotNull
    @Attribute("name")
    String className ="";

    @Nullable
    @Attribute("type")
    TrackingType trackingType;

    @SuppressWarnings("WeakerAccess")
    public TrackingClassState() {
    }

    TrackingClassState(@NotNull String name, @NotNull TrackingType type) {
      className = name;
      trackingType = type;
    }
  }
}
