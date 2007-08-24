/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.util.xml.Converter;
import com.intellij.util.xml.SubTag;

/**
 * @author peter
 */
public class SetInvocation implements Invocation {
  private final Converter myConverter;

  protected SetInvocation(final Converter converter) {
    myConverter = converter;
  }

  public Object invoke(final DomInvocationHandler<?> handler, final Object[] args) throws Throwable {
    assert handler.isValid();
    final SubTag annotation = handler.getAnnotation(SubTag.class);
    if (annotation != null && annotation.indicator()) {
      if ((Boolean)args[0]) {
        handler.ensureTagExists();
      } else {
        handler.undefineInternal();
      }
    } else {
      String value = myConverter.toString(args[0], new ConvertContextImpl(handler));
      if (value == null) {
        handler.undefineInternal();
      } else {
        handler.setValue(value);
      }
    }
    return null;
  }

}
