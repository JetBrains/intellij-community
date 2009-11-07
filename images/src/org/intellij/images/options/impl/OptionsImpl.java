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
import org.intellij.images.options.ExternalEditorOptions;
import org.intellij.images.options.Options;
import org.jdom.Element;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * Default options implementation.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
final class OptionsImpl implements Options, JDOMExternalizable {
    /**
     * Property change support (from injection)
     */
    private final PropertyChangeSupport propertyChangeSupport;

    private final EditorOptions editorOptions;
    private final ExternalEditorOptions externalEditorOptions;

    OptionsImpl() {
        propertyChangeSupport = new PropertyChangeSupport(this);
        editorOptions = new EditorOptionsImpl(propertyChangeSupport);
        externalEditorOptions = new ExternalEditorOptionsImpl(propertyChangeSupport);
    }

    public EditorOptions getEditorOptions() {
        return editorOptions;
    }

    public ExternalEditorOptions getExternalEditorOptions() {
        return externalEditorOptions;
    }

    public void inject(Options options) {
        editorOptions.inject(options.getEditorOptions());
        externalEditorOptions.inject(options.getExternalEditorOptions());
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(propertyName, listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(propertyName, listener);
    }

    public boolean setOption(String name, Object value) {
        return editorOptions.setOption(name, value) || externalEditorOptions.setOption(name, value);
    }

    public void readExternal(Element element) throws InvalidDataException {
        ((JDOMExternalizable)editorOptions).readExternal(element);
        ((JDOMExternalizable)externalEditorOptions).readExternal(element);
    }

    public void writeExternal(Element element) throws WriteExternalException {
        ((JDOMExternalizable)editorOptions).writeExternal(element);
        ((JDOMExternalizable)externalEditorOptions).writeExternal(element);
    }

    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof Options)) {
            return false;
        }
        Options otherOptions = (Options)obj;
        EditorOptions editorOptions = otherOptions.getEditorOptions();
        ExternalEditorOptions externalEditorOptions = otherOptions.getExternalEditorOptions();
        return editorOptions != null && editorOptions.equals(getEditorOptions()) &&
            externalEditorOptions != null && externalEditorOptions.equals(getExternalEditorOptions());
    }

    public int hashCode() {
        int result;
        result = (editorOptions != null ? editorOptions.hashCode() : 0);
        result = 29 * result + (externalEditorOptions != null ? externalEditorOptions.hashCode() : 0);
        return result;
    }
}
