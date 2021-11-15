// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.WeakList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;
import java.util.*;

public final class ModalityStateEx extends ModalityState {
  private final WeakList<Object> myModalEntities = new WeakList<>();
  private static final Set<Object> ourTransparentEntities = Collections.newSetFromMap(CollectionFactory.createConcurrentWeakMap());

  @SuppressWarnings("unused")
  public ModalityStateEx() { } // used by reflection to initialize NON_MODAL

  ModalityStateEx(@NotNull Collection<Object> modalEntities) {
    myModalEntities.addAll(modalEntities);
  }

  @NotNull List<Object> getModalEntities() {
    return myModalEntities.toStrongList();
  }

  @NotNull
  public ModalityState appendProgress(@NotNull ProgressIndicator progress){
    return appendEntity(progress);
  }

  @NotNull ModalityStateEx appendEntity(@NotNull Object anEntity){
    List<Object> modalEntities = getModalEntities();
    List<Object> list = new ArrayList<>(modalEntities.size() + 1);
    list.addAll(modalEntities);
    list.add(anEntity);
    return new ModalityStateEx(list);
  }

  void forceModalEntities(List<Object> entities) {
    myModalEntities.clear();
    myModalEntities.addAll(entities);
  }

  @Override
  public boolean dominates(@NotNull ModalityState anotherState){
    if (anotherState == ModalityState.any()) return false;
    if (myModalEntities.isEmpty()) return false;

    List<Object> otherEntities = ((ModalityStateEx)anotherState).getModalEntities();
    for (Object entity : getModalEntities()) {
      if (!otherEntities.contains(entity) && !ourTransparentEntities.contains(entity)) return true; // I have entity which is absent in anotherState
    }
    return false;
  }

  void cancelAllEntities() {
    for (Object entity : myModalEntities) {
      if (entity instanceof Dialog) {
        ((Dialog)entity).setVisible(false);
      }
      else if (entity instanceof ProgressIndicator) {
        ((ProgressIndicator)entity).cancel();
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
    Object element = ContainerUtil.getLastItem(getModalEntities(), null);
    if (element != null) {
      ourTransparentEntities.add(element);
    }
  }

  static void unmarkTransparent(@NotNull Object modalEntity) {
    ourTransparentEntities.remove(modalEntity);
  }
}