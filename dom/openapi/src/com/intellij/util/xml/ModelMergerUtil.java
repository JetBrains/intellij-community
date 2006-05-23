/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.util.Processor;
import com.intellij.util.CommonProcessors;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Collections;
import java.util.ArrayList;

/**
 * @author peter
 */
public class ModelMergerUtil {
  @Nullable
  public static <T, V> V getImplementation(final T element, final Class<V> clazz) {
    if (element == null) return null;
    CommonProcessors.FindFirstProcessor<T> processor = new CommonProcessors.FindFirstProcessor<T>() {
      public boolean process(final T t) {
        return !clazz.isAssignableFrom(t.getClass()) || super.process(t);
      }
    };
    new ImplementationProcessor<T>(processor, true).process(element);
    return (V)processor.getFoundValue();
  }

  @NotNull
  public static <T> List<T> getImplementations(T element) {
    if (element instanceof MergedObject) {
      final MergedObject<T> mergedObject = (MergedObject<T>)element;
      return mergedObject.getImplementations();
    }
    else if (element != null) {
      return Collections.singletonList(element);
    }
    else {
      return Collections.emptyList();
    }
  }

  @NotNull
  public static <T> List<T> getFilteredImplementations(final T element) {
    final CommonProcessors.CollectProcessor<T> processor = new CommonProcessors.CollectProcessor<T>(new ArrayList<T>());
    new ImplementationProcessor<T>(processor, false).process(element);
    return (List<T>)processor.getResults();
  }

  public static class ImplementationProcessor<T> implements Processor<T> {
    private final Processor<T> myProcessor;
    private final boolean myProcessMerged;

    public ImplementationProcessor(Processor<T> processor, final boolean processMerged) {
      myProcessor = processor;
      myProcessMerged = processMerged;
    }

    public boolean process(final T t) {
      final boolean merged = t instanceof MergedObject;
      if ((!merged || myProcessMerged) && !myProcessor.process(t)) {
        return false;
      }
      if (merged && !ContainerUtil.process(((MergedObject<T>)t).getImplementations(), this)) {
        return false;
      }
      return true;
    }
  }
}
