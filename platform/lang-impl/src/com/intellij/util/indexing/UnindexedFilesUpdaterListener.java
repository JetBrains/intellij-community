// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

public interface UnindexedFilesUpdaterListener {
  Topic<UnindexedFilesUpdaterListener> TOPIC = Topic.create("unindexed.files.updater.listener",
                                                            UnindexedFilesUpdaterListener.class);

  void updateStarted(@NotNull Project project);

  void updateFinished(@NotNull Project project);
}
