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
