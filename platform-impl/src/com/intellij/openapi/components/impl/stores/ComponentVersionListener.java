package com.intellij.openapi.components.impl.stores;

public interface ComponentVersionListener {
  ComponentVersionListener EMPTY = new ComponentVersionListener(){
    public void componentStateChanged(String componentName) {

    }
  } ;

  void componentStateChanged(String componentName);
}
