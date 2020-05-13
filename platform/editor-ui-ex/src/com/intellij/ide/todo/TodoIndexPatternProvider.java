// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.todo;

import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.IndexPatternProvider;
import org.jetbrains.annotations.NotNull;

public final class TodoIndexPatternProvider implements IndexPatternProvider {
  public static TodoIndexPatternProvider getInstance() {
    return EP_NAME.findExtensionOrFail(TodoIndexPatternProvider.class);
  }

  @Override
  public IndexPattern @NotNull [] getIndexPatterns() {
    return TodoConfiguration.getInstance().getIndexPatterns();
  }
}
