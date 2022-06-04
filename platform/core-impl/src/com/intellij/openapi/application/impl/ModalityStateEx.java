// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.WeakList;
import kotlinx.coroutines.Job;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.CancellationException;

public final class ModalityStateEx extends ModalityState {
  private final WeakList<Object> myModalEntities = new WeakList<>();
  private static final Set<Object> ourTransparentEntities = Collections.newSetFromMap(CollectionFactory.createConcurrentWeakMap());

  @SuppressWarnings("unused")
  public ModalityStateEx() { } // used by reflection to initialize NON_MODAL

  ModalityStateEx(@NotNull Collection<Object> modalEntities) {
    myModalEntities.addAll(modalEntities);
  }

  @NotNull
  private List<@NotNull Object> getModalEntities() {
    return myModalEntities.toStrongList();
  }

  @NotNull
  public ModalityState appendProgress(@NotNull ProgressIndicator progress){
    return appendEntity(progress);
  }

  public @NotNull ModalityState appendJob(@NotNull Job job) {
    return appendEntity(job);
  }

  @NotNull ModalityStateEx appendEntity(@NotNull Object anEntity){
    List<@NotNull Object> modalEntities = getModalEntities();
    List<Object> list = new ArrayList<>(modalEntities.size() + 1);
    list.addAll(modalEntities);
    list.add(anEntity);
    return new ModalityStateEx(list);
  }

  void forceModalEntities(@NotNull ModalityStateEx other) {
    List<@NotNull Object> otherEntities = other.getModalEntities();
    myModalEntities.clear();
    myModalEntities.addAll(otherEntities);
  }

  @Override
  public boolean dominates(@NotNull ModalityState anotherState){
    if (anotherState == this || anotherState == ModalityState.any()) return false;
    if (myModalEntities.isEmpty()) return false;
    
    List<@NotNull Object> otherEntities = ((ModalityStateEx)anotherState).getModalEntities();
    for (Object entity : getModalEntities()) {
      if (!otherEntities.contains(entity) && !ourTransparentEntities.contains(entity)) return true; // I have entity which is absent in anotherState
    }
    return false;
  }

  void cancelAllEntities() {
    for (Object entity : myModalEntities) {
      // DialogWrapperDialog is not accessible here
      if (entity instanceof Dialog) {
        ((Dialog)entity).setVisible(false);
        if (entity instanceof Disposable) {
          Disposer.dispose((Disposable)entity);
        }
      }
      else if (entity instanceof ProgressIndicator) {
        ((ProgressIndicator)entity).cancel();
      }
      else if (entity instanceof Job) {
        ((Job)entity).cancel(new CancellationException("force leave modal"));
      }
    }
  }

  @NonNls
  public String toString() {
    return this == NON_MODAL
           ? "ModalityState.NON_MODAL"
           : "ModalityState:{" + StringUtil.join(getModalEntities(), it -> "[" + it + "]", ", ") + "}";
  }

  void removeModality(Object modalEntity) {
    myModalEntities.remove(modalEntity);
  }

  void markTransparent() {
    ContainerUtil.addIfNotNull(ourTransparentEntities, ContainerUtil.getLastItem(getModalEntities()));
  }

  static void unmarkTransparent(@NotNull Object modalEntity) {
    ourTransparentEntities.remove(modalEntity);
  }

  boolean contains(@NotNull Object modalEntity) {
    return getModalEntities().contains(modalEntity);
  }
}