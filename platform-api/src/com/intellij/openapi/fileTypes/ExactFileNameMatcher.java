/*
 * @author max
 */
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ExactFileNameMatcher implements FileNameMatcher {
  private final String myFileName;

  public ExactFileNameMatcher(@NotNull @NonNls final String fileName) {
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

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final ExactFileNameMatcher that = (ExactFileNameMatcher)o;

    if (!myFileName.equals(that.myFileName)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myFileName.hashCode();
  }
}