// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.ex;

import com.intellij.openapi.actionSystem.AnAction;

import java.util.List;

public interface InlineActionsHolder {
  List<AnAction> getInlineActions();
}
