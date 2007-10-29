/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.SubTag;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class GetInvocation implements Invocation {
  private static final Key<CachedValue<ConcurrentFactoryMap<Converter,Object>>> DOM_VALUE_KEY = Key.create("Dom element value key");
  private final Converter myConverter;

  protected GetInvocation(final Converter converter) {
    assert converter != null;
    myConverter = converter;
  }

  public Object invoke(final DomInvocationHandler<?> handler, final Object[] args) throws Throwable {
    handler.checkIsValid();
    if (myConverter == Converter.EMPTY_CONVERTER) {
      return getValueInner(handler, myConverter);
    }

    CachedValue<ConcurrentFactoryMap<Converter, Object>> value;
    synchronized (handler) {
      value = handler.getUserData(DOM_VALUE_KEY);
      if (value == null) {
        final DomManagerImpl domManager = handler.getManager();
        final CachedValuesManager cachedValuesManager = PsiManager.getInstance(domManager.getProject()).getCachedValuesManager();
        handler.putUserData(DOM_VALUE_KEY, value = cachedValuesManager.createCachedValue(new CachedValueProvider<ConcurrentFactoryMap<Converter, Object>>() {
          public Result<ConcurrentFactoryMap<Converter, Object>> compute() {
            final ConcurrentFactoryMap<Converter, Object> map = new ConcurrentFactoryMap<Converter, Object>() {
              protected Object create(final Converter key) {
                return getValueInner(handler, key);
              }
            };
            return Result.create(map, PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT, domManager, ProjectRootManager.getInstance(domManager.getProject()));
          }
        }, false));
      }
    }

    return value.getValue().get(myConverter);
  }

  @Nullable
  private static Object getValueInner(final DomInvocationHandler<?> handler, Converter converter) {
    final XmlTag tag = handler.getXmlTag();
    final boolean tagNotNull = tag != null;
    final SubTag annotation = handler.getAnnotation(SubTag.class);
    if (annotation != null && annotation.indicator()) {
      if (converter == Converter.EMPTY_CONVERTER) {
        return tagNotNull ? "" : null;
      }
      else {
        return tagNotNull;
      }
    }

    final String tagValue = handler.getValue();
    return converter.fromString(tagValue, new ConvertContextImpl(handler));
  }

}
