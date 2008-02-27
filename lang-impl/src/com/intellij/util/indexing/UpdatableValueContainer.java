package com.intellij.util.indexing;

/**
 * @author Eugene Zhuravlev
 *         Date: Feb 27, 2008
 */
public abstract class UpdatableValueContainer<T> extends ValueContainer<T>{

  public abstract void addValue(int inputId, T value);

  public abstract boolean removeValue(int inputId, T value);
}
