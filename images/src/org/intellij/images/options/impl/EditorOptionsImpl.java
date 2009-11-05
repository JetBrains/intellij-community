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

    EditorOptionsImpl(PropertyChangeSupport propertyChangeSupport) {
        gridOptions = new GridOptionsImpl(propertyChangeSupport);
        transparencyChessboardOptions = new TransparencyChessboardOptionsImpl(propertyChangeSupport);
        zoomOptions = new ZoomOptionsImpl(propertyChangeSupport);
    }

    public GridOptions getGridOptions() {
        return gridOptions;
    }

    public TransparencyChessboardOptions getTransparencyChessboardOptions() {
        return transparencyChessboardOptions;
    }

    public ZoomOptions getZoomOptions() {
        return zoomOptions;
    }

    public EditorOptions clone() throws CloneNotSupportedException {
        return (EditorOptions)super.clone();
    }

    public void inject(EditorOptions options) {
        gridOptions.inject(options.getGridOptions());
        transparencyChessboardOptions.inject(options.getTransparencyChessboardOptions());
        zoomOptions.inject(options.getZoomOptions());
    }

    public boolean setOption(String name, Object value) {
        return gridOptions.setOption(name, value) ||
                   transparencyChessboardOptions.setOption(name, value) ||
                   zoomOptions.setOption(name, value);
    }

    public void readExternal(Element element) throws InvalidDataException {
        ((JDOMExternalizable)gridOptions).readExternal(element);
        ((JDOMExternalizable)transparencyChessboardOptions).readExternal(element);
        ((JDOMExternalizable)zoomOptions).readExternal(element);
    }

    public void writeExternal(Element element) throws WriteExternalException {
        ((JDOMExternalizable)gridOptions).writeExternal(element);
        ((JDOMExternalizable)transparencyChessboardOptions).writeExternal(element);
        ((JDOMExternalizable)zoomOptions).writeExternal(element);
    }

    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof EditorOptions)) {
            return false;
        }
        EditorOptions otherOptions = (EditorOptions)obj;
        GridOptions gridOptions = otherOptions.getGridOptions();
        TransparencyChessboardOptions chessboardOptions = otherOptions.getTransparencyChessboardOptions();
        ZoomOptions zoomOptions = otherOptions.getZoomOptions();
        return gridOptions != null && gridOptions.equals(getGridOptions()) &&
            chessboardOptions != null && chessboardOptions.equals(getTransparencyChessboardOptions()) &&
            zoomOptions != null && zoomOptions.equals(getZoomOptions());
    }

    public int hashCode() {
        int result;
        result = (gridOptions != null ? gridOptions.hashCode() : 0);
        result = 29 * result + (transparencyChessboardOptions != null ? transparencyChessboardOptions.hashCode() : 0);
        result = 29 * result + (zoomOptions != null ? zoomOptions.hashCode() : 0);
        return result;
    }
}
