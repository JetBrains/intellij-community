/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.util.xml.JavaMethodSignature;

/**
 * @author peter
 */
public class GetAttributeChildInvocation implements Invocation {
  private JavaMethodSignature myMethodSignature;

  public GetAttributeChildInvocation(final JavaMethodSignature method) {
    myMethodSignature = method;
  }

  public Object invoke(final DomInvocationHandler handler, final Object[] args) throws Throwable {
    return handler.getAttributeChild(myMethodSignature).getProxy();
  }
}
