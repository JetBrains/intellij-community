// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.UnknownSdk;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkDownloadableSdkFix;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkLocalSdkFix;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class UnknownMissingSdk {
  @NotNull
  public static UnknownSdkFix createMissingSdkFix(@Nullable Project project,
                                                  @NotNull UnknownSdk unknownSdk,
                                                  @Nullable UnknownSdkLocalSdkFix localSdkFix,
                                                  @Nullable UnknownSdkDownloadableSdkFix downloadFix) {
    return createMissingSdkFix(project, unknownSdk, () -> localSdkFix, () -> downloadFix);
  }

  @NotNull
  public static UnknownSdkFix createMissingSdkFix(@Nullable Project project,
                                                  @NotNull UnknownSdk unknownSdk,
                                                  @NotNull Supplier<? extends @Nullable UnknownSdkLocalSdkFix> localSdkFixAction,
                                                  @NotNull Supplier<? extends @Nullable UnknownSdkDownloadableSdkFix> downloadFixAction) {
    return new UnknownMissingSdkFix(project, unknownSdk, createMissingFixAction(unknownSdk, localSdkFixAction, downloadFixAction));
  }

  @Nullable
  public static UnknownSdkFixAction createMissingFixAction(@NotNull UnknownSdk unknownSdk,
                                                           @NotNull Supplier<? extends @Nullable UnknownSdkLocalSdkFix> localSdkFixAction,
                                                           @NotNull Supplier<? extends @Nullable UnknownSdkDownloadableSdkFix> downloadFixAction) {
    var localSdkFix = localSdkFixAction.get();
    if (localSdkFix != null) {
      return createMissingSdkFixAction(unknownSdk, localSdkFix);
    }

    var downloadFix = downloadFixAction.get();
    if (downloadFix != null) {
      return createMissingSdkFixAction(unknownSdk, downloadFix);
    }

    return null;
  }

  @NotNull
  public static UnknownSdkFixAction createMissingSdkFixAction(@NotNull UnknownSdk unknownSdk,
                                                              @NotNull UnknownSdkLocalSdkFix localSdkFix) {
    return new UnknownMissingSdkFixLocal(unknownSdk, localSdkFix);
  }

  @NotNull
  public static UnknownSdkFixAction createMissingSdkFixAction(@NotNull UnknownSdk unknownSdk,
                                                              @NotNull UnknownSdkDownloadableSdkFix downloadFix) {
    return new UnknownMissingSdkFixDownload(unknownSdk, downloadFix);
  }


  @NlsSafe
  @NotNull
  static String getSdkNameForUi(@NotNull UnknownSdk sdk) {
    String name = sdk.getSdkName();
    if (name == null) return ProjectBundle.message("unknown.sdk.with.no.name");
    return name;
  }
}
