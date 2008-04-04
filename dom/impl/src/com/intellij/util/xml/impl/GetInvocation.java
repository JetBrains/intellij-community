/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.SubTag;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author peter
 */
public class GetInvocation implements Invocation {
  private static final Key<CachedValue<CopyOnWriteArrayList<Pair<Converter,Object>>>> DOM_VALUE_KEY = Key.create("Dom element value key");
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

    CachedValue<CopyOnWriteArrayList<Pair<Converter,Object>>> value = handler.getUserData(DOM_VALUE_KEY);
    if (value == null) {
      final DomManagerImpl domManager = handler.getManager();
      final Project project = domManager.getProject();
      final CachedValuesManager cachedValuesManager = PsiManager.getInstance(project).getCachedValuesManager();
      handler.putUserData(DOM_VALUE_KEY, value = cachedValuesManager.createCachedValue(new CachedValueProvider<CopyOnWriteArrayList<Pair<Converter,Object>>>() {
        public Result<CopyOnWriteArrayList<Pair<Converter,Object>>> compute() {
          return Result.create(new CopyOnWriteArrayList<Pair<Converter, Object>>(),
                               PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT, domManager, ProjectRootManager.getInstance(
            project));
        }
      }, false));
    }

    return getOrCalcValue(handler, value.getValue());
  }

  @Nullable
  private Object getOrCalcValue(final DomInvocationHandler<?> handler, final CopyOnWriteArrayList<Pair<Converter, Object>> list) {
    if (!list.isEmpty()) {
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0; i < list.size(); i++) {
        Pair<Converter, Object> pair = list.get(i);
        if (pair.first == myConverter) return pair.second;
      }
    }
    final Object returnValue = getValueInner(handler, myConverter);
    list.add(Pair.create(myConverter, returnValue));
    return returnValue;
  }

  @Nullable
  private static Object getValueInner(final DomInvocationHandler<?> handler, Converter converter) {
    final SubTag annotation = handler.getAnnotation(SubTag.class);
    if (annotation != null && annotation.indicator()) {
      final boolean tagNotNull = handler.getXmlTag() != null;
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
