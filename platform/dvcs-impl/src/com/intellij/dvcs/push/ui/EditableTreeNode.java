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
package com.intellij.dvcs.push.ui;

import com.intellij.dvcs.push.OutgoingResult;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

@ApiStatus.Internal
public interface EditableTreeNode extends CustomRenderedTreeNode {

  void fireOnChange();

  void fireOnCancel();

  void fireOnSelectionChange(boolean isSelected);

  void cancelLoading();

  void startLoading(@NotNull JTree tree, @NotNull Future<AtomicReference<OutgoingResult>> future, boolean initial);

  boolean isEditableNow();
}
