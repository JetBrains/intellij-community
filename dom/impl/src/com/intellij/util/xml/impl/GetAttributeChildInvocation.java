/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

/**
 * @author peter
 */
public class GetAttributeChildInvocation implements Invocation {
  private AttributeChildDescriptionImpl myDescription;

  public GetAttributeChildInvocation(final AttributeChildDescriptionImpl description) {
    myDescription = description;
  }

  public Object invoke(final DomInvocationHandler handler, final Object[] args) throws Throwable {
    return handler.getAttributeChild(myDescription).getProxy();
  }
}
