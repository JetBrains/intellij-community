package com.intellij.ide;

import com.intellij.openapi.actionSystem.DataContext;

public interface CutProvider {
  void performCut(DataContext dataContext);
  boolean isCutEnabled(DataContext dataContext);
}
