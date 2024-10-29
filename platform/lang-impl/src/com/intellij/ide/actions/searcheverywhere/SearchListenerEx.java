// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;

@ApiStatus.Internal
public interface SearchListenerEx extends SearchListener {

  void searchFinished(List<Object> items);
}
