// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public class MemoryAgentCapabilities {
  private static final Key<MemoryAgentCapabilities> MEMORY_AGENT_CAPABILITIES_KEY = Key.create("MEMORY_AGENT_CAPABILITIES_KEY");
  public static final MemoryAgentCapabilities DISABLED = new MemoryAgentCapabilities(false, Collections.emptySet());

  private final boolean myIsLoaded;
  private final Set<Capability> myCapabilities;

  private MemoryAgentCapabilities(boolean isLoaded, @NotNull Set<Capability> capabilitySet) {
    myIsLoaded = isLoaded;
    myCapabilities = EnumSet.noneOf(Capability.class);
    myCapabilities.addAll(capabilitySet);
  }

  @NotNull
  static MemoryAgentCapabilities get(@NotNull DebugProcessImpl debugProcess) {
    if (!DebuggerSettings.getInstance().ENABLE_MEMORY_AGENT) return DISABLED;
    MemoryAgentCapabilities capabilities = debugProcess.getUserData(MEMORY_AGENT_CAPABILITIES_KEY);
    return capabilities != null ? capabilities : DISABLED;
  }

  static void set(@NotNull DebugProcessImpl debugProcess, MemoryAgentCapabilities capabilities) {
    debugProcess.putUserData(MEMORY_AGENT_CAPABILITIES_KEY, capabilities);
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
    private final Set<Capability> myCapabilities = EnumSet.noneOf(Capability.class);

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
