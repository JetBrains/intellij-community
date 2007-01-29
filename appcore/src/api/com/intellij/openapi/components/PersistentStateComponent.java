package com.intellij.openapi.components;

import com.intellij.util.xmlb.XmlSerializer;

/**
 * Every component which would like to persist its state across IDEA restarts
 * should implement this interface.
 *
 * todo: describe registration procedures
 */
public interface PersistentStateComponent<T> {
  /**
   * @return a component state. All properties and public fields are serialized. Only values, which differ
   * from default (i.e. the value of newly instantiated class) are serialized.
   * @see XmlSerializer
   */
  T getState();

  /**
   * This method is called when new component state is loaded. A component should expect this method
   * to be called at any moment of its lifecycle. The method can and will be called several times, if
   * config files were externally changed while IDEA running.
   * @param object loaded component state
   */
  void loadState(T object);
}
