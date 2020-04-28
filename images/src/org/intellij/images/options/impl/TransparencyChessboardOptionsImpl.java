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

import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.JDOMExternalizer;
import com.intellij.util.JdomKt;
import org.intellij.images.options.DefaultImageEditorSettings;
import org.intellij.images.options.TransparencyChessboardOptions;
import org.jdom.Element;

import java.awt.*;
import java.beans.PropertyChangeSupport;

/**
 * Background options implementation.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
final class TransparencyChessboardOptionsImpl implements TransparencyChessboardOptions, JDOMExternalizable {
  private int cellSize = DEFAULT_CELL_SIZE;
  private Color whiteColor = DEFAULT_WHITE_COLOR;
  private Color blackColor = DEFAULT_BLACK_COLOR;
  private final PropertyChangeSupport propertyChangeSupport;

  TransparencyChessboardOptionsImpl(PropertyChangeSupport propertyChangeSupport) {
    this.propertyChangeSupport = propertyChangeSupport;
  }

  @Override
  public boolean isShowDefault() {
    return DefaultImageEditorSettings.INSTANCE.getShowChessboard();
  }

  @Override
  public int getCellSize() {
    return DefaultImageEditorSettings.INSTANCE.getChessboardCellSize();
  }

  @Override
  public Color getWhiteColor() {
    return whiteColor;
  }

  @Override
  public Color getBlackColor() {
    return blackColor;
  }

  void setShowDefault(boolean showDefault) {
  }

  void setCellSize(int cellSize) {
    int oldValue = this.cellSize;
    if (oldValue != cellSize) {
      this.cellSize = cellSize;
      propertyChangeSupport.firePropertyChange(ATTR_CELL_SIZE, oldValue, this.cellSize);
    }
  }

  void setWhiteColor(Color whiteColor) {
    Color oldValue = this.whiteColor;
    if (whiteColor == null) {
      this.whiteColor = DEFAULT_WHITE_COLOR;
    }
    if (!oldValue.equals(whiteColor)) {
      this.whiteColor = whiteColor;
      propertyChangeSupport.firePropertyChange(ATTR_WHITE_COLOR, oldValue, this.whiteColor);
    }
  }

  void setBlackColor(Color blackColor) {
    Color oldValue = this.blackColor;
    if (blackColor == null) {
      blackColor = DEFAULT_BLACK_COLOR;
    }
    if (!oldValue.equals(blackColor)) {
      this.blackColor = blackColor;
      propertyChangeSupport.firePropertyChange(ATTR_BLACK_COLOR, oldValue, this.blackColor);
    }
  }

  @Override
  public void inject(TransparencyChessboardOptions options) {
    setShowDefault(options.isShowDefault());
    setCellSize(options.getCellSize());
    setWhiteColor(options.getWhiteColor());
    setBlackColor(options.getBlackColor());
  }

  @Override
  public boolean setOption(String name, Object value) {
    if (ATTR_SHOW_DEFAULT.equals(name)) {
      setShowDefault((Boolean)value);
    }
    else if (ATTR_CELL_SIZE.equals(name)) {
      setCellSize((Integer)value);
    }
    else if (ATTR_WHITE_COLOR.equals(name)) {
      setWhiteColor((Color)value);
    }
    else if (ATTR_BLACK_COLOR.equals(name)) {
      setBlackColor((Color)value);
    }
    else {
      return false;
    }
    return true;
  }

  @Override
  public void readExternal(Element element) {
    setShowDefault(JDOMExternalizer.readBoolean(element, ATTR_SHOW_DEFAULT));
    setCellSize(JDOMExternalizer.readInteger(element, ATTR_CELL_SIZE, DEFAULT_CELL_SIZE));
    setWhiteColor(JDOMExternalizerEx.readColor(element, ATTR_WHITE_COLOR, DEFAULT_WHITE_COLOR));
    setBlackColor(JDOMExternalizerEx.readColor(element, ATTR_BLACK_COLOR, DEFAULT_BLACK_COLOR));
  }

  @Override
  public void writeExternal(Element element) {
    JdomKt.addOptionTag(element, ATTR_SHOW_DEFAULT, Boolean.toString(isShowDefault()), "setting");
    JdomKt.addOptionTag(element, ATTR_CELL_SIZE, Integer.toString(cellSize), "setting");
    JDOMExternalizerEx.write(element, ATTR_WHITE_COLOR, whiteColor);
    JDOMExternalizerEx.write(element, ATTR_BLACK_COLOR, blackColor);
  }
}
