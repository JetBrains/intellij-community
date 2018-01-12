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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaSdkVersionUtil {
  public static boolean isAtLeast(@NotNull PsiElement element, @NotNull JavaSdkVersion minVersion) {
    JavaSdkVersion version = getJavaSdkVersion(element);
    return version == null || version.isAtLeast(minVersion);
  }

  public static JavaSdkVersion getJavaSdkVersion(@NotNull PsiElement element) {
    Module module = ModuleUtilCore.findModuleForPsiElement(element);
    return module != null ? getJavaSdkVersion(ModuleRootManager.getInstance(module).getSdk()) : null;
  }

  public static JavaSdkVersion getJavaSdkVersion(@Nullable Sdk sdk) {
    // Android Studio: Offer a reasonable language level when using an Android SDK
    // since this is tied to various editor features such as the ability to
    // to switch from 1.7 to 1.8 language level when using lambdas.
    if (sdk != null && sdk.getSdkType().getName().equals("Android SDK")) {
      // With Desugar we now support Java 1.8 language features
      return JavaSdkVersion.JDK_1_8;
    }

    return sdk != null && sdk.getSdkType() instanceof JavaSdk ? ((JavaSdk)sdk.getSdkType()).getVersion(sdk) : null;
  }
}