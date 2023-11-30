// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.messages;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.impl.BuildTargetChunk;

/**
 * @author Eugene Zhuravlev
 */
public final class ProgressMessage extends BuildMessage {
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
  public @Nullable BuildTargetChunk getCurrentTargets() {
    return myCurrentTargets;
  }
}
