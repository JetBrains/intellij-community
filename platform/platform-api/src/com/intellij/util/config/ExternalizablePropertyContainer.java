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
package com.intellij.util.config;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.SmartList;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ExternalizablePropertyContainer extends AbstractProperty.AbstractPropertyContainer {
  private static final Logger LOG = Logger.getInstance(ExternalizablePropertyContainer.class);
  private final Map<AbstractProperty, Object> myValues = new THashMap<>();
  private final Map<AbstractProperty, Externalizer> myExternalizers = new THashMap<>();

  public <T> void registerProperty(AbstractProperty<T> property, Externalizer<T> externalizer) {
    String name = property.getName();
    LOG.assertTrue(!myExternalizers.containsKey(property), name);
    myExternalizers.put(property, externalizer);
  }

  public void rememberKey(AbstractProperty property) {
    LOG.assertTrue(myExternalizers.get(property) == null, property.getName());
    myExternalizers.put(property, null);
  }

  public void registerProperty(BooleanProperty property) {
    registerProperty(property, Externalizer.BOOLEAN);
  }

  public void registerProperty(StringProperty property) {
    registerProperty(property, Externalizer.STRING);
  }

  public void registerProperty(IntProperty property) {
    registerProperty(property, Externalizer.INTEGER);
  }

  public void registerProperty(StorageProperty property) {
    registerProperty(property, Externalizer.STORAGE);
  }

  public <T> void  registerProperty(ListProperty<T> property,@NonNls String itemTagName, Externalizer<T> itemExternalizer) {
    registerProperty(property, createListExternalizer(itemExternalizer, itemTagName));
  }

  /**
   * @deprecated
   */
  public <T extends JDOMExternalizable> void  registerProperty(ListProperty<T> property, @NonNls String itemTagName, Factory<T> factory) {
    registerProperty(property, itemTagName, Externalizer.FactoryBased.create(factory));
  }

  private static <T> Externalizer<List<T>> createListExternalizer(final Externalizer<T> itemExternalizer, final String itemTagName) {
    return new ListExternalizer(itemExternalizer, itemTagName);
  }

  public void readExternal(@NotNull Element element) {
    Map<String, AbstractProperty> propertyByName = new THashMap<>();
    for (AbstractProperty abstractProperty : myExternalizers.keySet()) {
      propertyByName.put(abstractProperty.getName(), abstractProperty);
    }
    for (Element child : element.getChildren()) {
      AbstractProperty property = propertyByName.get(child.getName());
      if (property == null) {
        continue;
      }
      Externalizer externalizer = myExternalizers.get(property);
      if (externalizer == null) {
        continue;
      }
      try {
        myValues.put(property, externalizer.readValue(child));
      }
      catch (Exception e) {
        LOG.info(e);
      }
    }
  }

  public void writeExternal(@NotNull Element element) {
    if (myExternalizers.isEmpty()) {
      return;
    }

    List<AbstractProperty> properties = new ArrayList<>(myExternalizers.keySet());
    Collections.sort(properties, AbstractProperty.NAME_COMPARATOR);
    for (AbstractProperty property : properties) {
      Externalizer externalizer = myExternalizers.get(property);
      if (externalizer == null) {
        continue;
      }

      Object propValue = property.get(this);
      if (!Comparing.equal(propValue, property.getDefault(this))) {
        Element child = new Element(property.getName());
        externalizer.writeValue(child, propValue);
        if (!JDOMUtil.isEmpty(child)) {
          element.addContent(child);
        }
      }
    }
  }

  @Override
  protected Object getValueOf(AbstractProperty property) {
    Object value = myValues.get(property);
    return value != null ? value : property.getDefault(this);
  }

  @Override
  protected void setValueOf(AbstractProperty externalizableProperty, Object value) {
    myValues.put(externalizableProperty, value);
  }

  @Override
  public boolean hasProperty(AbstractProperty property) {
    return myExternalizers.containsKey(property);
  }

  private static class ListExternalizer<T> implements Externalizer<List<T>> {
    @NonNls private static final String NULL_ELEMENT = "NULL_VALUE_ELEMENT";
    private final Externalizer<T> myItemExternalizer;
    private final String myItemTagName;

    public ListExternalizer(Externalizer<T> itemExternalizer, String itemTagName) {
      myItemExternalizer = itemExternalizer;
      myItemTagName = itemTagName;
    }

    @Override
    public List<T> readValue(Element dataElement) {
      List<T> list = new SmartList<>();
      for (Element element : dataElement.getChildren()) {
        if (NULL_ELEMENT.equals(element.getName())) {
          list.add(null);
        }
        else if (myItemTagName.equals(element.getName())) {
          T item = myItemExternalizer.readValue(element);
          if (item == null) {
            LOG.error("Can't create element " + myItemExternalizer);
            return list;
          }
          list.add(item);
        }
      }
      return list;
    }

    @Override
    public void writeValue(Element dataElement, List<T> value) {
      for (T item : value) {
        if (item == null) {
          dataElement.addContent(new Element(NULL_ELEMENT));
        }
        else {
          Element element = new Element(myItemTagName);
          myItemExternalizer.writeValue(element, item);
          dataElement.addContent(element);
        }
      }
    }
  }
}
