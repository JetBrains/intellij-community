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

  public boolean canEstimateObjectSize() {
    return check(Capability.OBJECT_SIZE);
  }

  public boolean canEstimateObjectsSizes() {
    return check(Capability.OBJECTS_SIZES);
  }

  public boolean canFindPathsToClosestGcRoots() {
    return check(Capability.PATHS_TO_CLOSEST_GC_ROOTS);
  }

  public boolean canGetShallowSizeByClasses() {
    return check(Capability.SHALLOW_SIZE_BY_CLASSES);
  }

  public boolean canGetRetainedSizeByClasses() { return check(Capability.RETAINED_SIZE_BY_CLASSES); }

  private boolean check(Capability capability) {
    return myCapabilities.contains(capability);
  }

  @Override
  public String toString() {
    return "Agent capabilities: " + myCapabilities.toString();
  }

  private enum Capability {
    PATHS_TO_CLOSEST_GC_ROOTS,
    OBJECT_SIZE,
    OBJECTS_SIZES,
    SHALLOW_SIZE_BY_CLASSES,
    RETAINED_SIZE_BY_CLASSES
  }

  static class Builder {
    private final EnumSet<Capability> myCapabilities = EnumSet.noneOf(Capability.class);

    public Builder setCanEstimateObjectSize(boolean value) {
      return update(Capability.OBJECT_SIZE, value);
    }

    public Builder setCanEstimateObjectsSizes(boolean value) {
      return update(Capability.OBJECTS_SIZES, value);
    }

    public Builder setCanGetShallowSizeByClasses(boolean value) {
      return update(Capability.SHALLOW_SIZE_BY_CLASSES, value);
    }

    public Builder setCanGetRetainedSizeByClasses(boolean value) {
      return update(Capability.RETAINED_SIZE_BY_CLASSES, value);
    }

    public Builder setCanFindPathsToClosestGcRoots(boolean value) {
      return update(Capability.PATHS_TO_CLOSEST_GC_ROOTS, value);
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
