/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.extensions.impl;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import org.jdom.Element;

/**
 * @author Alexander Kireyev
 */
public class ElementConverter implements Converter {
  public boolean canConvert(Class aClass) {
    return Element.class.isAssignableFrom(aClass);
  }

  public void marshal(Object object, HierarchicalStreamWriter hierarchicalStreamWriter, MarshallingContext marshallingContext) {
    throw new UnsupportedOperationException("This method is not yet implemented");
  }

  public Object unmarshal(HierarchicalStreamReader hierarchicalStreamReader, UnmarshallingContext unmarshallingContext) {
    return hierarchicalStreamReader.peekUnderlyingNode();
  }
}
