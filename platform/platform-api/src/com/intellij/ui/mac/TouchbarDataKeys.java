// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DataKey;

public interface TouchbarDataKeys {
  DataKey<ActionGroup> ACTIONS_KEY = DataKey.create("TouchBarActions");
}
