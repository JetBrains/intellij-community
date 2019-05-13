// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.ReferringObjectsProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MemoryAgentReferringObjectProvider implements ReferringObjectsProvider {
  private static final Logger LOG = Logger.getInstance(MemoryAgentReferringObjectProvider.class);

  private final Map<ObjectReference, Integer> myReversedMap = new HashMap<>();
  private final List<ObjectReference> myDirectMap;
  private final List<List<Integer>> myPaths;

  public MemoryAgentReferringObjectProvider(@NotNull List<ObjectReference> values, @NotNull List<List<Integer>> paths) {
    myDirectMap = values;
    for (int i = 0; i < values.size(); i++) {
      myReversedMap.put(values.get(i), i);
    }

    myPaths = paths;
  }

  @NotNull
  @Override
  public List<ObjectReference> getReferringObjects(@NotNull ObjectReference value, long limit) {
    Integer index = myReversedMap.get(value);
    if (index == null) {
      LOG.error("Could not find referring object for reference " + value.toString());
      return Collections.emptyList();
    }

    return myPaths.get(index).stream().limit(limit).map(ix -> myDirectMap.get(ix)).collect(Collectors.toList());
  }
}
