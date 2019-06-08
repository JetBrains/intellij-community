// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.conversion;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author nik
 */
public abstract class ConversionService {
  @NotNull
  public static ConversionService getInstance() {
    ConversionService service = ServiceManager.getService(ConversionService.class);
    return service == null ? new DummyConversionService() : service;
  }

  @NotNull
  public abstract ConversionResult convertSilently(@NotNull String projectPath);

  @NotNull
  public abstract ConversionResult convertSilently(@NotNull String projectPath, @NotNull ConversionListener conversionListener);

  @NotNull
  public abstract ConversionResult convert(@NotNull VirtualFile projectPath);

  @NotNull
  public abstract ConversionResult convertModule(@NotNull Project project, @NotNull File moduleFile);

  public abstract void saveConversionResult(@NotNull String projectPath);
}
