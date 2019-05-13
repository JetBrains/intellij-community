// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.todo;

import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.IndexPatternProvider;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class TodoIndexPatternProvider implements IndexPatternProvider {
  private final TodoConfiguration myConfiguration;

  public static TodoIndexPatternProvider getInstance() {
    for (IndexPatternProvider provider : EP_NAME.getExtensionList()) {
      if (provider instanceof TodoIndexPatternProvider) {
        return (TodoIndexPatternProvider) provider;
      }
    }
    assert false: "Couldn't find self in extensions list";
    return null;
  }

  public TodoIndexPatternProvider(TodoConfiguration configuration) {
    myConfiguration = configuration;
  }

  @Override
  @NotNull public IndexPattern[] getIndexPatterns() {
    return myConfiguration.getIndexPatterns();
  }
}
