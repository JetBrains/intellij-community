// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.extension;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URL;
import java.util.List;

/**
 * Implement to contribute JSON Schemas for particular JSON documents to enable validation/completion based on JSON Schema.
 */
public interface JsonSchemaProviderFactory extends PossiblyDumbAware {
  ExtensionPointName<JsonSchemaProviderFactory> EP_NAME = ExtensionPointName.create("JavaScript.JsonSchema.ProviderFactory");
  Logger LOG = Logger.getInstance(JsonSchemaProviderFactory.class);

  /**
   * Called in smart mode by default. Implement {@link com.intellij.openapi.project.DumbAware} to be called in dumb mode.
   */
  @NotNull List<JsonSchemaFileProvider> getProviders(@NotNull Project project);

  /**
   * Finds a {@link VirtualFile} instance corresponding to a specified resource path (relative or absolute).
   *
   * @param resourcePath  String identifying a resource (relative or absolute)
   *                      See {@link Class#getResource(String)} for more details
   * @return VirtualFile instance, or null if not found
   */
  static @Nullable VirtualFile getResourceFile(@NotNull Class<?> baseClass, @NonNls @NotNull String resourcePath) {
    URL url = baseClass.getResource(resourcePath);
    if (url == null) {
      LOG.error("Cannot find resource " + resourcePath);
      return null;
    }
    VirtualFile file = VfsUtil.findFileByURL(url);
    if (file != null) {
      return file;
    }
    LOG.info("File not found by " + url + ", performing refresh...");
    ApplicationManager.getApplication().invokeLaterOnWriteThread(() -> {
      VirtualFile refreshed = WriteAction.compute(() -> {
        return VirtualFileManager.getInstance().refreshAndFindFileByUrl(VfsUtilCore.convertFromUrl(url));
      });
      if (refreshed != null) {
        LOG.info("Refreshed " + url + " successfully");
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
          JsonSchemaService service = project.getService(JsonSchemaService.class);
          service.reset();
        }
      }
      else {
        LOG.error("Cannot refresh and find file by " + resourcePath);
      }
    });
    return null;
  }
}
