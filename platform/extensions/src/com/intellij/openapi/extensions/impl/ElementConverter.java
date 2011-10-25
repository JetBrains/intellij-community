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
package com.intellij.openapi.extensions.impl;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import org.jdom.Element;

/**
 * @author Alexander Kireyev
 */
class ElementConverter implements Converter {
  @Override
  public boolean canConvert(Class aClass) {
    return Element.class.isAssignableFrom(aClass);
  }

  @Override
  public void marshal(Object object, HierarchicalStreamWriter hierarchicalStreamWriter, MarshallingContext marshallingContext) {
    throw new UnsupportedOperationException("This method is not yet implemented");
  }

  @Override
  public Object unmarshal(HierarchicalStreamReader hierarchicalStreamReader, UnmarshallingContext unmarshallingContext) {
    //return hierarchicalStreamReader.peekUnderlyingNode();
    throw new UnsupportedOperationException("This method is not yet implemented");
  }
}
