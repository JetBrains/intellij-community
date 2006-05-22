/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.Converter;

import java.lang.reflect.Method;

/**
 * @author peter
 */
public abstract class SetInvocation implements Invocation {
  private final Converter myConverter;
  private final Method myMethod;

  protected SetInvocation(final Converter converter, final Method method) {
    myConverter = converter;
    myMethod = method;
  }

  public Object invoke(final DomInvocationHandler handler, final Object[] args) throws Throwable {
    assert handler.isValid();
    final VirtualFile virtualFile = handler.getFile().getVirtualFile();
    if (virtualFile != null && !virtualFile.isWritable()) {
      VirtualFileManager.getInstance().fireReadOnlyModificationAttempt(virtualFile);
      return null;
    }

    final DomManagerImpl manager = handler.getManager();
    final boolean changing = manager.setChanging(true);
    try {
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
    }
    finally {
      manager.setChanging(changing);
    }
    return null;
  }

  protected abstract void setValue(DomInvocationHandler handler, String value) throws IncorrectOperationException;

}
