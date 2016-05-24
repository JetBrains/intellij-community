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
package com.intellij.openapi.editor;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;
import java.util.List;

public interface InlayModel {
  @Nullable
  Inlay addInlineElement(int offset, @NotNull Inlay.Renderer renderer);

  // startOffset inclusive, endOffset exclusive (unless startOffset == endOffset)
  @NotNull
  List<Inlay> getInlineElementsInRange(int startOffset, int endOffset);

  void addListener(@NotNull Listener listener, @NotNull Disposable disposable);

  interface Listener extends EventListener {
    void onAdded(Inlay inlay);

    void onRemoved(Inlay inlay);
  }

  abstract class Adapter implements Listener {
    @Override
    public void onAdded(Inlay inlay) {
      onChanged(inlay);
    }

    @Override
    public void onRemoved(Inlay inlay) {
      onChanged(inlay);
    }

    public void onChanged(Inlay inlay) {}
  }
}
