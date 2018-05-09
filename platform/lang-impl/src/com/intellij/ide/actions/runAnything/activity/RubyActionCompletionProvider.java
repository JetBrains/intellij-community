// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.activity;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

/**
 * Marker interface for ruby-related providers collection
 */
public interface RubyActionCompletionProvider {
  static Module fetchModule(@NotNull DataContext dataContext) {
    return LangDataKeys.MODULE.getData(dataContext);
  }
}