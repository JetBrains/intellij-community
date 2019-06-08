// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Alexander Lobas
 */
public class BgProgressIndicator extends AbstractProgressIndicatorExBase {
  public BgProgressIndicator() {
    super.setText("Downloading...");
    setIndeterminate(false);
  }

  @Override
  public void setText(String text) {
  }

  @Override
  public void setText2(String text) {
  }

  public void removeStateDelegate(@Nullable ProgressIndicatorEx delegate) {
    List<ProgressIndicatorEx> stateDelegates =
      ReflectionUtil.getField(AbstractProgressIndicatorExBase.class, this, List.class, "myStateDelegates");
    synchronized (getLock()) {
      if (stateDelegates == null) {
        return;
      }
      if (delegate == null) {
        stateDelegates.clear();
      }
      else {
        stateDelegates.remove(delegate);
      }
    }
  }
}