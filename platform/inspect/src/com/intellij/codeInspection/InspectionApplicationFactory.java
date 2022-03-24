// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public interface InspectionApplicationFactory {
  ExtensionPointName<InspectionApplicationFactory> EP_NAME = ExtensionPointName.create("com.intellij.inspectionApplicationFactory");

  @NotNull
  String id();

  InspectionApplicationBase getApplication(@NotNull List<String> args);

  @NotNull
  static InspectionApplicationBase getApplication(@NotNull String id, @NotNull List<String> args) {
    for (InspectionApplicationFactory extension : EP_NAME.getExtensions()) {
      if (extension.id().equals(id)) {
        return extension.getApplication(args);
      }
    }
    throw new IllegalArgumentException("There is no loaded inspect engine with id= '" + id + "'. Please check loaded plugin list.");
  }
}