// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.popup;

import com.intellij.openapi.ui.popup.JBPopupListener;

public abstract class PopupUpdateProcessorBase implements JBPopupListener {
  public abstract void updatePopup(Object lookupItemObject);
}
