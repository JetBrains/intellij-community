package com.intellij.execution.junit2.ui.properties;




public interface JUnitPropertyListener<T> {
  void onChanged(T value);
}
