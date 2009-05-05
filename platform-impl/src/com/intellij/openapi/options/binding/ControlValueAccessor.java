package com.intellij.openapi.options.binding;

/**
 * @author Dmitry Avdeev
 */
public abstract class ControlValueAccessor<V> extends ValueAccessor<V> {

  public abstract boolean isEnabled();
  public abstract void addChangeListener(Runnable listener);
}
