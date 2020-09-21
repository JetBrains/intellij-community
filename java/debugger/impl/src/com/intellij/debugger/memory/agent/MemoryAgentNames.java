// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

interface MemoryAgentNames {
  String PROXY_CLASS_NAME = "com.intellij.memory.agent.proxy.IdeaNativeAgentProxy";

  interface Methods {
    String IS_LOADED = "isLoaded";

    String CAN_ESTIMATE_OBJECT_SIZE = "canEstimateObjectSize";
    String CAN_ESTIMATE_OBJECTS_SIZES = "canEstimateObjectsSizes";
    String CAN_GET_SHALLOW_SIZE_BY_CLASSES = "canGetShallowSizeByClasses";
    String CAN_GET_RETAINED_SIZE_BY_CLASSES = "canGetRetainedSizeByClasses";
    String CAN_FIND_PATHS_TO_CLOSEST_GC_ROOTS = "canFindPathsToClosestGcRoots";

    String ESTIMATE_OBJECT_SIZE = "size";
    String ESTIMATE_OBJECTS_SIZE = "estimateRetainedSize";
    String FIND_PATHS_TO_CLOSEST_GC_ROOTS = "findPathsToClosestGcRoots";
    String GET_SHALLOW_SIZE_BY_CLASSES = "getShallowSizeByClasses";
    String GET_RETAINED_SIZE_BY_CLASSES = "getRetainedSizeByClasses";
    String GET_SHALLOW_AND_RETAINED_SIZE_BY_CLASSES = "getShallowAndRetainedSizeByClasses";
  }
}
