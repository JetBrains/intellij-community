// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.actions;

import com.intellij.openapi.actionSystem.AnAction;

@SuppressWarnings("ComponentNotRegistered")
public class LfeActionPrevOccurrence extends LfeActionNextOccurence {

  public LfeActionPrevOccurrence(AnAction originalAction) {
    super(originalAction);
  }

  @Override
  protected boolean isForwardDirection() {
    return false;
  }
}
