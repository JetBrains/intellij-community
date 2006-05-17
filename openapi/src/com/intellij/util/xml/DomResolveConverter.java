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

import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author peter
 */
public class DomResolveConverter<T extends DomElement> implements ResolvingConverter<T>{
  private static final FactoryMap<Class<? extends DomElement>,DomResolveConverter> ourCache = new FactoryMap<Class<? extends DomElement>, DomResolveConverter>() {
    @NotNull
    protected DomResolveConverter create(final Class<? extends DomElement> key) {
      return new DomResolveConverter(key);
    }
  };
  private final Class<T> myClass;

  public DomResolveConverter(final Class<T> aClass) {
    myClass = aClass;
  }

  public static <T extends DomElement> DomResolveConverter<T> createConverter(Class<T> aClass) {
    return ourCache.get(aClass);
  }

  public final T fromString(final String s, final ConvertContext context) {
    if (s == null) return null;
    final DomElement[] result = new DomElement[]{null};
    final DomElement scope = getResolvingScope(context);
    scope.acceptChildren(new DomElementVisitor() {
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

  private static DomElement getResolvingScope(final ConvertContext context) {
    final DomElement invocationElement = context.getInvocationElement();
    return invocationElement.getManager().getResolvingScope((GenericDomValue)invocationElement);
  }

  public final String toString(final T t, final ConvertContext context) {
    if (t == null) return null;
    return t.getGenericInfo().getElementName(t);
  }

  public Collection<T> getVariants(final ConvertContext context) {
    final List<T> result = new ArrayList<T>();
    getResolvingScope(context).acceptChildren(new DomElementVisitor() {
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
