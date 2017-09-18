/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.WeakList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ModalityStateEx extends ModalityState {
  private final WeakList<Object> myModalEntities = new WeakList<>();

  @SuppressWarnings("unused")
  public ModalityStateEx() { } // used by reflection to initialize NON_MODAL

  public ModalityStateEx(@NotNull Object[] modalEntities) {
    Collections.addAll(myModalEntities, modalEntities);
  }

  private List<Object> getModalEntities() {
    return myModalEntities.toStrongList();
  }

  @NotNull
  public ModalityState appendProgress(@NotNull ProgressIndicator progress){
    return appendEntity(progress);
  }

  @NotNull
  ModalityStateEx appendEntity(@NotNull Object anEntity){
    List<Object> modalEntities = getModalEntities();
    List<Object> list = new ArrayList<>(modalEntities.size() + 1);
    list.addAll(modalEntities);
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

  @NonNls
  public String toString() {
    return this == NON_MODAL
           ? "ModalityState.NON_MODAL"
           : "ModalityState:{" + StringUtil.join(getModalEntities(), it -> "[" + it + "]", ", ") + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ModalityStateEx)) return false;

    List<Object> entities = getModalEntities();
    if (entities.isEmpty()) return false; //e.g. NON_MODAL isn't equal to ANY

    return entities.equals(((ModalityStateEx)o).getModalEntities());
  }

  @Override
  public int hashCode() {
    return getModalEntities().hashCode();
  }

  void removeModality(Object modalEntity) {
    myModalEntities.remove(modalEntity);
  }
}