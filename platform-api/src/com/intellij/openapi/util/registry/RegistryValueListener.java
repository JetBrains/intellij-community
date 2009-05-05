package com.intellij.openapi.util.registry;

public interface RegistryValueListener {

  void beforeValueChanged(RegistryValue value);
  void afterValueChanged(RegistryValue value);

  class Adapter implements RegistryValueListener {
    public void beforeValueChanged(RegistryValue value) {
    }

    public void afterValueChanged(RegistryValue value) {
    }
  }

}