// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.ex;

import com.intellij.openapi.actionSystem.AnAction;

import java.util.Collections;
import java.util.List;

/** @deprecated Use {@link com.intellij.ui.popup.PopupFactoryImpl.ActionItem#INLINE_ACTIONS} instead */
@Deprecated(forRemoval = true)
public interface InlineActionsHolder {

  default List<AnAction> getInlineActions() { return Collections.emptyList(); }
}
