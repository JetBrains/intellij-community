// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaSdkVersionUtil {
  public static boolean isAtLeast(@NotNull PsiElement element, @NotNull JavaSdkVersion expected) {
    JavaSdkVersion version = getJavaSdkVersion(element);
    return version == null || version.isAtLeast(expected);
  }

  @Contract("null, _ -> false")
  public static boolean isAtLeast(@Nullable Sdk jdk, @NotNull JavaSdkVersion expected) {
    JavaSdkVersion actual = getJavaSdkVersion(jdk);
    return actual != null && actual.isAtLeast(expected);
  }

  public static JavaSdkVersion getJavaSdkVersion(@NotNull PsiElement element) {
    Module module = ModuleUtilCore.findModuleForPsiElement(element);
    return module != null ? getJavaSdkVersion(ModuleRootManager.getInstance(module).getSdk()) : null;
  }

  public static JavaSdkVersion getJavaSdkVersion(@Nullable Sdk sdk) {
    if (sdk != null) {
      SdkTypeId sdkType = sdk.getSdkType();
      if (!(sdkType instanceof JavaSdk) && sdkType instanceof SdkType) {
        sdkType = ((SdkType)sdkType).getDependencyType();
      }
      if (sdkType instanceof JavaSdk) {
        return ((JavaSdk)sdkType).getVersion(sdk);
      }
    }
    return null;
  }

  @Nullable
  public static Sdk findJdkByVersion(@NotNull JavaSdkVersion version) {
    JavaSdk javaSdk = JavaSdk.getInstance();
    Sdk candidate = null;
    for (Sdk sdk : ProjectJdkTable.getInstance().getSdksOfType(javaSdk)) {
      JavaSdkVersion v = javaSdk.getVersion(sdk);
      if (v == version) {
        return sdk;  // exact match
      }
      if (candidate == null && v != null && v.isAtLeast(version)) {
        candidate = sdk;  // first suitable
      }
    }
    return candidate;
  }
}