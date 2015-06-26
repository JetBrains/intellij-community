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
package com.intellij.openapi.options;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Deprecated
public final class ExternalInfo {
  // we keep it to detect rename
  private String myPreviouslySavedName;
  private String myCurrentFileName;

  private int myContentHash;

  public String getCurrentFileName() {
    return myCurrentFileName;
  }

  public void setCurrentFileName(@Nullable String currentFileName) {
    myCurrentFileName = currentFileName;
  }

  @Nullable
  public String getPreviouslySavedName() {
    return myPreviouslySavedName;
  }

  public void setPreviouslySavedName(@NotNull String previouslySavedName) {
    myPreviouslySavedName = previouslySavedName;
  }

  public int getHash() {
    return myContentHash;
  }

  public void setHash(int newHash) {
    myContentHash = newHash;
  }

  @Override
  public String toString() {
    return "file: " + myCurrentFileName;
  }
}