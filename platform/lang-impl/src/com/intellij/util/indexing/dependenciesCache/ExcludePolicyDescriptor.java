// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependenciesCache;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

final class ExcludePolicyDescriptor {
  private final @NotNull Class<? extends DirectoryIndexExcludePolicy> policyClass;
  private final String[] excludedRootUrls;
  final @NotNull Set<VirtualFile> excludedFromSdkRoots;

  static @NotNull ExcludePolicyDescriptor create(@NotNull DirectoryIndexExcludePolicy policy,
                                                 @NotNull Set<? extends Sdk> sdks,
                                                 @NotNull Set<? extends VirtualFile> sdkRoots) {
    String[] excludedRootUrls = policy.getExcludeUrlsForProject();
    if (excludedRootUrls.length == 0) excludedRootUrls = ArrayUtil.EMPTY_STRING_ARRAY;
    Function<Sdk, List<VirtualFile>> strategy = policy.getExcludeSdkRootsStrategy();
    Set<VirtualFile> excludedFromSdkRoots;
    if (strategy == null) {
      excludedFromSdkRoots = Collections.emptySet();
    }
    else {
      Collection<VirtualFile> excludedFromSdk = new ArrayList<>();
      for (Sdk sdk : sdks) {
        List<VirtualFile> excluded = strategy.fun(sdk);
        for (VirtualFile file : excluded) {
          if (!sdkRoots.contains(file)) {
            excludedFromSdk.add(file);
          }
        }
      }
      excludedFromSdkRoots = Set.copyOf(excludedFromSdk);
    }
    return new ExcludePolicyDescriptor(policy.getClass(), excludedRootUrls, excludedFromSdkRoots);
  }

  private ExcludePolicyDescriptor(@NotNull Class<? extends DirectoryIndexExcludePolicy> policyClass,
                                  String[] excludedRootUrls,
                                  @NotNull Set<VirtualFile> excludedFromSdkRoots) {
    this.policyClass = policyClass;
    this.excludedRootUrls = excludedRootUrls;
    this.excludedFromSdkRoots = excludedFromSdkRoots;
  }

  public @NotNull Set<VirtualFile> getExcludedRoots() {
    return Set.copyOf(ContainerUtil.mapNotNull(excludedRootUrls, url -> {
      return VirtualFileManager.getInstance().findFileByUrl(url);
    }));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ExcludePolicyDescriptor that = (ExcludePolicyDescriptor)o;
    return policyClass.equals(that.policyClass) &&
           Arrays.equals(excludedRootUrls, that.excludedRootUrls) &&
           excludedFromSdkRoots.equals(that.excludedFromSdkRoots);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(policyClass, excludedFromSdkRoots);
    result = 31 * result + Arrays.hashCode(excludedRootUrls);
    return result;
  }

  @Override
  public String toString() {
    if (excludedRootUrls.length == 0 && excludedFromSdkRoots.isEmpty()) {
      return "ExcludePolicyDescriptor{" +
             "policyClass=" + policyClass.getSimpleName() +
             "; empty}";
    }
    return "ExcludePolicyDescriptor{" +
           "policyClass=" + policyClass.getSimpleName() +
           ", excludedRootUrls=" + Arrays.toString(excludedRootUrls) +
           ", excludedFromSdkRoots=" + excludedFromSdkRoots +
           '}';
  }

  public static @NotNull List<ExcludePolicyDescriptor> collectDescriptors(@NotNull Project project) {
    Set<Sdk> sdks = new HashSet<>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
      if (sdk != null) {
        sdks.add(sdk);
      }
    }
    Set<VirtualFile> roots = new HashSet<>();
    for (Sdk sdk : sdks) {
      roots.addAll(Arrays.asList(sdk.getRootProvider().getFiles(OrderRootType.CLASSES)));
    }
    List<ExcludePolicyDescriptor> excludePolicies = new ArrayList<>();
    for (DirectoryIndexExcludePolicy policy : DirectoryIndexExcludePolicy.EP_NAME.getExtensions(project)) {
      excludePolicies.add(create(policy, sdks, roots));
    }
    return excludePolicies;
  }
}
