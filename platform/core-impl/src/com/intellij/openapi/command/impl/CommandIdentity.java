// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import java.util.concurrent.atomic.AtomicInteger;


final class CommandIdentity {
  private static final AtomicInteger ID_GENERATOR = new AtomicInteger(0);

  private final int id = ID_GENERATOR.incrementAndGet();

  @Override
  public boolean equals(Object object) {
    if (!(object instanceof CommandIdentity)) return false;
    CommandIdentity identity = (CommandIdentity) object;
    return id == identity.id;
  }

  @Override
  public int hashCode() {
    return id;
  }

  @Override
  public String toString() {
    return "CommandIdentity{" +
           "id=" + id +
           '}';
  }
}
