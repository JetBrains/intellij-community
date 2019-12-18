// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl;

import com.intellij.openapi.actionSystem.DataKey;

public interface MorePopupAware {
  DataKey<MorePopupAware> KEY = DataKey.create("MorePopupAware");

  boolean canShowMorePopup();
  void showMorePopup();
}
