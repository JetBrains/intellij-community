package com.intellij.util.xml;

/**
 * @author Gregory.Shrago
 */
public interface MutableGenericValue<T> extends GenericValue<T> {

  void setStringValue(String value);

  void setValue(T value);

}
