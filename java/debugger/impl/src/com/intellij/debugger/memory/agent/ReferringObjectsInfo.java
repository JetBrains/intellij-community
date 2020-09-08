// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.ReferringObject;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Stores tree of backward references for particular object
 */
public class ReferringObjectsInfo {
  private static final Logger LOG = Logger.getInstance(ReferringObjectsInfo.class);

  private final Map<ObjectReference, Integer> myReversedMap = new HashMap<>();
  private final List<List<ReferringObject>> myReferrers;

  public ReferringObjectsInfo(@NotNull List<? extends ObjectReference> values,
                              @NotNull List<List<ReferringObject>> referrers) {
    for (int i = 0; i < values.size(); i++) {
      myReversedMap.put(values.get(i), i);
    }

    myReferrers = referrers;
  }

  public boolean hasReferringObjectsFor(@NotNull ObjectReference reference) {
    return myReversedMap.containsKey(reference);
  }

  @NotNull
  @TestOnly
  public List<ObjectReference> getAllReferrers(@NotNull ObjectReference value) {
    return myReferrers.get(myReversedMap.get(value)).stream()
      .distinct()
      .map(ReferringObject::getReference)
      .filter(ref -> ref != null)
      .collect(Collectors.toList());
  }

  @NotNull
  public List<ReferringObject> getReferringObjects(@NotNull ObjectReference value, long limit) {
    Integer index = myReversedMap.get(value);
    if (index == null) {
      LOG.error("Could not find referring object for reference " + value.toString());
      return Collections.emptyList();
    }

    return myReferrers.get(index).stream()
      .distinct()
      .limit(limit)
      .collect(Collectors.toList());
  }
}
