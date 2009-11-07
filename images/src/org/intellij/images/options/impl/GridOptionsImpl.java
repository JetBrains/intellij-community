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
import org.intellij.images.options.GridOptions;
import org.jdom.Element;

import java.awt.*;
import java.beans.PropertyChangeSupport;

/**
 * Grid options implementation.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
final class GridOptionsImpl implements GridOptions, JDOMExternalizable {
    private boolean showDefault;
    private int lineMinZoomFactor = DEFAULT_LINE_ZOOM_FACTOR;
    private int lineSpan = DEFAULT_LINE_SPAN;
    private Color lineColor = DEFAULT_LINE_COLOR;
    private final PropertyChangeSupport propertyChangeSupport;

    GridOptionsImpl(PropertyChangeSupport propertyChangeSupport) {
        this.propertyChangeSupport = propertyChangeSupport;
    }

    public boolean isShowDefault() {
        return showDefault;
    }

    public int getLineZoomFactor() {
        return lineMinZoomFactor;
    }

    public int getLineSpan() {
        return lineSpan;
    }

    public Color getLineColor() {
        return lineColor;
    }

    void setShowDefault(boolean showDefault) {
        boolean oldValue = this.showDefault;
        if (oldValue != showDefault) {
            this.showDefault = showDefault;
            propertyChangeSupport.firePropertyChange(ATTR_SHOW_DEFAULT, oldValue, this.showDefault);
        }
    }

    void setLineMinZoomFactor(int lineMinZoomFactor) {
        int oldValue = this.lineMinZoomFactor;
        if (oldValue != lineMinZoomFactor) {
            this.lineMinZoomFactor = lineMinZoomFactor;
            propertyChangeSupport.firePropertyChange(ATTR_LINE_ZOOM_FACTOR, oldValue, this.lineMinZoomFactor);
        }
    }

    void setLineSpan(int lineSpan) {
        int oldValue = this.lineSpan;
        if (oldValue != lineSpan) {
            this.lineSpan = lineSpan;
            propertyChangeSupport.firePropertyChange(ATTR_LINE_SPAN, oldValue, this.lineSpan);
        }
    }

    void setLineColor(Color lineColor) {
        Color oldColor = this.lineColor;
        if (lineColor == null) {
            this.lineColor = DEFAULT_LINE_COLOR;
        }
        if (!oldColor.equals(lineColor)) {
            this.lineColor = lineColor;
            propertyChangeSupport.firePropertyChange(ATTR_LINE_COLOR, oldColor, this.lineColor);
        }
    }

    public void inject(GridOptions options) {
        setShowDefault(options.isShowDefault());
        setLineMinZoomFactor(options.getLineZoomFactor());
        setLineSpan(options.getLineSpan());
        setLineColor(options.getLineColor());
    }

    public boolean setOption(String name, Object value) {
        if (ATTR_SHOW_DEFAULT.equals(name)) {
            setShowDefault((Boolean) value);
        } else if (ATTR_LINE_ZOOM_FACTOR.equals(name)) {
            setLineMinZoomFactor((Integer) value);
        } else if (ATTR_LINE_SPAN.equals(name)) {
            setLineSpan((Integer) value);
        } else if (ATTR_LINE_COLOR.equals(name)) {
            setLineColor((Color) value);
        } else {
            return false;
        }
        return true;
    }

    public void readExternal(Element element) {
        showDefault = JDOMExternalizer.readBoolean(element, ATTR_SHOW_DEFAULT);
        lineMinZoomFactor = JDOMExternalizer.readInteger(element, ATTR_LINE_ZOOM_FACTOR, DEFAULT_LINE_ZOOM_FACTOR);
        lineSpan = JDOMExternalizer.readInteger(element, ATTR_LINE_SPAN, DEFAULT_LINE_SPAN);
        lineColor = JDOMExternalizerEx.readColor(element, ATTR_LINE_COLOR, DEFAULT_LINE_COLOR);
    }

    public void writeExternal(Element element) {
        JDOMExternalizer.write(element, ATTR_SHOW_DEFAULT, showDefault);
        JDOMExternalizer.write(element, ATTR_LINE_ZOOM_FACTOR, lineMinZoomFactor);
        JDOMExternalizer.write(element, ATTR_LINE_SPAN, lineSpan);
        JDOMExternalizerEx.write(element, ATTR_LINE_COLOR, lineColor);
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof GridOptions)) {
            return false;
        }

        GridOptions otherOptions = (GridOptions) obj;
        return lineMinZoomFactor == otherOptions.getLineZoomFactor() &&
            lineSpan == otherOptions.getLineSpan() &&
            showDefault == otherOptions.isShowDefault() &&
            lineColor != null ? lineColor.equals(otherOptions.getLineColor()) : otherOptions.getLineColor() == null;
    }

    public int hashCode() {
        int result;
        result = (showDefault ? 1 : 0);
        result = 29 * result + lineMinZoomFactor;
        result = 29 * result + lineSpan;
        result = 29 * result + (lineColor != null ? lineColor.hashCode() : 0);
        return result;
    }
}
