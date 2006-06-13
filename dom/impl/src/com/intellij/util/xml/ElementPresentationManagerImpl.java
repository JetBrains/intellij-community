/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;
import java.util.ArrayList;

import com.intellij.util.Function;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.codeInsight.lookup.LookupValueFactory;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class ElementPresentationManagerImpl extends ElementPresentationManager implements ApplicationComponent {

  @NotNull
  public  <T extends DomElement> Object[] createVariants(Collection<T> elements, Function<T, String> namer) {
    ArrayList<Object> result = new ArrayList<Object>(elements.size());
    for (T element: elements) {
      String name = namer.fun(element);
      if (name != null) {
        Icon icon = getIcon(element);
        Object value = LookupValueFactory.createLookupValue(name, icon);
        result.add(value);
      }
    }
    return result.toArray();
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "ElementPresentationManager";
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }
}
