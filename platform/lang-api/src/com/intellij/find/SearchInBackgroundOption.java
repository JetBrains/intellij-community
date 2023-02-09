// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.find;

import com.intellij.ide.GeneralSettings;
import com.intellij.openapi.progress.PerformInBackgroundOption;

public final class SearchInBackgroundOption implements PerformInBackgroundOption {
  @Override
  public boolean shouldStartInBackground() {
    return GeneralSettings.getInstance().isSearchInBackground();
  }

  @Override
  public void processSentToBackground() {
    GeneralSettings.getInstance().setSearchInBackground(true);
  }
}
