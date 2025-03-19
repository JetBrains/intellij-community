// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementCreator;
import org.jetbrains.jps.model.JpsUrlList;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;

@ApiStatus.Internal
public final class JpsUrlListRole extends JpsElementChildRoleBase<JpsUrlList> implements JpsElementCreator<JpsUrlList> {
  public JpsUrlListRole(String debugName) {
    super(debugName);
  }

  @Override
  public @NotNull JpsUrlList create() {
    return new JpsUrlListImpl();
  }
}
