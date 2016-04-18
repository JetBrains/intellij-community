package com.jetbrains.jsonSchema.extension;


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

  List<JsonSchemaFileProvider> getProviders(@Nullable Project project);

  static VirtualFile getResourceFile(@NotNull Class baseClass, @NotNull String resourcePath) {
    final ClassLoader loader = baseClass.getClassLoader();
    final URL resource = loader.getResource(resourcePath);
    assert resource != null;

    final VirtualFile file = VfsUtil.findFileByURL(resource);
    assert file != null;
    return file;
  }
}
