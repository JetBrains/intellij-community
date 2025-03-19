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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.intellij.images.options.EditorOptions;
import org.intellij.images.options.ExternalEditorOptions;
import org.intellij.images.options.Options;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

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
  private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

  private final EditorOptions editorOptions = new EditorOptionsImpl(propertyChangeSupport);
  private final ExternalEditorOptions externalEditorOptions = new ExternalEditorOptionsImpl(propertyChangeSupport);

  @Override
  public @NotNull EditorOptions getEditorOptions() {
    return editorOptions;
  }

  @Override
  public @NotNull ExternalEditorOptions getExternalEditorOptions() {
    return externalEditorOptions;
  }

  @Override
  public void inject(@NotNull Options options) {
    editorOptions.inject(options.getEditorOptions());
    externalEditorOptions.inject(options.getExternalEditorOptions());
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener, @NotNull Disposable parent) {
    propertyChangeSupport.addPropertyChangeListener(listener);
    Disposer.register(parent, () -> propertyChangeSupport.removePropertyChangeListener(listener));
  }

  @Override
  public boolean setOption(@NotNull String name, Object value) {
    return editorOptions.setOption(name, value) || externalEditorOptions.setOption(name, value);
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    ((JDOMExternalizable)editorOptions).readExternal(element);
    ((JDOMExternalizable)externalEditorOptions).readExternal(element);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    ((JDOMExternalizable)editorOptions).writeExternal(element);
    ((JDOMExternalizable)externalEditorOptions).writeExternal(element);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof Options otherOptions)) {
      return false;
    }
    EditorOptions editorOptions = otherOptions.getEditorOptions();
    ExternalEditorOptions externalEditorOptions = otherOptions.getExternalEditorOptions();
    return editorOptions.equals(getEditorOptions()) && externalEditorOptions.equals(getExternalEditorOptions());
  }

  @Override
  public int hashCode() {
    int result = editorOptions.hashCode();
    result = 29 * result + externalEditorOptions.hashCode();
    return result;
  }
}
