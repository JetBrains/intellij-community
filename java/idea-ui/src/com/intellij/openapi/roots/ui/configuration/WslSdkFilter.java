// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.execution.wsl.WSLDistribution;
import com.intellij.execution.wsl.WSLUtil;
import com.intellij.execution.wsl.WslPath;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import org.jetbrains.annotations.NotNull;

public class WslSdkFilter {
  public static Condition<? super Sdk> filterSdkByWsl(@NotNull Project project) {
    var path = project.getBasePath();
    if (path == null || !WSLUtil.isSystemCompatible()) {
      return Conditions.alwaysTrue();
    }

    var distribution = WslPath.getDistributionByWindowsUncPath(path);
    var projectOnLocalFs = distribution == null;
    return (Condition<Sdk>)sdk -> {
      if (projectOnLocalFs && sdk.getSdkType().allowWslSdkForLocalProject()) {
        return true;
      }
      String sdkHomePath = sdk.getHomePath();
      return sdkHomePath == null || WslPath.getDistributionByWindowsUncPath(sdkHomePath) == distribution;
    };
  }

  public static Condition<? super SdkListItem.SuggestedItem> filterSdkSuggestionByWsl(@NotNull Project project) {
    String path = project.getBasePath();
    if (path == null || !WSLUtil.isSystemCompatible()) return Conditions.alwaysTrue();
    WSLDistribution distribution = WslPath.getDistributionByWindowsUncPath(path);
    return (Condition<SdkListItem.SuggestedItem>)suggestedItem -> {
      return WslPath.getDistributionByWindowsUncPath(suggestedItem.homePath) == distribution;
    };
  }
}
