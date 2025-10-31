// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

public final class JavaSdkVersionUtil {

  public static boolean isAtLeast(@NotNull PsiElement element, @NotNull JavaSdkVersion expected) {
    JavaSdkVersion version = getJavaSdkVersion(element);
    return version == null || version.isAtLeast(expected);
  }

  @Contract("null, _ -> true")
  public static boolean isAtLeast(@Nullable Sdk jdk, @NotNull JavaSdkVersion expected) {
    JavaSdkVersion actual = getJavaSdkVersion(jdk);
    return actual == null || actual.isAtLeast(expected);
  }

  public static @Nullable JavaSdkVersion getJavaSdkVersion(@NotNull PsiElement element) {
    Module module = ModuleUtilCore.findModuleForPsiElement(element);
    return module != null ? getJavaSdkVersion(ModuleRootManager.getInstance(module).getSdk()) : null;
  }

  public static @Nullable JavaSdkVersion getJavaSdkVersion(@Nullable Sdk sdk) {
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

  /**
   * Obsolete: ignores environment‑scoped SDKs (WSL, Docker, Remote Dev).
   */
  @ApiStatus.Obsolete
  public static @Nullable Sdk findJdkByVersion(@NotNull JavaSdkVersion version) {
    JavaSdk javaSdk = JavaSdk.getInstance();
    Sdk candidate = null;
    for (Sdk sdk : ProjectJdkTable.getInstance().getSdksOfType(javaSdk)) {
      String homePath = sdk.getHomePath();
      if (homePath == null || !javaSdk.isValidSdkHome(homePath)) continue;
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

  public static @NotNull Comparator<Sdk> naturalJavaSdkOrder(boolean nullsFirst) {
    var javaSdkVersionComparator =
      nullsFirst ? Comparator.nullsFirst(Comparator.<JavaSdkVersion>naturalOrder())
                 : Comparator.nullsLast(Comparator.<JavaSdkVersion>naturalOrder());
    return (sdk1, sdk2) -> {
      var jdkVersion1 = getJavaSdkVersion(sdk1);
      var jdkVersion2 = getJavaSdkVersion(sdk2);
      return javaSdkVersionComparator.compare(jdkVersion1, jdkVersion2);
    };
  }
}