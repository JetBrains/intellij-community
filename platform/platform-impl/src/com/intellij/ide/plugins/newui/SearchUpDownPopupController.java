// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.event.KeyEvent;

@ApiStatus.Internal
public abstract class SearchUpDownPopupController extends SearchPopupController {
  private EventHandler myEventHandler;
  private EventHandler mySearchResultEventHandler;

  public SearchUpDownPopupController(@NotNull PluginSearchTextField searchTextField) {
    super(searchTextField);
  }

  public void setEventHandler(@NotNull EventHandler eventHandler) {
    myEventHandler = eventHandler;
  }

  public void setSearchResultEventHandler(@NotNull EventHandler eventHandler) {
    mySearchResultEventHandler = eventHandler;
  }

  @Override
  public boolean handleUpDown(@NotNull KeyEvent event) {
    if (myPopup != null && myPopup.list != null) {
      return super.handleUpDown(event);
    }
    if (myTextField.getText().isEmpty()) {
      if (myEventHandler != null) {
        myEventHandler.handleUpDown(event);
      }
    }
    else if (mySearchResultEventHandler != null) {
      mySearchResultEventHandler.handleUpDown(event);
    }
    return false;
  }
}