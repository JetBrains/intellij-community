// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.slicer;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.usageView.UsageInfo;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

// rehash map on each PSI modification since SmartPsiPointer's hashCode() and equals() are changed
public final class DuplicateMap {
  private static final Hash.Strategy<SliceUsage> USAGE_INFO_EQUALITY = new Hash.Strategy<>() {
    @Override
    public int hashCode(@Nullable SliceUsage object) {
      if (object == null) {
        return 0;
      }

      UsageInfo info = object.getUsageInfo();
      TextRange range = info.getRangeInElement();
      return range == null ? 0 : range.hashCode();
    }

    @Override
    public boolean equals(@Nullable SliceUsage o1, @Nullable SliceUsage o2) {
      return o1 == o2 || (o1 != null && o2 != null && o1.getUsageInfo().equals(o2.getUsageInfo()));
    }
  };
  private final Map<SliceUsage, SliceNode> myDuplicates = new Object2ObjectOpenCustomHashMap<>(USAGE_INFO_EQUALITY);

  SliceNode putNodeCheckDupe(@NotNull SliceNode node) {
    return ApplicationManager.getApplication().runReadAction((Computable<? extends SliceNode>)() -> {
      SliceUsage usage = node.getValue();
      return myDuplicates.putIfAbsent(usage, node);
    });
  }

  public void clear() {
    myDuplicates.clear();
  }
}
