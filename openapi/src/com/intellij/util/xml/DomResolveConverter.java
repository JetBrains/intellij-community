/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
 *
 */
package com.intellij.util.xml;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;

/**
 * @author peter
 */
public class DomResolveConverter<T extends DomElement> implements ResolvingConverter<T>{
  private final Class<T> myClass;

  public DomResolveConverter(final Class<T> aClass) {
    myClass = aClass;
  }

  public final T fromString(final String s, final ConvertContext context) {
    final DomElement[] result = new DomElement[]{null};
    context.getInvocationElement().getRoot().acceptChildren(new DomElementVisitor() {
      public void visitDomElement(DomElement element) {
        if (result[0] != null) return;
        if (myClass.isInstance(element) && s.equals(element.getGenericInfo().getElementName(element))) {
          result[0] = element;
        } else {
          element.acceptChildren(this);
        }
      }
    });
    return (T) result[0];
  }

  public final String toString(final T t, final ConvertContext context) {
    return t.getGenericInfo().getElementName(t);
  }

  public Collection<T> getVariants(final ConvertContext context) {
    final List<T> result = new ArrayList<T>();
    context.getInvocationElement().getRoot().acceptChildren(new DomElementVisitor() {
      public void visitDomElement(DomElement element) {
        if (myClass.isInstance(element)) {
          result.add((T)element);
        }
        element.acceptChildren(this);
      }
    });
    return result;
  }
}
