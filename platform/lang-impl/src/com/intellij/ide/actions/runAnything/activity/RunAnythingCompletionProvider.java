// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.activity;

import com.intellij.ide.actions.runAnything.groups.RunAnythingCompletionProviderGroup;
import com.intellij.ide.actions.runAnything.groups.RunAnythingCompletionProviderGroupImpl;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public interface RunAnythingCompletionProvider<V> extends RunAnythingMultiParametrizedExecutionProvider<V> {
  @NotNull
  String getGroupTitle();

  @NotNull
  default String getId() {
    return getGroupTitle();
  }

  @NotNull
  default RunAnythingCompletionProviderGroup createGroup() {
    //noinspection unchecked
    return new RunAnythingCompletionProviderGroupImpl(this);
  }

  @NotNull
  static List<RunAnythingCompletionProvider> getCompletionProviders() {
    return StreamEx.of(EP_NAME.getExtensions()).select(RunAnythingCompletionProvider.class).collect(Collectors.toList());
  }
}