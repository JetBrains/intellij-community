// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webcore.packaging;

import com.intellij.util.CatchingConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public abstract class PackageManagementServiceEx extends PackageManagementService {

  public abstract void updatePackage(@NotNull InstalledPackage installedPackage,
                                     @Nullable String version,
                                     @NotNull Listener listener);

  public boolean shouldFetchLatestVersionsForOnlyInstalledPackages() {
    return true;
  }

  public abstract void fetchLatestVersion(@NotNull InstalledPackage pkg, final @NotNull CatchingConsumer<? super String, ? super Exception> consumer);

  public void installPackage(final RepoPackage repoPackage,
                             final @Nullable String version,
                             @Nullable String extraOptions,
                             final Listener listener,
                             final @NotNull File workingDir) {}
}
