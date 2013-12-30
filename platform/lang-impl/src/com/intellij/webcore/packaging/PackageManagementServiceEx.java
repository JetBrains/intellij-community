package com.intellij.webcore.packaging;

import com.intellij.util.CatchingConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey Simonchik
 */
public abstract class PackageManagementServiceEx extends PackageManagementService {

  public abstract void updatePackage(@NotNull InstalledPackage installedPackage,
                                     @Nullable String version,
                                     @NotNull Listener listener);

  public abstract boolean shouldFetchLatestVersionsForOnlyInstalledPackages();

  public abstract void fetchLatestVersion(@NotNull String packageName, @NotNull final CatchingConsumer<String, Exception> consumer);

}
