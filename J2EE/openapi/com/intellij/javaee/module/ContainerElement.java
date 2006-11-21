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
package com.intellij.javaee.module;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

/**
 * @deprecated use {@link com.intellij.openapi.module.ContainerElement}
 */
public abstract class ContainerElement implements JDOMExternalizable, Cloneable, ResolvableElement {
  private final com.intellij.openapi.module.ContainerElement myDelegate;

  protected ContainerElement(final com.intellij.openapi.module.ContainerElement delegate) {
    myDelegate = delegate;
  }

  public com.intellij.openapi.module.ContainerElement getDelegate() {
    return myDelegate;
  }

  @SuppressWarnings({"UnusedDeclaration"})
  @Deprecated
  protected ContainerElement(Module parentModule) {
    throw new UnsupportedOperationException();
  }

  public String getPresentableName() {
    return getDelegate().getPresentableName();
  }

  public String getURI() {
    return myDelegate.getURI();
  }

  public void setURI(String uri) {
    myDelegate.setURI(uri);
  }

  public J2EEPackagingMethod getPackagingMethod() {
    return J2EEPackagingMethod.getPackagingMethod(myDelegate.getPackagingMethod());
  }

  public void setPackagingMethod(J2EEPackagingMethod method) {
    myDelegate.setPackagingMethod(method.getDelegate());
  }

  public void setAttribute(String name, String value) {
    myDelegate.setAttribute(name, value);
  }

  public String getAttribute(String name) {
    return myDelegate.getAttribute(name);
  }

  public Module getParentModule() {
    return myDelegate.getParentModule();
  }

  public void setParentModule(Module module) {
    myDelegate.setParentModule(module);
  }

  public void readExternal(Element element) throws InvalidDataException {
    myDelegate.readExternal(element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    myDelegate.writeExternal(element);
  }

  public boolean equalsIgnoreAttributes(ContainerElement otherElement) {
    if (this == otherElement) return true;
    return myDelegate.equalsIgnoreAttributes(otherElement.myDelegate);
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ContainerElement)) return false;

    final ContainerElement otherElement = (ContainerElement)o;
    return myDelegate.equals(otherElement.myDelegate);
  }

  public int hashCode() {
    return myDelegate.hashCode();
  }

  public String getDescription() {
    return myDelegate.getDescription();
  }

  public String getDescriptionForPackagingMethod(J2EEPackagingMethod method) {
    return myDelegate.getDescriptionForPackagingMethod(method.getDelegate());
  }

  public ContainerElement clone() {
    throw new UnsupportedOperationException();
  }

  public String toString() {
    return getDelegate().toString();
  }
}