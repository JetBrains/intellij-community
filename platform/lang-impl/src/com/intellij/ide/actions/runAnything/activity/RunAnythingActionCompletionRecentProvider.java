// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.activity;

import com.intellij.openapi.actionSystem.AnAction;

public abstract class RunAnythingActionCompletionRecentProvider<V extends AnAction> extends RunAnythingActionExecutionProvider<V>
  implements RunAnythingMultiParametrizedExecutionProvider<V>, RunAnythingRecentProvider<V>, RunAnythingCompletionProvider<V>,
             RunAnythingHelpProviderBase<V> {
}