package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.impl.JpsEventDispatcherBase;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author nik
 */
class TestJpsEventDispatcher extends JpsEventDispatcherBase implements JpsEventDispatcher {
  private List<JpsElement> myAdded = new ArrayList<JpsElement>();
  private List<JpsElement> myRemoved = new ArrayList<JpsElement>();
  private List<JpsElement> myChanged = new ArrayList<JpsElement>();

  @Override
  public <T extends JpsElement> void fireElementAdded(@NotNull T element, @NotNull JpsElementChildRole<T> role) {
    super.fireElementAdded(element, role);
    myAdded.add(element);
  }

  @Override
  public <T extends JpsElement> void fireElementRemoved(@NotNull T element, @NotNull JpsElementChildRole<T> role) {
    super.fireElementRemoved(element, role);
    myRemoved.add(element);
  }

  @Override
  public void fireElementChanged(@NotNull JpsElement element) {
    myChanged.add(element);
  }

  @Override
  public void fireElementRenamed(@NotNull JpsNamedElement element, @NotNull String oldName, @NotNull String newName) {
  }

  public <T extends JpsElement> List<T> retrieveAdded(Class<T> type) {
    return retrieve(type, myAdded);
  }

  public <T extends JpsElement> List<T> retrieveRemoved(Class<T> type) {
    return retrieve(type, myRemoved);
  }

  public <T extends JpsElement> List<T> retrieveChanged(Class<T> type) {
    return retrieve(type, myChanged);
  }

  public void clear() {
    myAdded.clear();
    myRemoved.clear();
    myChanged.clear();
  }


  private static <T extends JpsElement> List<T> retrieve(Class<T> type, List<JpsElement> list) {
    final List<T> result = new ArrayList<T>();
    final Iterator<JpsElement> iterator = list.iterator();
    while (iterator.hasNext()) {
      JpsElement element = iterator.next();
      if (type.isInstance(element)) {
        result.add(type.cast(element));
        iterator.remove();
      }
    }
    return result;
  }
}
