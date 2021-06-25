// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;

/**
 * @author Alexander Lobas
 */
final class BgProgressIndicator extends AbstractProgressIndicatorExBase {

  BgProgressIndicator() {
    super.setText(IdeBundle.message("progress.text.downloading"));
    setIndeterminate(false);
  }

  public void removeStateDelegates() {
    super.removeAllStateDelegates();
  }

  @Override
  public void setText(String text) {
  }

  @Override
  public void setText2(String text) {
  }
}