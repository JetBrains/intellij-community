/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.Converter;

/**
 * @author peter
 */
public abstract class SetInvocation implements Invocation {
  private final Converter myConverter;

  protected SetInvocation(final Converter converter) {
    myConverter = converter;
  }

  public Object invoke(final DomInvocationHandler handler, final Object[] args) throws Throwable {
    assert handler.isValid();
    if (handler.isIndicator()) {
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
        setValue(handler, value);
      }
    }
    return null;
  }

  protected abstract void setValue(DomInvocationHandler handler, String value) throws IncorrectOperationException;

}
