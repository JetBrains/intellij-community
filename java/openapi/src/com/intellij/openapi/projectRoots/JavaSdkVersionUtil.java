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
    return sdk != null && sdk.getSdkType() instanceof JavaSdk ? ((JavaSdk)sdk.getSdkType()).getVersion(sdk) : null;
  }
}