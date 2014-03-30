/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

public class ExternalInfo {
  private boolean myIsImported = false;
  private String myOriginalPath = null;
  private String myCurrentFileName = null;
  private String myPreviouslySavedName = null;
  private Long mySavedHash = null;

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

  public String getPreviouslySavedName() {
    return myPreviouslySavedName;
  }

  public void setCurrentFileName(final String currentFileName) {
    myCurrentFileName = currentFileName;
  }

  public void setPreviouslySavedName(final String previouslySavedName) {
    myPreviouslySavedName = previouslySavedName;
  }

  public void copy(final ExternalInfo externalInfo) {
    myCurrentFileName = externalInfo.myCurrentFileName;
    myIsImported = externalInfo.isIsImported();
    myOriginalPath = externalInfo.myOriginalPath;
    myPreviouslySavedName = externalInfo.myPreviouslySavedName;
  }

  public Long getHash() {
    return mySavedHash;
  }

  public void setHash(final long newHash) {
    mySavedHash = newHash;
  }
}
