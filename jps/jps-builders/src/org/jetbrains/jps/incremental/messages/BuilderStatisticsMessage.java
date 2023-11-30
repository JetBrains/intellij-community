// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.messages;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.Utils;

public final class BuilderStatisticsMessage extends BuildMessage {
  private final String myBuilderName;
  private final int myNumberOfProcessedSources;
  private final long myElapsedTimeMs;

  public BuilderStatisticsMessage(@Nls String builderName, int numberOfProcessedSources, long elapsedTimeMs) {
    super(createText(builderName, numberOfProcessedSources, elapsedTimeMs), Kind.INFO);
    myBuilderName = builderName;
    myNumberOfProcessedSources = numberOfProcessedSources;
    myElapsedTimeMs = elapsedTimeMs;
  }

  private static @NotNull String createText(String builderName, int srcCount, long time) {
    return "Build duration: Builder '" + StringUtil.capitalize(builderName) + "' took " + Utils.formatDuration(time) + "; " +
           srcCount + " sources processed" +
           (srcCount == 0 ? "" : " (" + time / srcCount + " ms per file)");
  }

  public String getBuilderName() {
    return myBuilderName;
  }

  public int getNumberOfProcessedSources() {
    return myNumberOfProcessedSources;
  }

  public long getElapsedTimeMs() {
    return myElapsedTimeMs;
  }
}
