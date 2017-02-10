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
package com.intellij.ui.components;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * A {@link DefaultBoundedRangeModel} that doesn't notify its scrollbar UI listener on value changes
 * while the scrollbar value is adjusted (required for interpolation of the thumb input).
 */
public class SmoothBoundedRangeModel extends DefaultBoundedRangeModel {
  private static final String SCROLLBAR_UI_LISTENER_PATTERN = "ScrollBarUI";

  private final JScrollBar myScrollBar;

  public SmoothBoundedRangeModel(JScrollBar scrollBar) {
    myScrollBar = scrollBar;
  }

  /**
   * Implementation of {@link DefaultBoundedRangeModel#fireStateChanged()} with filtering.
   */
  @Override
  protected void fireStateChanged() {
    Object[] listeners = listenerList.getListenerList();
    for (int i = listeners.length - 2; i >= 0; i -= 2) {
      if (listeners[i] == ChangeListener.class) {
        if (changeEvent == null) {
          changeEvent = new ChangeEvent(this);
        }
        ChangeListener listener = (ChangeListener)listeners[i + 1];
        if (!(myScrollBar.getValueIsAdjusting() && listener.getClass().getName().contains(SCROLLBAR_UI_LISTENER_PATTERN))) {
          listener.stateChanged(changeEvent);
        }
      }
    }
  }
}
