package com.intellij.ide;

import com.intellij.openapi.actionSystem.DataContext;

public interface DeleteProvider {
  void deleteElement(DataContext dataContext);
  boolean canDeleteElement(DataContext dataContext);
}
