/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.module.Module;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.AbstractConvertContext;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class ConvertContextImpl extends AbstractConvertContext {
  private final DomInvocationHandler myHandler;

  public ConvertContextImpl(final DomInvocationHandler handler) {
    myHandler = handler;
  }

  @NotNull
  public final DomElement getInvocationElement() {
    return myHandler.getProxy();
  }

  public Module getModule() {
    return myHandler.getModule();
  }

  public PsiManager getPsiManager() {
    return myHandler.getFile().getManager();
  }
}
