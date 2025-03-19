// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.concurrency.JobSchedulerImpl;
import com.intellij.util.containers.HashingStrategy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

@ApiStatus.Internal
public final class FileTypeAssocTableUtil {
  public static @NotNull <T> FileTypeAssocTable<T> newScalableFileTypeAssocTable() {
    return new FileTypeAssocTable<>(FileTypeAssocTableUtil::createScalableCharSequenceConcurrentMap);
  }

  private static @NotNull <T> Map<CharSequence, T> createScalableCharSequenceConcurrentMap(@NotNull Map<? extends CharSequence, ? extends T> source, boolean caseSensitive) {
    HashingStrategy<CharSequence> hashingStrategy = caseSensitive ? HashingStrategy.caseSensitiveCharSequence() : HashingStrategy.caseInsensitiveCharSequence();
    Map<CharSequence, T> map = ConcurrentCollectionFactory.createConcurrentMap(source.size(),
                                                                               0.5f,
                                                                               JobSchedulerImpl.getCPUCoresCount(),
                                                                               hashingStrategy);
    map.putAll(source);
    return map;
  }
}
