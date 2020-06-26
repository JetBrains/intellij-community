// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Set;

public final class MemoryAgentCapabilities {
  static final MemoryAgentCapabilities DISABLED = new MemoryAgentCapabilities(false, EnumSet.noneOf(Capability.class));

  private final boolean myIsLoaded;
  private final Set<Capability> myCapabilities;

  private MemoryAgentCapabilities(boolean isLoaded, @NotNull EnumSet<Capability> capabilitySet) {
    myIsLoaded = isLoaded;
    myCapabilities = EnumSet.copyOf(capabilitySet);
  }

  public boolean isLoaded() {
    return myIsLoaded;
  }

  public boolean canGetReferringObjects() {
    return check(Capability.GC_ROOTS);
  }

  public boolean canEstimateObjectSize() {
    return check(Capability.OBJECT_SIZE);
  }

  public boolean canEstimateObjectsSizes() {
    return check(Capability.OBJECTS_SIZES);
  }

  private boolean check(Capability capability) {
    return myCapabilities.contains(capability);
  }

  @Override
  public String toString() {
    return "Agent capabilities: " + myCapabilities.toString();
  }

  private enum Capability {
    GC_ROOTS,
    OBJECT_SIZE,
    OBJECTS_SIZES
  }

  static class Builder {
    private final EnumSet<Capability> myCapabilities = EnumSet.noneOf(Capability.class);

    public Builder setCanFindGcRoots(boolean value) {
      return update(Capability.GC_ROOTS, value);
    }

    public Builder setCanEstimateObjectSize(boolean value) {
      return update(Capability.OBJECT_SIZE, value);
    }

    public Builder setCanEstimateObjectsSizes(boolean value) {
      return update(Capability.OBJECTS_SIZES, value);
    }

    public MemoryAgentCapabilities buildLoaded() {
      return new MemoryAgentCapabilities(true, myCapabilities);
    }

    private Builder update(@NotNull Capability capability, boolean value) {
      if (!value) {
        myCapabilities.remove(capability);
      }
      else {
        myCapabilities.add(capability);
      }

      return this;
    }
  }
}
