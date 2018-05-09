// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.groups;

import com.intellij.ide.actions.runAnything.activity.RunAnythingCompletionProvider;
import org.jetbrains.annotations.NotNull;

public class RunAnythingCompletionProviderGroupImpl<V, P extends RunAnythingCompletionProvider<V>>
  extends RunAnythingCompletionProviderGroup<V, P> {
  @NotNull private final P myProvider;

  public RunAnythingCompletionProviderGroupImpl(@NotNull P provider) {
    myProvider = provider;
  }

  @NotNull
  @Override
  protected P getProvider() {
    return myProvider;
  }
}