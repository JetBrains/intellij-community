/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui;

import com.intellij.openapi.util.ScalableIcon;
import com.intellij.util.ui.JBUI;

import javax.swing.*;

/**
 * A helper class for adjusting a scaled icon width/height values.
 *
 * @author tav
 */
abstract class AbstractSizeAdjustingIcon implements Icon, ScalableIcon {
  protected int myWidth;
  protected int myHeight;

  /**
   * Sticks to the global JBUI.scale(1f) lazily.
   */
  private float globalScale;

  protected AbstractSizeAdjustingIcon() {
    globalScale = JBUI.scale(1f);
  }

  @Override
  public int getIconWidth() {
    checkRescale();
    return myWidth;
  }

  @Override
  public int getIconHeight() {
    checkRescale();
    return myHeight;
  }

  private void checkRescale() {
    if (globalScale != JBUI.scale(1f)) {
      globalScale = JBUI.scale(1f);
      adjustSize();
    }
  }

  /**
   * Called to let the icon adjust its size when re-globalScale is detected.
   */
  protected abstract void adjustSize();

  /**
   * Scales the icon relative to the {@link #globalScale}.
   *
   * @param scaleFactor the scale factor
   * @return the icon effectively scaled by @{code scaleFactor*globalScale}
   */
  @Override
  abstract public Icon scale(float scaleFactor);
}
