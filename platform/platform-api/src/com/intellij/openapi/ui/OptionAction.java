// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
* @author Konstantin Bulenkov
*/
public interface OptionAction extends Action {
  String AN_ACTION = "AnAction";

  @NotNull
  Action[] getOptions();
}
