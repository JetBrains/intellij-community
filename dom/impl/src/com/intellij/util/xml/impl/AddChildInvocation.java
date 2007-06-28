/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.util.Function;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.XmlName;

import java.lang.reflect.Type;

/**
 * @author peter
 */
public class AddChildInvocation implements Invocation{
  private final XmlName myTagName;
  private final Type myType;
  private final Function<Object[],Integer> myIndexGetter;
  private final Function<Object[], Type> myClassGetter;

  public AddChildInvocation(final Function<Object[], Type> classGetter,
                            final Function<Object[], Integer> indexGetter,
                            final XmlName tagName,
                            final Type type) {
    myClassGetter = classGetter;
    myIndexGetter = indexGetter;
    myTagName = tagName;
    myType = type;
  }

  public Object invoke(final DomInvocationHandler handler, final Object[] args) throws Throwable {
    final Type type = myClassGetter.fun(args);
    final DomElement domElement = handler.addChild(handler.createEvaluatedXmlName(myTagName), type, myIndexGetter.fun(args));
    final DomManagerImpl manager = handler.getManager();
    final boolean b = manager.setChanging(true);
    try {
      manager.getTypeChooserManager().getTypeChooser(myType).distinguishTag(domElement.getXmlTag(), ReflectionUtil.getRawType(type));
    }
    finally {
      handler.getManager().setChanging(b);
    }
    return domElement;
  }
}
