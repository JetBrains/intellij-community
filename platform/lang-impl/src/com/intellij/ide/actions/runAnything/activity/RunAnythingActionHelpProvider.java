// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.activity;

import com.intellij.openapi.actionSystem.AnAction;

public abstract class RunAnythingActionHelpProvider<V extends AnAction> extends RunAnythingActionExecutionProvider<V>
  implements RunAnythingHelpProviderBase<V>, RunAnythingActivityProvider<V> {
}