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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.*;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.*;

public class ExternalizablePropertyContainer
    extends AbstractProperty.AbstractPropertyContainer
    implements JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.config.ExternalizablePropertyContainer");
  private final Map<AbstractProperty, Object> myValues = new HashMap<AbstractProperty, Object>();
  private final Map<AbstractProperty, Externalizer> myExternalizers = new HashMap<AbstractProperty, Externalizer>();

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

  private <T> Externalizer<List<T>> createListExternalizer(final Externalizer<T> itemExternalizer, final String itemTagName) {
    return new ListExternalizer<T>(itemExternalizer, itemTagName);
  }

  public void readExternal(Element element) throws InvalidDataException {
    HashMap<String, AbstractProperty> propertyByName = new HashMap<String, AbstractProperty>();
    for (AbstractProperty abstractProperty : myExternalizers.keySet()) {
      propertyByName.put(abstractProperty.getName(), abstractProperty);
    }
    final List<Element> children = element.getChildren();
    for (Element child : children) {
      AbstractProperty property = propertyByName.get(child.getName());
      if (property == null) {
        continue;
      }
      final Externalizer externalizer = myExternalizers.get(property);
      if (externalizer == null) {
        continue;
      }
      try {
        myValues.put(property, externalizer.readValue(child));
      }
      catch (InvalidDataException e) {
        LOG.info(e);
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    List<AbstractProperty> properties = new ArrayList<AbstractProperty>(myExternalizers.keySet());
    Collections.sort(properties, AbstractProperty.NAME_COMPARATOR);
    for (AbstractProperty property : properties) {
      final Externalizer externalizer = myExternalizers.get(property);
      if (externalizer == null) {
        continue;
      }
      final Object propValue = property.get(this);
      if (!Comparing.equal(propValue, property.getDefault(this))) {
        final Element child = new Element(property.getName());
        externalizer.writeValue(child, propValue);
        element.addContent(child);
      }
    }
  }

  protected Object getValueOf(AbstractProperty property) {
    Object value = myValues.get(property);
    return value != null ? value : property.getDefault(this);
  }

  protected void setValueOf(AbstractProperty externalizableProperty, Object value) {
    myValues.put(externalizableProperty, value);
  }

  public boolean hasProperty(AbstractProperty property) {
    return myExternalizers.containsKey(property);
  }

  private class ListExternalizer<T> implements Externalizer<List<T>> {
    @NonNls private static final String NULL_ELEMENT = "NULL_VALUE_ELEMENT";
    private final Externalizer<T> myItemExternalizer;
    private final String myItemTagName;

    public ListExternalizer(Externalizer<T> itemExternalizer, String itemTagName) {
      myItemExternalizer = itemExternalizer;
      myItemTagName = itemTagName;
    }

    public List<T> readValue(Element dataElement) throws InvalidDataException {
      ArrayList<T> list = new ArrayList<T>();
      List<Element> children = dataElement.getChildren();
      for (Iterator<Element> iterator = children.iterator(); iterator.hasNext();) {
        Element element = iterator.next();
        if (NULL_ELEMENT.equals(element.getName())) list.add(null);
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

    public void writeValue(Element dataElement, List<T> value) throws WriteExternalException {
      for (Iterator<T> iterator = value.iterator(); iterator.hasNext();) {
        T item = iterator.next();
        if (item != null) {
          Element element = new Element(myItemTagName);
          myItemExternalizer.writeValue(element, item);
          dataElement.addContent(element);
        }
        else dataElement.addContent(new Element(NULL_ELEMENT));
      }
    }
  }

}
