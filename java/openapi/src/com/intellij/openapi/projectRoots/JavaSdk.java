/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public abstract class JavaSdk extends SdkType implements JavaSdkType {
  public JavaSdk(@NotNull @NonNls String name) {
    super(name);
  }

  public static JavaSdk getInstance() {
    return ApplicationManager.getApplication().getComponent(JavaSdk.class);
  }

  @NotNull
  public final Sdk createJdk(@NotNull String jdkName, @NotNull String jreHome) {
    return createJdk(jdkName, jreHome, true);
  }

  /**
   * @deprecated use {@link #isOfVersionOrHigher(Sdk, JavaSdkVersion)} instead
   */
  public abstract int compareTo(@NotNull String versionString, @NotNull String versionNumber);

  @NotNull
  public abstract Sdk createJdk(@NonNls String jdkName, @NotNull String home, boolean isJre);

  @Nullable
  public abstract JavaSdkVersion getVersion(@NotNull Sdk sdk);

  @Nullable
  public abstract JavaSdkVersion getVersion(@NotNull String versionString);

  public abstract boolean isOfVersionOrHigher(@NotNull Sdk sdk, @NotNull JavaSdkVersion version);

  public static boolean checkForJdk(@NotNull File file) {
    return JdkUtil.checkForJdk(file);
  }

  public static boolean checkForJre(@NotNull String file) {
    return JdkUtil.checkForJre(file);
  }

  @Nullable
  public static String getJdkVersion(@NotNull String sdkHome) {
    return SdkVersionUtil.detectJdkVersion(sdkHome);
  }
}
