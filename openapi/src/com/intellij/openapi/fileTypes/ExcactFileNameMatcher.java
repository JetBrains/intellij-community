/*
 * @author max
 */
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ExcactFileNameMatcher implements FileNameMatcher {
  private final String myFileName;

  public ExcactFileNameMatcher(@NotNull @NonNls final String fileName) {
    myFileName = fileName;
  }

  public boolean accept(@NonNls @NotNull final String fileName) {
    return Comparing.equal(fileName, myFileName, SystemInfo.isFileSystemCaseSensitive);
  }

  @NonNls
  @NotNull
  public String getPresentableString() {
    return myFileName;
  }

  public String getFileName() {
    return myFileName;
  }
}