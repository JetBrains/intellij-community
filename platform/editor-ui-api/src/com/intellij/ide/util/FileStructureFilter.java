// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.util;

import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;


public interface FileStructureFilter extends Filter {
  @NotNull
  @NlsContexts.Checkbox
  String getCheckBoxText();

  Shortcut @NotNull [] getShortcut();
}
