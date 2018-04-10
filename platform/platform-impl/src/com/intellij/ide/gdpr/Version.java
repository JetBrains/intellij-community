/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ide.gdpr;

import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 * Date: 05-Dec-17
 */
public final class Version implements Comparable<Version> {

  public static final Version UNKNOWN = new Version(-1, -1);

  private final int myMajor;
  private final int myMinor;

  Version(int major, int minor) {
    myMajor = major;
    myMinor = minor;
  }

  /**
   * @param ver string in format "[major].[minor]"
   */
  public static Version fromString(@Nullable String ver) {
    int major = -1, minor = -1;
    final int dot = ver == null ? -1 : ver.indexOf('.');
    if (dot > 0) {
      major = Integer.parseInt(ver.substring(0, dot));
      minor = Integer.parseInt(ver.substring(dot + 1));
    }
    return major < 0 || minor < 0 ? UNKNOWN : new Version(major, minor);
  }

  public boolean isUnknown() {
    return myMajor < 0 || myMinor < 0;
  }

  public int getMajor() {
    return myMajor;
  }

  public int getMinor() {
    return myMinor;
  }

  @Override
  public int compareTo(Version other) {
    if (isUnknown()) {
      return other.isUnknown() ? 0 : -1;
    }
    final int majorDiff = myMajor - other.myMajor;
    return majorDiff != 0 ? majorDiff : myMinor - other.myMinor;
  }

  public boolean isNewer(Version other) {
    return compareTo(other) > 0;
  }

  public boolean isOlder(Version other) {
    return compareTo(other) < 0;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Version version = (Version)o;

    if (myMajor != version.myMajor) {
      return false;
    }
    if (myMinor != version.myMinor) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = myMajor;
    result = 31 * result + myMinor;
    return result;
  }

  @Override
  public String toString() {
    return isUnknown() ? "unknown" : myMajor + "." + myMinor;
  }

}
