// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.docking;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface DockContainerFactory {
  @NotNull
  DockContainer createContainer(@Nullable DockableContent<?> content);

  interface Persistent extends DockContainerFactory {
    DockContainer loadContainerFrom(@NotNull Element element);
  }
}
