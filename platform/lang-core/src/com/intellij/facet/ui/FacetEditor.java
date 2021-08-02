// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.facet.ui;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.NonExtendable
public interface FacetEditor {

  FacetEditorTab[] getEditorTabs();

  <T extends FacetEditorTab> T getEditorTab(@NotNull Class<T> aClass);

}
