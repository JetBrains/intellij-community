/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.SubTag;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class GetInvocation implements Invocation {
  private static final Key<FactoryMap<Converter,CachedValue>> DOM_VALUE_KEY = Key.create("Dom element value key");
  private final Converter myConverter;

  protected GetInvocation(final Converter converter) {
    assert converter != null;
    myConverter = converter;
  }

  public Object invoke(final DomInvocationHandler<?> handler, final Object[] args) throws Throwable {
    handler.checkIsValid();
    FactoryMap<Converter,CachedValue> map = handler.getUserData(DOM_VALUE_KEY);
    if (map == null) {
      final DomManagerImpl domManager = handler.getManager();
      final CachedValuesManager cachedValuesManager = PsiManager.getInstance(domManager.getProject()).getCachedValuesManager();
      handler.putUserData(DOM_VALUE_KEY, map = new FactoryMap<Converter, CachedValue>() {
        protected CachedValue create(final Converter key) {
          return cachedValuesManager.createCachedValue(new CachedValueProvider<Object>() {
            public Result<Object> compute() {
              return Result.create(getValueInner(handler, key), PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT, domManager);
            }
          }, false);
        }
      });
    }
    return map.get(myConverter).getValue();
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
