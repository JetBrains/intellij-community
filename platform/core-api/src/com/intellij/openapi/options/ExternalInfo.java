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
package com.intellij.openapi.options;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ExternalInfo {
  private boolean myIsImported;
  private String myOriginalPath;

  // we keep it to detect rename
  private String myPreviouslySavedName;
  private String myCurrentFileName;

  private int mySavedHash;

  private boolean myRemote;

  public void setIsImported(final boolean isImported) {
    myIsImported = isImported;
  }

  public void setOriginalPath(final String originalPath) {
    myOriginalPath = originalPath;
  }

  public boolean isIsImported() {
    return myIsImported;
  }

  public String getOriginalPath() {
    return myOriginalPath;
  }

  public String getCurrentFileName() {
    return myCurrentFileName;
  }

  public void setCurrentFileName(@Nullable String currentFileName) {
    myCurrentFileName = currentFileName;
  }

  public void copy(@NotNull ExternalInfo externalInfo) {
    myCurrentFileName = externalInfo.myCurrentFileName;
    myIsImported = externalInfo.isIsImported();
    myOriginalPath = externalInfo.myOriginalPath;
  }

  public String getPreviouslySavedName() {
    return myPreviouslySavedName;
  }

  public void setPreviouslySavedName(final String previouslySavedName) {
    myPreviouslySavedName = previouslySavedName;
  }

  public int getHash() {
    return mySavedHash;
  }

  public void setHash(int newHash) {
    mySavedHash = newHash;
  }

  public boolean isRemote() {
    return myRemote;
  }

  public void markRemote() {
    myRemote = true;
  }

  @Override
  public String toString() {
    return "file: " + myCurrentFileName + (myRemote ? ", remote" : "");
  }
}
