/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.Utils;

public class BuilderStatisticsMessage extends BuildMessage {
  private final String myBuilderName;
  private final int myNumberOfProcessedSources;
  private final long myElapsedTimeMs;

  public BuilderStatisticsMessage(@Nls String builderName, int numberOfProcessedSources, long elapsedTimeMs) {
    super(createText(builderName, numberOfProcessedSources, elapsedTimeMs), Kind.INFO);
    myBuilderName = builderName;
    myNumberOfProcessedSources = numberOfProcessedSources;
    myElapsedTimeMs = elapsedTimeMs;
  }

  @NotNull
  private static String createText(String builderName, int srcCount, long time) {
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
