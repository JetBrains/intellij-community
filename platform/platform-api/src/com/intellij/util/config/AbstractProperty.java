/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.util.config;

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NonNls;

import java.util.Comparator;

public abstract class AbstractProperty<T> {
  public static final Comparator<AbstractProperty> NAME_COMPARATOR = new Comparator<AbstractProperty>() {
    public int compare(AbstractProperty property, AbstractProperty property1) {
      return property.getName().compareTo(property1.getName());
    }
  };

  @NonNls public abstract String getName();
  public abstract T getDefault(AbstractPropertyContainer container);
  public abstract T copy(T value);

  public boolean areEqual(T value1, T value2) {
    return Comparing.equal(value1, value2);
  }

  public T get(AbstractPropertyContainer container) { return (T) container.getValueOf(this); }
  public void set(AbstractPropertyContainer container, T value) { container.setValueOf(this, value); }
  public final T cast(Object value) { return (T) value; }

  public String toString() {
    return getName();
  }

  public static abstract class AbstractPropertyContainer<PropertyImpl extends AbstractProperty> {
    public static final AbstractPropertyContainer EMPTY = new AbstractPropertyContainer() {
      public Object getValueOf(AbstractProperty property) {
        return property.getDefault(this);
      }

      public void setValueOf(AbstractProperty property, Object value) {
        throw new UnsupportedOperationException("Property: " + property.getName() + " value: " + value);
      }

      public boolean hasProperty(AbstractProperty property) {
        return false;
      }
    };

    protected abstract Object getValueOf(PropertyImpl property);
    protected abstract void setValueOf(PropertyImpl property, Object value);
    public abstract boolean hasProperty(AbstractProperty property);

    /**
     * Only containers can delegate to another.
     * Other clients should use {@link AbstractProperty#set AbstractProperty.set}
     */
    protected final <T> void delegateSet(AbstractPropertyContainer container, AbstractProperty<T> property, T value) {
      container.setValueOf(property, value);
    }

    /**
     * Only containers can delegate to another.
     * Other clients should use {@link AbstractProperty#get AbstractProperty.get}  
     */
    protected final <T> T delegateGet(AbstractPropertyContainer container, AbstractProperty<T> property) {
      return (T)container.getValueOf(property);
    }

    public final void copyFrom(AbstractPropertyContainer source, AbstractProperty[] properties) {
      for (int i = 0; i < properties.length; i++) {
        AbstractProperty property = properties[i];
        setValueOf((PropertyImpl)property, source.getValueOf(property));
      }
    }

    public final boolean areValueEqual(AbstractPropertyContainer other, AbstractProperty[] properties) {
      for (int i = 0; i < properties.length; i++) {
        AbstractProperty property = properties[i];
        if (!property.areEqual(getValueOf((PropertyImpl)property), other.getValueOf(property))) return false;
      }
      return true;
    }
  }
}
