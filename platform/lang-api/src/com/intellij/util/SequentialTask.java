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
package com.intellij.util;

import com.intellij.openapi.progress.ProgressIndicator;

/**
 * Defines general contract for processing that may be executed by parts, i.e. it remembers the state after every iteration
 * and allows to resume the processing any time.
 * 
 * @author Denis Zhdanov
 * @since 2/14/11 9:15 AM
 */
public interface SequentialTask {

  /**
   * Callback method that is assumed to be called before the processing.
   */
  void prepare();

  /**
   * @return      {@code true} if the processing is complete; {@code false} otherwise
   */
  boolean isDone();

  /**
   * Asks current task to perform one more processing iteration.
   * 
   * @return    {@code true} if the processing is done; {@code false} otherwise
   */
  boolean iteration();

  default boolean iteration(ProgressIndicator indicator) {
    return iteration();
  }

  /**
   * Asks current task to stop the processing (if any).
   */
  void stop();
}
