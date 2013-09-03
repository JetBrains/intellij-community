package com.intellij.webcore.packaging;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey Simonchik
 */
public abstract class PackageManagementServiceEx extends PackageManagementService {

  public abstract void updatePackage(@NotNull InstalledPackage installedPackage,
                                     @Nullable String version,
                                     @NotNull Listener listener);

}
