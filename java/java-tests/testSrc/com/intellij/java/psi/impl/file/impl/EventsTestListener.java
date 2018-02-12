/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.psi.impl.file.impl;

import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.PsiTreeChangeListener;
import org.jetbrains.annotations.NotNull;

/**
 *  @author dsl
 */
class EventsTestListener implements PsiTreeChangeListener {
  StringBuffer myBuffer = new StringBuffer();

  public String getEventsString() {
    return myBuffer.toString();
  }

  @Override
  public void beforeChildAddition(@NotNull PsiTreeChangeEvent event) {
    myBuffer.append("beforeChildAddition\n");
  }

  @Override
  public void beforeChildRemoval(@NotNull PsiTreeChangeEvent event) {
    myBuffer.append("beforeChildRemoval\n");
  }

  @Override
  public void beforeChildReplacement(@NotNull PsiTreeChangeEvent event) {
    myBuffer.append("beforeChildReplacement\n");
  }

  @Override
  public void beforeChildMovement(@NotNull PsiTreeChangeEvent event) {
    myBuffer.append("beforeChildMovement\n");
  }

  @Override
  public void beforeChildrenChange(@NotNull PsiTreeChangeEvent event) {
    myBuffer.append("beforeChildrenChange\n");
  }

  @Override
  public void beforePropertyChange(@NotNull PsiTreeChangeEvent event) {
    myBuffer.append("beforePropertyChange " + event.getPropertyName() + "\n");
  }

  @Override
  public void childAdded(@NotNull PsiTreeChangeEvent event) {
    myBuffer.append("childAdded\n");
  }

  @Override
  public void childRemoved(@NotNull PsiTreeChangeEvent event) {
    myBuffer.append("childRemoved\n");
  }

  @Override
  public void childReplaced(@NotNull PsiTreeChangeEvent event) {
    myBuffer.append("childReplaced\n");
  }

  @Override
  public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
    myBuffer.append("childrenChanged\n");
  }

  @Override
  public void childMoved(@NotNull PsiTreeChangeEvent event) {
    myBuffer.append("childMoved\n");
  }

  @Override
  public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
    myBuffer.append("propertyChanged " + event.getPropertyName() + "\n");
  }
}
