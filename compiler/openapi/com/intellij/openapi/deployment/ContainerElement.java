/**
 * @author cdr
 */
/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.deployment;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class ContainerElement implements JDOMExternalizable, Cloneable, ResolvableElement {
  private final Map<String,String> myAttributes = new LinkedHashMap<String, String>();
  private Module myParentModule;
  @NonNls public static final String URI_ATTR = "URI";
  @NonNls public static final String PACKAGING_METHOD_ATTR = "method";
  @NonNls public static final String ELEMENT_ATTRIBUTE = "attribute";
  @NonNls public static final String ATTRIBUTE_NAME = "name";
  @NonNls public static final String ATTRIBUTE_VALUE = "value";

  protected ContainerElement(@Nullable Module parentModule) {
    myParentModule = parentModule;
  }

  public abstract String getPresentableName();

  public String getURI() {
    return getAttribute(URI_ATTR);
  }

  public void setURI(String uri) {
    setAttribute(URI_ATTR, uri);
  }
  public PackagingMethod getPackagingMethod() {
    final String attribute = getAttribute(PACKAGING_METHOD_ATTR);
    return attribute == null ? PackagingMethod.DO_NOT_PACKAGE : PackagingMethod.getDeploymentMethodById(attribute);
  }
  public void setPackagingMethod(PackagingMethod method) {
    setAttribute(PACKAGING_METHOD_ATTR, method.getId());
  }

  public void setAttribute(String name, String value) {
    myAttributes.put(name, value);
  }
  public String getAttribute(String name) {
    return myAttributes.get(name);
  }

  @Nullable
  public Module getParentModule() {
    return myParentModule;
  }
  public void setParentModule(Module module) {
    myParentModule = module;
  }

  public void readExternal(Element element) throws InvalidDataException {
    final List attrs = element.getChildren(ELEMENT_ATTRIBUTE);
    for (int i = 0; i < attrs.size(); i++) {
      Element attribute = (Element)attrs.get(i);
      final String name = attribute.getAttributeValue(ATTRIBUTE_NAME);
      final String value = attribute.getAttributeValue(ATTRIBUTE_VALUE);
      setAttribute(name, value);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (Iterator iterator = myAttributes.keySet().iterator(); iterator.hasNext();) {
      String name = (String)iterator.next();
      String value = getAttribute(name);
      final Element attr = new Element(ELEMENT_ATTRIBUTE);
      attr.setAttribute(ATTRIBUTE_NAME, name);
      attr.setAttribute(ATTRIBUTE_VALUE, value==null?"":value);
      element.addContent(attr);
    }
  }

  public abstract boolean equalsIgnoreAttributes(ContainerElement otherElement);
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ContainerElement)) return false;

    final ContainerElement otherElement = (ContainerElement)o;
    if (!equalsIgnoreAttributes(otherElement)) return false;
    return myAttributes.equals(otherElement.myAttributes);
  }

  public int hashCode() {
    return 0;
  }

  public abstract String getDescription();

  public abstract String getDescriptionForPackagingMethod(PackagingMethod method);

  public ContainerElement clone() {
    throw new UnsupportedOperationException();
  }

}
