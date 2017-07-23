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
package com.intellij.debugger.memory.component;

import com.intellij.debugger.memory.utils.StackFrameItem;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public interface TrackedStacksContainer {
  @Nullable
  List<StackFrameItem> getStack(@NotNull ObjectReference reference);

  void addStack(@NotNull ObjectReference ref, @NotNull List<StackFrameItem> frames);

  void pinStacks(@NotNull ReferenceType referenceType);

  void unpinStacks(@NotNull ReferenceType referenceType);

  void release();

  void clear();
}
