/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.formatting;

import com.intellij.util.SequentialTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * //TODO den add doc
 * 
 * @author Denis Zhdanov
 * @since 2/10/11 3:38 PM
 */
public interface FormattingProgressIndicator {

  enum EventType { SUCCESS, CANCEL }

  //TODO den add doc
  void afterWrappingBlock(@NotNull LeafBlockWrapper wrapped);

  //TODO den add doc
  void afterProcessingBlock(@NotNull LeafBlockWrapper block);

  //TODO den add doc
  void beforeApplyingFormatChanges(@NotNull Collection<LeafBlockWrapper> modifiedBlocks);

  //TODO den add doc
  void afterApplyingChange(@NotNull LeafBlockWrapper block);

  //TODO den add doc
  void setTask(@Nullable SequentialTask task);
  
  //TODO den add doc
  boolean addCallback(@NotNull EventType eventType, @NotNull Runnable callback);
  
  //TODO den add support for bulk reformatting
  
  /**
   * <a hrep="http://en.wikipedia.org/wiki/Null_Object_pattern">Null object</a> for {@link FormattingProgressIndicator}. 
   */
  FormattingProgressIndicator EMPTY = new FormattingProgressIndicator() {
    @Override
    public void afterWrappingBlock(@NotNull LeafBlockWrapper wrapped) {
    }

    @Override
    public void afterProcessingBlock(@NotNull LeafBlockWrapper block) {
    }

    @Override
    public void beforeApplyingFormatChanges(@NotNull Collection<LeafBlockWrapper> modifiedBlocks) {
    }

    @Override
    public void afterApplyingChange(@NotNull LeafBlockWrapper block) {
    }

    @Override
    public void setTask(@Nullable SequentialTask task) {
    }

    @Override
    public boolean addCallback(@NotNull EventType eventType, @NotNull Runnable callback) {
      return false;
    }
  };
}
