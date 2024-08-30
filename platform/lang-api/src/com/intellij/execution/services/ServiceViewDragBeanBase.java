// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.services;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface ServiceViewDragBeanBase {
  @NotNull
  List<Object> getSelectedItems();
}
