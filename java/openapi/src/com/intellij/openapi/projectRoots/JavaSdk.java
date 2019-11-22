// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public abstract class JavaSdk extends SdkType implements JavaSdkType {
  public static JavaSdk getInstance() {
    return SdkType.EP_NAME.findExtension(JavaSdk.class);
  }

  public JavaSdk(@NotNull String name) {
    super(name);
  }

  @NotNull
  public final Sdk createJdk(@NotNull String jdkName, @NotNull String jreHome) {
    return createJdk(jdkName, jreHome, true);
  }

  @NotNull
  public abstract Sdk createJdk(@NotNull String jdkName, @NotNull String home, boolean isJre);

  @Nullable
  public abstract JavaSdkVersion getVersion(@NotNull Sdk sdk);

  public abstract boolean isOfVersionOrHigher(@NotNull Sdk sdk, @NotNull JavaSdkVersion version);

  //<editor-fold desc="Deprecated stuff.">
  /** @deprecated use {@link JdkUtil#checkForJdk} */
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  @Deprecated
  public static boolean checkForJdk(@NotNull File file) {
    return JdkUtil.checkForJdk(file);
  }

  /** @deprecated use {@link JdkUtil#checkForJre} */
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  @Deprecated
  public static boolean checkForJre(@NotNull String file) {
    return JdkUtil.checkForJre(file);
  }

  /** @deprecated use {@link JavaSdkVersion#fromVersionString} */
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  @Deprecated
  public abstract JavaSdkVersion getVersion(@NotNull String versionString);
  //</editor-fold>
}