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
package com.intellij.diagnostic;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public interface IdePerformanceListener {
  Topic<IdePerformanceListener> TOPIC = Topic.create("IdePerformanceListener", IdePerformanceListener.class);

  /**
   * Invoked after thread state has been dumped to a file.
   */
  void dumpedThreads(@NotNull File toFile, @NotNull ThreadDump dump);

  /**
   * Invoked when IDE has detected that the UI hasn't responded for some time (5 seconds by default)
   */
  void uiFreezeStarted();

  /**
   * Invoked after the UI has become responsive again following a {@link #uiFreezeStarted()} event.
   * @param lengthInSeconds approximate length in seconds of the interval that the IDE was unresponsive
   */
  void uiFreezeFinished(int lengthInSeconds);

  /**
   * Empty implementation of {@link IdePerformanceListener}
   */
  class Adapter implements IdePerformanceListener {
    @Override
    public void dumpedThreads(@NotNull File toFile, @NotNull ThreadDump dump) { }

    @Override
    public void uiFreezeStarted() { }

    @Override
    public void uiFreezeFinished(int lengthInSeconds) { }
  }

}
