package com.intellij.facet.impl.ui.libraries.versions;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

public class LibraryVersionInfo {
  @NotNull private String myVersion;
  @Nullable private String myRI;


  public LibraryVersionInfo(@NotNull String version) {
    myVersion = version;
  }

  public LibraryVersionInfo(@NotNull String version, @Nullable String RI) {
    myVersion = version;
    myRI = RI;
  }

  @NotNull
  public String getVersion() {
    return myVersion;
  }

  public void setVersion(@NotNull String version) {
    myVersion = version;
  }

  @Nullable
  public String getRI() {
    return myRI;
  }

  public void setRI(@Nullable String RI) {
    myRI = RI;
  }

  @Override
  public String toString() {
    return myVersion;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    LibraryVersionInfo that = (LibraryVersionInfo)o;

    if (myRI != null ? !myRI.equals(that.myRI) : that.myRI != null) return false;
    if (!myVersion.equals(that.myVersion)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myVersion.hashCode();
    result = 31 * result + (myRI != null ? myRI.hashCode() : 0);
    return result;
  }
}
