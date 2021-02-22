// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.webcore.packaging;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author yole
 */
public class InstalledPackage {
  private final @NotNull String myName;
  private final @Nullable String myVersion;

  public InstalledPackage(@NotNull String name, @Nullable String version) {
    myName = name;
    myVersion = version;
  }

  public @NlsSafe @NotNull String getName() {
    return myName;
  }

  public @NlsSafe @Nullable String getVersion() {
    return myVersion;
  }

  public @NlsContexts.Tooltip @Nullable String getTooltipText() {
    return null;
  }

  @Override
  public @NotNull String toString() {
    return getName();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    InstalledPackage aPackage = (InstalledPackage)o;
    return myName.equals(aPackage.myName) &&
           Objects.equals(myVersion, aPackage.myVersion);
  }

  @Override
  public int hashCode() {
    int result = myName.hashCode();
    result = 31 * result + (myVersion != null ? myVersion.hashCode() : 0);
    return result;
  }
}
