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

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.intellij.images.options.EditorOptions;
import org.intellij.images.options.GridOptions;
import org.intellij.images.options.TransparencyChessboardOptions;
import org.intellij.images.options.ZoomOptions;
import org.jdom.Element;

import java.beans.PropertyChangeSupport;

/**
 * Editor options implementation.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
final class EditorOptionsImpl implements EditorOptions, JDOMExternalizable {
  private final GridOptions gridOptions;
  private final TransparencyChessboardOptions transparencyChessboardOptions;
  private final ZoomOptions zoomOptions;
  private boolean fileNameVisible = true;
  private boolean fileSizeVisible = true;

  EditorOptionsImpl(PropertyChangeSupport propertyChangeSupport) {
    gridOptions = new GridOptionsImpl(propertyChangeSupport);
    transparencyChessboardOptions = new TransparencyChessboardOptionsImpl(propertyChangeSupport);
    zoomOptions = new ZoomOptionsImpl(propertyChangeSupport);
  }

  @Override
  public GridOptions getGridOptions() {
    return gridOptions;
  }

  @Override
  public TransparencyChessboardOptions getTransparencyChessboardOptions() {
    return transparencyChessboardOptions;
  }

  @Override
  public ZoomOptions getZoomOptions() {
    return zoomOptions;
  }

  @Override
  public EditorOptions clone() throws CloneNotSupportedException {
    return (EditorOptions)super.clone();
  }

  @Override
  public void inject(EditorOptions options) {
    gridOptions.inject(options.getGridOptions());
    transparencyChessboardOptions.inject(options.getTransparencyChessboardOptions());
    zoomOptions.inject(options.getZoomOptions());
  }

  @Override
  public boolean setOption(String name, Object value) {
    return gridOptions.setOption(name, value) ||
           transparencyChessboardOptions.setOption(name, value) ||
           zoomOptions.setOption(name, value);
  }

  @Override
  public boolean isFileNameVisible() {
    return fileNameVisible;
  }

  @Override
  public void setFileNameVisible(boolean fileNameVisible) {
    this.fileNameVisible = fileNameVisible;
  }

  @Override
  public void setFileSizeVisible(boolean fileSizeVisible) {
    this.fileSizeVisible = fileSizeVisible;
  }

  @Override
  public boolean isFileSizeVisible() {
    return fileSizeVisible;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    ((JDOMExternalizable)transparencyChessboardOptions).readExternal(element);
    String fileNameVisibleAttr = element.getAttributeValue("fileNameVisible");
    fileNameVisible = fileNameVisibleAttr == null || Boolean.parseBoolean(fileNameVisibleAttr);
    String fileSizeVisibleAttr = element.getAttributeValue("fileSizeVisible");
    fileSizeVisible = fileNameVisibleAttr == null || Boolean.parseBoolean(fileSizeVisibleAttr);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    ((JDOMExternalizable)transparencyChessboardOptions).writeExternal(element);
    if (!fileNameVisible) {
      element.setAttribute("fileNameVisible", "false");
    }
    if (!fileSizeVisible) {
      element.setAttribute("fileSizeVisible", "false");
    }
  }
}
