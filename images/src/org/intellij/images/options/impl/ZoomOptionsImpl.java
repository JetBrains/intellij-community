/*
 * Copyright 2004-2005 Alexey Efimov
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
package org.intellij.images.options.impl;

import org.intellij.images.options.ZoomOptions;

import java.awt.*;
import java.beans.PropertyChangeSupport;
import java.util.Objects;

/**
 * Zoom options implementation.
 */
final class ZoomOptionsImpl implements ZoomOptions {
  private int prefferedWidth = DEFAULT_PREFFERED_SIZE.width;
  private int prefferedHeight = DEFAULT_PREFFERED_SIZE.height;
  private final PropertyChangeSupport propertyChangeSupport;

  ZoomOptionsImpl(PropertyChangeSupport propertyChangeSupport) {
    this.propertyChangeSupport = propertyChangeSupport;
  }

  @Override
  public Dimension getPrefferedSize() {
    return new Dimension(prefferedWidth, prefferedHeight);
  }

  void setPrefferedSize(Dimension prefferedSize) {
    if (prefferedSize == null) {
      prefferedSize = DEFAULT_PREFFERED_SIZE;
    }
    setPrefferedWidth(prefferedSize.width);
    setPrefferedHeight(prefferedSize.height);
  }

  void setPrefferedWidth(int prefferedWidth) {
    int oldValue = this.prefferedWidth;
    if (oldValue != prefferedWidth) {
      this.prefferedWidth = prefferedWidth;
      propertyChangeSupport.firePropertyChange(ATTR_PREFFERED_WIDTH, oldValue, this.prefferedWidth);
    }
  }

  void setPrefferedHeight(int prefferedHeight) {
    int oldValue = this.prefferedHeight;
    if (oldValue != prefferedHeight) {
      this.prefferedHeight = prefferedHeight;
      propertyChangeSupport.firePropertyChange(ATTR_PREFFERED_HEIGHT, oldValue, this.prefferedHeight);
    }
  }

  @Override
  public void inject(ZoomOptions options) {
    setPrefferedSize(options.getPrefferedSize());
  }

  @Override
  public boolean setOption(String name, Object value) {
    return true;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ZoomOptions otherOptions)) {
      return false;
    }

    Dimension prefferedSize = otherOptions.getPrefferedSize();
    return prefferedSize != null && prefferedHeight == prefferedSize.height &&
           prefferedWidth == prefferedSize.width;
  }

  @Override
  public int hashCode() {
    return Objects.hash(prefferedWidth, prefferedHeight, propertyChangeSupport);
  }
}
