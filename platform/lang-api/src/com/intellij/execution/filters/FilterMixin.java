/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.filters;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 8/3/11
 * Time: 4:50 PM
 */
public interface FilterMixin {
  boolean shouldRunHeavy();
  void applyHeavyFilter(@NotNull Document copiedFragment, int startOffset, int startLineNumber, @NotNull Consumer<AdditionalHighlight> consumer);

  @NotNull
  String getUpdateMessage();

  abstract class AdditionalHighlight {
    private final int myStart;
    private final int myEnd;

    protected AdditionalHighlight(int start, int end) {
      myStart = start;
      myEnd = end;
    }

    public int getStart() {
      return myStart;
    }

    public int getEnd() {
      return myEnd;
    }

    @NotNull
    public abstract TextAttributes getTextAttributes(@Nullable final TextAttributes source);
  }
}
