// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages;

import com.intellij.usages.ConfigurableUsageTarget;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@ApiStatus.Internal
public final class UsageHistory {
  // the last element is the most recent
  private final Reference2ObjectLinkedOpenHashMap<ConfigurableUsageTarget, String> myHistory = new Reference2ObjectLinkedOpenHashMap<>();

  public void add(@NotNull ConfigurableUsageTarget usageTarget, String descriptiveName) {
    synchronized (myHistory) {
      final Set<Map.Entry<ConfigurableUsageTarget, String>> entries = myHistory.entrySet();
      entries.removeIf(entry -> entry.getValue().equals(descriptiveName));
      myHistory.put(usageTarget, descriptiveName);
      if (myHistory.size() > 15) {
        myHistory.removeFirst();
      }
    }
  }

  public @NotNull List<ConfigurableUsageTarget> getAll() {
    synchronized (myHistory) {
      List<ConfigurableUsageTarget> result = new ArrayList<>();
      final Set<ConfigurableUsageTarget> entries = myHistory.keySet();
      for (Iterator<ConfigurableUsageTarget> iterator = entries.iterator(); iterator.hasNext(); ) {
        final ConfigurableUsageTarget target = iterator.next();
        if (!target.isValid()) {
          iterator.remove();
        }
        else {
          result.add(target);
        }
      }
      return result;
    }
  }

  public @NotNull Map<ConfigurableUsageTarget, String> getAllHistoryData() {
    synchronized (myHistory) {
      final Set<ConfigurableUsageTarget> entries = myHistory.keySet();
      for (Iterator<ConfigurableUsageTarget> iterator = entries.iterator(); iterator.hasNext(); ) {
        final ConfigurableUsageTarget target = iterator.next();
        if (!target.isValid()) {
          iterator.remove();
        }
      }
      return myHistory;
    }
  }
}
