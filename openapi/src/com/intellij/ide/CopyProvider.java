package com.intellij.ide;

import com.intellij.openapi.actionSystem.DataContext;

public interface CopyProvider {
  void performCopy(DataContext dataContext);
  boolean isCopyEnabled(DataContext dataContext);
}
