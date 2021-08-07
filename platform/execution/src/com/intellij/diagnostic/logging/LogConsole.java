// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.diagnostic.logging;


public interface LogConsole {
  void setFilterModel(LogFilterModel model);

  LogFilterModel getFilterModel();
}
