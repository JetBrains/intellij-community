/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author traff
 */
public class MutableCollectionComboBoxModel<T> extends AbstractCollectionComboBoxModel {
  private List<T> myItems;

  public MutableCollectionComboBoxModel(@NotNull List<T> items, @Nullable Object selection) {
    super(selection);

    myItems = items;
  }

  public MutableCollectionComboBoxModel(List<T> items) {
    super(items.isEmpty() ? null : items.get(0));
    myItems = items;
  }

  @NotNull
  @Override
  final protected List<T> getItems() {
    return myItems;
  }

  public void update(List<T> items) {
    myItems = items;
    super.update();
  }
}
