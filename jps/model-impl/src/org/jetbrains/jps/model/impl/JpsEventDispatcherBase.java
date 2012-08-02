package org.jetbrains.jps.model.impl;

import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.JpsEventDispatcher;

import java.util.EventListener;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public abstract class JpsEventDispatcherBase implements JpsEventDispatcher {
  private Map<Class<?>, EventDispatcher<?>> myDispatchers = new HashMap<Class<?>, EventDispatcher<?>>();

  @NotNull
  @Override
  public <T extends EventListener> T getPublisher(Class<T> listenerClass) {
    EventDispatcher<?> dispatcher = myDispatchers.get(listenerClass);
    if (dispatcher == null) {
      dispatcher = EventDispatcher.create(listenerClass);
      myDispatchers.put(listenerClass, dispatcher);
    }
    //noinspection unchecked
    return (T)dispatcher.getMulticaster();
  }

  @Override
  public <T extends JpsElement> void fireElementAdded(@NotNull T element, @NotNull JpsElementChildRole<T> role) {
    role.fireElementAdded(this, element);
  }

  @Override
  public <T extends JpsElement> void fireElementRemoved(@NotNull T element, @NotNull JpsElementChildRole<T> role) {
    role.fireElementRemoved(this, element);
  }
}
