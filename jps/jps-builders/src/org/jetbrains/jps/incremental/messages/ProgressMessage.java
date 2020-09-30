/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental.messages;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.impl.BuildTargetChunk;

/**
 * @author Eugene Zhuravlev
 */
public class ProgressMessage extends BuildMessage {
  private volatile float myDone;
  private final BuildTargetChunk myCurrentTargets;

  public ProgressMessage(@Nls(capitalization = Nls.Capitalization.Sentence) String messageText, BuildTargetChunk currentTargets) {
    this(messageText, -1.0f, currentTargets);
  }

  public ProgressMessage(@Nls(capitalization = Nls.Capitalization.Sentence) String messageText) {
    this(messageText, -1.0f);
  }

  public ProgressMessage(@Nls(capitalization = Nls.Capitalization.Sentence) String messageText, float done) {
    this(messageText, done, null);
  }

  private ProgressMessage(@Nls(capitalization = Nls.Capitalization.Sentence) String messageText, float done, BuildTargetChunk currentTargets) {
    super(messageText, Kind.PROGRESS);
    myDone = done;
    myCurrentTargets = currentTargets;
  }

  public float getDone() {
    return myDone;
  }

  public void setDone(float done) {
    myDone = done;
  }

  /**
   * If this message reports a progress of building some build target (or set of build targets), returns the corresponding {@link BuildTargetChunk}.
   * If this message is about the build process itself, returns {@code null}.
   */
  @Nullable
  public BuildTargetChunk getCurrentTargets() {
    return myCurrentTargets;
  }
}
