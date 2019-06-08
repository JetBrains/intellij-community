// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

interface MemoryAgentNames {
  String PROXY_CLASS_NAME = "com.intellij.memory.agent.proxy.IdeaNativeAgentProxy";

  interface Methods {
    String IS_LOADED = "isLoaded";

    String CAN_FIND_GC_ROOTS = "canFindGcRoots";
    String CAN_ESTIMATE_OBJECT_SIZE = "canEstimateObjectSize";
    String CAN_ESTIMATE_OBJECTS_SIZES = "canEstimateObjectsSizes";

    String ESTIMATE_OBJECT_SIZE = "size";
    String ESTIMATE_OBJECTS_SIZE = "estimateRetainedSize";
    String FIND_GC_ROOTS = "gcRoots";
  }
}
