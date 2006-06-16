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
 */
package com.intellij.util.xml;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.Function;
import com.intellij.util.ReflectionCache;
import com.intellij.util.containers.ContainerUtil;
import net.sf.cglib.proxy.Factory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.Method;
import java.util.*;

/**
 * @author peter
 */
public abstract class ElementPresentationManager {

  private final static Function<? extends DomElement, String> DEFAULT_NAMER = new Function<DomElement, String>() {
    public String fun(final DomElement element) {
      return element.getGenericInfo().getElementName(element);
    }
  };

  public static ElementPresentationManager getInstance() {
    return ApplicationManager.getApplication().getComponent(ElementPresentationManager.class);
  }

  @NotNull
  public <T extends DomElement> Object[] createVariants(Collection<T> elements) {
    return createVariants(elements, (Function<T, String>)DEFAULT_NAMER);
  }

  @NotNull
  public abstract  <T extends DomElement> Object[] createVariants(Collection<T> elements, Function<T, String> namer);


  private static final Map<Class, String> ourTypeNames = new HashMap<Class, String>();
  private static final Map<Class, Icon[]> ourIcons = new HashMap<Class, Icon[]>();

  private static final List<Function<Object, String>> ourNameProviders = new ArrayList<Function<Object, String>>();
  private static final List<Function<Class, String>> ourTypeProviders = new ArrayList<Function<Class, String>>();
  private static final List<Function<Object, Icon>> ourIconProviders = new ArrayList<Function<Object, Icon>>();

  public static void registerNameProvider(Function<Object, String> function) { ourNameProviders.add(function); }
  public static void registerTypeProvider(Function<Class, String> function) { ourTypeProviders.add(function); }
  public static void registerIconProvider(Function<Object, Icon> function) { ourIconProviders.add(function); }

  public static void unregisterNameProvider(Function<Object, String> function) { ourNameProviders.remove(function); }
  public static void unregisterTypeProvider(Function<Class, String> function) { ourTypeProviders.remove(function); }
  public static void unregisterIconProvider(Function<Object, Icon> function) { ourIconProviders.remove(function); }

  public static void registerTypeName(Class aClass, String typeName) { ourTypeNames.put(aClass, typeName); }
  public static void registerIcon(Class aClass, Icon icon) { registerIcons(aClass, icon); }
  public static void registerIcons(Class aClass, Icon... icon) { ourIcons.put(aClass, icon); }

  public static String getElementName(Object element) {
    for (final Function<Object, String> function : ourNameProviders) {
      final String s = function.fun(element);
      if (s != null) {
        return s;
      }
    }
    Object o = invokeNameValueMethod(element);
    return o == null || o instanceof String ? (String)o : ((GenericValue)o).getStringValue();
  }

  public static Object invokeNameValueMethod(final Object element) {
    final Method nameValueMethod = findNameValueMethod(element.getClass());
    if (nameValueMethod == null) {
      return null;
    }

    return DomReflectionUtil.invokeMethod(nameValueMethod, element);
  }

  public static String getTypeNameForObject(Object o) {
    final Class<? extends Object> aClass = o.getClass();
    String s = _getTypeName(aClass);
    if (s != null) {
      return s;
    }

    if (o instanceof DomElement) {
      final DomElement element = (DomElement)o;
      return StringUtil.capitalizeWords(element.getNameStrategy().splitIntoWords(element.getXmlElementName()), true);
    }
    return getDefaultTypeName(aClass);
  }

  public static String getTypeName(Class aClass) {
    String s = _getTypeName(aClass);
    if (s != null) return s;
    return getDefaultTypeName(aClass);
  }

  private static String getDefaultTypeName(final Class aClass) {
    String simpleName = aClass.getSimpleName();
    final int i = simpleName.indexOf('$');
    if (i >= 0) {
      if (Factory.class.isAssignableFrom(aClass)) {
        simpleName = simpleName.substring(0, i);
      } else {
        simpleName = simpleName.substring(i + 1);
      }
    }
    return StringUtil.capitalizeWords(StringUtil.join(NameUtil.nameToWords(simpleName),  " "), true);
  }

  private static <T> T getFromClassMap(Map<Class,T> map, Class value) {
    for (final Map.Entry<Class, T> entry : map.entrySet()) {
      if (entry.getKey().isAssignableFrom(value)) {
        return entry.getValue();
      }
    }
    return null;
  }

  private static String _getTypeName(final Class aClass) {
    for (final Function<Class, String> function : ourTypeProviders) {
      final String s = function.fun(aClass);
      if (s != null) {
        return s;
      }
    }
    return getFromClassMap(ourTypeNames, aClass);
  }

  public static Icon getIcon(Object o) {
    return getFirst(getIcons(o));
  }

  @Nullable
  private static <T> T getFirst(@Nullable final T[] array) {
    return array == null || array.length == 0 ? null : array[0];
  }

  @Nullable
  public static Icon getLargeIcon(@Nullable Icon[] icons) {
    if (icons == null || icons.length == 0) return null;
    Icon largest = icons[0];
    for (int i = 1; i < icons.length; i++) {
      Icon icon = icons[i];
      if (icon.getIconWidth() > largest.getIconWidth()) {
        largest = icon;
      }
    }
    return largest;
  }


  @Nullable
  public static Icon getSmallIcon(@Nullable Icon[] icons) {
    if (icons == null || icons.length == 0) return null;
    Icon smallest = icons[0];
    for (int i = 1; i < icons.length; i++) {
      Icon icon = icons[i];
      if (icon.getIconWidth() < smallest.getIconWidth()) {
        smallest = icon;
      }
    }
    return smallest;
  }

  @NotNull
  public static Icon[] getIcons(Object o) {
    List<Icon> result = new ArrayList<Icon>();
    for (final Function<Object, Icon> function : ourIconProviders) {
      final Icon icon = function.fun(o);
      if (icon != null) {
        result.add(icon);
      }
    }
    final Icon[] icons = getFromClassMap(ourIcons, o.getClass());
    if (icons != null) {
      result.addAll(Arrays.asList(icons));
    }
    return result.toArray(new Icon[result.size()]);
  }

  public static Icon getIconForClass(Class clazz) {
    return getFirst(getIconsForClass(clazz));
  }

  @Nullable
  public static Icon[] getIconsForClass(final Class clazz) {
    return ourIcons.get(clazz);
  }

  public static Method findNameValueMethod(final Class<? extends Object> aClass) {
    for (final Method method : ReflectionCache.getMethods(aClass)) {
      if (DomReflectionUtil.findAnnotationDFS(method, NameValue.class) != null) {
        return method;
      }
    }
    return null;
  }

  public static <T> T findByName(Collection<T> collection, final String name) {
    return ContainerUtil.find(collection, new Condition<T>() {
      public boolean value(final T object) {
        return Comparing.equal(name, getElementName(object));
      }
    });
  }

}
