// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

import org.jetbrains.annotations.Nullable;

public interface ElementInterner {
  ElementInterner IDENTITY = new ElementInterner() {
    @Override
    public String intern(@Nullable String str) {
      return str;
    }

    @Override
    public <T extends Usage> T intern(@Nullable T usage) {
      return usage;
    }

    @Override
    public <T extends ReferenceID> T intern(@Nullable T id) {
      return id;
    }

    @Override
    public void clear() {
    }
  };

  
  String intern(@Nullable String str);

  <T extends Usage> T intern(@Nullable T usage);

  <T extends ReferenceID> T intern(@Nullable T id);

  void clear();
}
