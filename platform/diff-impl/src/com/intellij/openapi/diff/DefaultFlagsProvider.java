// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff;

import com.intellij.openapi.vcs.ex.Range;
import com.intellij.openapi.vcs.ex.VisibleRangeMerger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public abstract class DefaultFlagsProvider implements VisibleRangeMerger.FlagsProvider<DefaultLineFlags> {
  public static final VisibleRangeMerger.FlagsProvider<DefaultLineFlags> DEFAULT = new DefaultFlagsProvider() {
    @Override
    public @NotNull DefaultLineFlags getFlags(@NotNull Range range) {
      return DefaultLineFlags.DEFAULT;
    }
  };

  public static final VisibleRangeMerger.FlagsProvider<DefaultLineFlags> ALL_IGNORED = new DefaultFlagsProvider() {
    @Override
    public @NotNull DefaultLineFlags getFlags(@NotNull Range range) {
      return DefaultLineFlags.IGNORED;
    }
  };

  @Override
  public @NotNull DefaultLineFlags mergeFlags(@NotNull DefaultLineFlags flags1, @NotNull DefaultLineFlags flags2) {
    return flags1.isIgnored && flags2.isIgnored ? DefaultLineFlags.IGNORED : DefaultLineFlags.DEFAULT;
  }

  @Override
  public boolean shouldIgnoreInnerRanges(@NotNull DefaultLineFlags flag) {
    return flag.isIgnored;
  }
}
