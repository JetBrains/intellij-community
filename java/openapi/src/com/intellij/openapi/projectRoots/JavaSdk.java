/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.impl.SdkVersionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public abstract class JavaSdk extends SdkType implements JavaSdkType {
  public static JavaSdk getInstance() {
    return ApplicationManager.getApplication().getComponent(JavaSdk.class);
  }

  public JavaSdk(@NotNull String name) {
    super(name);
  }

  @NotNull
  public final Sdk createJdk(@NotNull String jdkName, @NotNull String jreHome) {
    return createJdk(jdkName, jreHome, true);
  }

  @NotNull
  public abstract Sdk createJdk(String jdkName, @NotNull String home, boolean isJre);

  @Nullable
  public abstract JavaSdkVersion getVersion(@NotNull Sdk sdk);

  @Nullable
  public abstract JavaSdkVersion getVersion(@NotNull String versionString);

  public abstract boolean isOfVersionOrHigher(@NotNull Sdk sdk, @NotNull JavaSdkVersion version);

  /** @deprecated use {@link JdkUtil#checkForJdk} (to be removed in IDEA 2019) */
  public static boolean checkForJdk(@NotNull File file) {
    return JdkUtil.checkForJdk(file);
  }

  /** @deprecated use {@link JdkUtil#checkForJre} (to be removed in IDEA 2019) */
  public static boolean checkForJre(@NotNull String file) {
    return JdkUtil.checkForJre(file);
  }

  /** @deprecated use {@link SdkVersionUtil#detectJdkVersion} (to be removed in IDEA 2019) */
  public static String getJdkVersion(@NotNull String sdkHome) {
    return SdkVersionUtil.detectJdkVersion(sdkHome);
  }
}