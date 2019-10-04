// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.services;

import com.intellij.ide.dnd.DnDEvent;
import org.jetbrains.annotations.NotNull;

public interface ServiceViewDnDDescriptor {
  boolean canDrop(@NotNull DnDEvent event, @NotNull Position position);

  void drop(@NotNull DnDEvent event, @NotNull Position position);

  enum Position {
    ABOVE, INTO, BELOW
  }
}
