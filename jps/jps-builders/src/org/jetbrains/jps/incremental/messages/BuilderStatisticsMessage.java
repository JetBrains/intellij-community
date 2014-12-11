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

/**
 * @author nik
 */
public class BuilderStatisticsMessage extends BuildMessage {
  private final String myBuilderName;
  private final int myNumberOfProcessedSources;
  private final long myElapsedTimeMs;

  public BuilderStatisticsMessage(String builderName, int numberOfProcessedSources, long elapsedTimeMs) {
    super("", Kind.INFO);
    myBuilderName = builderName;
    myNumberOfProcessedSources = numberOfProcessedSources;
    myElapsedTimeMs = elapsedTimeMs;
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
