// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependenciesCache;

import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class ExcludePolicyDescriptor {
  private final Class<? extends DirectoryIndexExcludePolicy> policyClass;
  private final String[] excludedRootUrls;
  final Set<VirtualFile> excludedFromSdkRoots;

  private static ExcludePolicyDescriptor create(DirectoryIndexExcludePolicy policy, Set<Sdk> sdks, Set<VirtualFile> sdkRoots) {
    var excludedRootUrls = policy.getExcludeUrlsForProject();
    if (excludedRootUrls.length == 0) excludedRootUrls = ArrayUtil.EMPTY_STRING_ARRAY;
    @SuppressWarnings("removal")
    var strategy = policy.getExcludeSdkRootsStrategy();
    Set<VirtualFile> excludedFromSdkRoots;
    if (strategy == null) {
      excludedFromSdkRoots = Set.of();
    }
    else {
      var excludedFromSdk = new ArrayList<VirtualFile>();
      for (var sdk : sdks) {
        var excluded = strategy.fun(sdk);
        for (var file : excluded) {
          if (!sdkRoots.contains(file)) {
            excludedFromSdk.add(file);
          }
        }
      }
      excludedFromSdkRoots = Set.copyOf(excludedFromSdk);
    }
    return new ExcludePolicyDescriptor(policy.getClass(), excludedRootUrls, excludedFromSdkRoots);
  }

  private ExcludePolicyDescriptor(
    Class<? extends DirectoryIndexExcludePolicy> policyClass,
    String[] excludedRootUrls,
    Set<VirtualFile> excludedFromSdkRoots
  ) {
    this.policyClass = policyClass;
    this.excludedRootUrls = excludedRootUrls;
    this.excludedFromSdkRoots = excludedFromSdkRoots;
  }

  public @NotNull Set<VirtualFile> getExcludedRoots() {
    return Set.copyOf(ContainerUtil.mapNotNull(excludedRootUrls, url -> VirtualFileManager.getInstance().findFileByUrl(url)));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    var that = (ExcludePolicyDescriptor)o;
    return (
      policyClass.equals(that.policyClass) &&
      Arrays.equals(excludedRootUrls, that.excludedRootUrls) &&
      excludedFromSdkRoots.equals(that.excludedFromSdkRoots)
    );
  }

  @Override
  public int hashCode() {
    var result = Objects.hash(policyClass, excludedFromSdkRoots);
    result = 31 * result + Arrays.hashCode(excludedRootUrls);
    return result;
  }

  @Override
  public String toString() {
    if (excludedRootUrls.length == 0 && excludedFromSdkRoots.isEmpty()) {
      return "ExcludePolicyDescriptor{policyClass=" + policyClass.getSimpleName() + "; empty}";
    }
    else {
      return (
        "ExcludePolicyDescriptor{policyClass=" + policyClass.getSimpleName() +
        ", excludedRootUrls=" + Arrays.toString(excludedRootUrls) +
        ", excludedFromSdkRoots=" + excludedFromSdkRoots + '}'
      );
    }
  }

  static @NotNull List<ExcludePolicyDescriptor> collectDescriptors(@NotNull Project project) {
    var sdks = new HashSet<Sdk>();
    for (var module : ModuleManager.getInstance(project).getModules()) {
      var sdk = ModuleRootManager.getInstance(module).getSdk();
      if (sdk != null) {
        sdks.add(sdk);
      }
    }
    var roots = new HashSet<VirtualFile>();
    for (var sdk : sdks) {
      roots.addAll(Arrays.asList(sdk.getRootProvider().getFiles(OrderRootType.CLASSES)));
    }
    var excludePolicies = new ArrayList<ExcludePolicyDescriptor>();
    for (var policy : DirectoryIndexExcludePolicy.EP_NAME.getExtensions(project)) {
      excludePolicies.add(create(policy, sdks, roots));
    }
    return excludePolicies;
  }
}
