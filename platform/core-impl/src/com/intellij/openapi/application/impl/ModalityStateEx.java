/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.application.impl;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class ModalityStateEx extends ModalityState {
  private static final WeakReference[] EMPTY_REFS_ARRAY = new WeakReference[0];

  private final WeakReference[] myModalEntities;

  public ModalityStateEx() {
    this(EMPTY_REFS_ARRAY);
  }

  public ModalityStateEx(@NotNull Object[] modalEntities) {
    if (modalEntities.length > 0) {
      myModalEntities = new WeakReference[modalEntities.length];
      for (int i = 0; i < modalEntities.length; i++) {
        Object entity = modalEntities[i];
        myModalEntities[i] = new WeakReference<Object>(entity);
      }
    }
    else{
      myModalEntities = EMPTY_REFS_ARRAY;
    }
  }

  private List<Object> getModalEntities() {
    return ContainerUtil.mapNotNull(myModalEntities, new Function<WeakReference, Object>() {
      @Override
      public Object fun(WeakReference reference) {
        return reference.get();
      }
    });
  }

  @NotNull
  public ModalityState appendProgress(@NotNull ProgressIndicator progress){
    return appendEntity(progress);
  }

  @NotNull
  ModalityStateEx appendEntity(@NotNull Object anEntity){
    List<Object> list = new ArrayList<Object>(myModalEntities.length+1);
    list.addAll(getModalEntities());
    list.add(anEntity);
    return new ModalityStateEx(list.toArray());
  }

  @Override
  public boolean dominates(@NotNull ModalityState anotherState){
    if (anotherState == ModalityState.any()) return false;

    List<Object> otherEntities = ((ModalityStateEx)anotherState).getModalEntities();
    for (Object entity : getModalEntities()) {
      if (!otherEntities.contains(entity)) return true; // I have entity which is absent in anotherState
    }
    return false;
  }

  boolean contains(@NotNull Object modalEntity) {
    return getModalEntities().contains(modalEntity);
  }

  @NonNls
  public String toString() {
    if (myModalEntities.length == 0) return "ModalityState.NON_MODAL";
    @NonNls StringBuilder buffer = new StringBuilder();
    buffer.append("ModalityState:");
    for (int i = 0; i < myModalEntities.length; i++) {
      Object entity = myModalEntities[i].get();
      if (i > 0) buffer.append(", ");
      buffer.append(entity);
    }
    return buffer.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ModalityStateEx)) return false;

    List<Object> entities = getModalEntities();
    if (entities.isEmpty()) return false; // e.g. NON_MODAL isn't equal to ANY

    return entities.equals(((ModalityStateEx)o).getModalEntities());
  }

  @Override
  public int hashCode() {
    return getModalEntities().hashCode();
  }
}
