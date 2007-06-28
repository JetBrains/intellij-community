/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.util.xml.JavaMethodSignature;
import com.intellij.util.xml.XmlName;

/**
 * @author peter
 */
public class GetFixedChildInvocation implements Invocation {
  private JavaMethodSignature myMethodSignature;

  public GetFixedChildInvocation(final JavaMethodSignature method) {
    myMethodSignature = method;
  }

  public Object invoke(final DomInvocationHandler handler, final Object[] args) throws Throwable {
    final Pair<XmlName, Integer> info = handler.getGenericInfo().getFixedChildInfo(myMethodSignature);
    final EvaluatedXmlName evaluatedXmlName = handler.createEvaluatedXmlName(info.getFirst());
    handler.checkInitialized(evaluatedXmlName);
    return handler.getFixedChild(Pair.create(evaluatedXmlName, info.second)).getProxy();
  }
}
