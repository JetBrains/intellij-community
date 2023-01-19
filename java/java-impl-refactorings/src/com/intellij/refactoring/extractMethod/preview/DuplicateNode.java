// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.preview;

import com.intellij.refactoring.util.duplicates.Match;
import org.jetbrains.annotations.NotNull;

class DuplicateNode extends FragmentNode {
  private boolean myExcluded;

  DuplicateNode(@NotNull Match duplicate) {
    super(duplicate.getMatchStart(), duplicate.getMatchEnd(), new ExtractableFragment(duplicate.getMatchStart(), duplicate.getMatchEnd()));
  }

  @Override
  public boolean isExcluded() {
    return myExcluded;
  }

  public void setExcluded(boolean excluded) {
    myExcluded = excluded;
  }
}
