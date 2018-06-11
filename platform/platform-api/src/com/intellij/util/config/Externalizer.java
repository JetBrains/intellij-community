// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.config;

import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.JDOMExternalizable;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.Iterator;

public interface Externalizer<T> {
  @NonNls String VALUE_ATTRIBUTE = "value";
  Externalizer<String> STRING = new BaseExternalizer<String>(){
    @Override
    public String readValue(Element dataElement) {
      return dataElement.getAttributeValue(VALUE_ATTRIBUTE);
    }
  };
  Externalizer<Integer> INTEGER = new BaseExternalizer<Integer>() {
    @Override
    public Integer readValue(Element dataElement) {
      try {
        return new Integer(dataElement.getAttributeValue(VALUE_ATTRIBUTE));
      } catch(NumberFormatException e) {
        return null;
      }
    }
  };
  Externalizer<Storage> STORAGE = new StorageExternalizer();

  abstract class BaseExternalizer<T> implements Externalizer<T> {

    @Override
    public void writeValue(Element dataElement, T value) {
      dataElement.setAttribute(VALUE_ATTRIBUTE, value.toString());
    }
  }
  Externalizer<Boolean> BOOLEAN = new BaseExternalizer<Boolean>() {
    @Override
    public Boolean readValue(Element dataElement) {
      return Boolean.valueOf(dataElement.getAttributeValue(VALUE_ATTRIBUTE));
    }
  };

  T readValue(Element dataElement);

  void writeValue(Element dataElement, T value);

  class FactoryBased<T extends JDOMExternalizable> implements Externalizer<T> {
    private final Factory<T> myFactory;

    public FactoryBased(Factory<T> factory) {
      myFactory = factory;
    }

    @Override
    public T readValue(Element dataElement) {
      T data = myFactory.create();
      data.readExternal(dataElement);
      return data;
    }

    @Override
    public void writeValue(Element dataElement, T value) {
      value.writeExternal(dataElement);
    }

    static <T extends JDOMExternalizable> FactoryBased<T> create(Factory<T> factory) {
      return new FactoryBased<>(factory);
    }
  }

  class StorageExternalizer implements Externalizer<Storage> {
    @NonNls private static final String ITEM_TAG = "item";
    @NonNls private static final String KEY_ATTR = "key";
    @NonNls private static final String VALUE_ATTR = "value";

    @Override
    public Storage readValue(Element dataElement) {
      Storage.MapStorage storage = new Storage.MapStorage();
      for (Element element : dataElement.getChildren(ITEM_TAG)) {
        storage.put(element.getAttributeValue(KEY_ATTR), element.getAttributeValue(VALUE_ATTR));
      }
      return storage;
    }

    @Override
    public void writeValue(Element dataElement, Storage storage) {
      Iterator<String> keys = ((Storage.MapStorage)storage).getKeys();
      while (keys.hasNext()) {
        String key = keys.next();
        String value = storage.get(key);
        Element element = new Element(ITEM_TAG);
        element.setAttribute(KEY_ATTR, key);
        if (value != null) {
          element.setAttribute(VALUE_ATTR, value);
        }
        dataElement.addContent(element);
      }
    }
  }
}
