package com.jetbrains.jsonSchema.extension;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URL;
import java.util.List;

public interface JsonSchemaProviderFactory {
  ExtensionPointName<JsonSchemaProviderFactory> EP_NAME = ExtensionPointName.create("JavaScript.JsonSchema.ProviderFactory");
  Logger LOG = Logger.getInstance(JsonSchemaProviderFactory.class);

  List<JsonSchemaFileProvider> getProviders(@Nullable Project project);

  /**
   * Finds a {@link VirtualFile} instance corresponding to a specified resource path (relative or absolute).
   *
   * @param baseClass
   * @param resourcePath  String identifying a resource (relative or absolute)
   *                      See {@link Class#getResource(String)} for more details
   * @return VirtualFile instance, or null if not found
   */
  static VirtualFile getResourceFile(@NotNull Class baseClass, @NotNull String resourcePath) {
    URL url = baseClass.getResource(resourcePath);
    if (url == null) {
      LOG.error("Cannot find resource " + resourcePath);
      return null;
    }
    VirtualFile file = VfsUtil.findFileByURL(url);
    if (file == null) {
      LOG.error("Cannot find file by " + resourcePath);
      return null;
    }
    return file;
  }
}
