package com.jetbrains.jsonSchema.ide;


import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.impl.JsonSchemaServiceEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface JsonSchemaService {

  @NotNull
  Collection<VirtualFile> getSchemaFilesForFile(@NotNull VirtualFile file);

  class Impl {
    public static JsonSchemaService get(@NotNull Project project) {
      return ServiceManager.getService(project, JsonSchemaService.class);
    }
    public static JsonSchemaServiceEx getEx(@NotNull Project project) {
      return (JsonSchemaServiceEx) get(project);
    }
  }

  @Nullable
  Annotator getAnnotator(@Nullable VirtualFile file);

  @Nullable
  CompletionContributor getCompletionContributor(@Nullable VirtualFile file);

  @Nullable
  DocumentationProvider getDocumentationProvider(@Nullable VirtualFile file);

  @Nullable
  JsonSchemaFileProvider getSchemaProvider(@NotNull final VirtualFile schemaFile);

  void reset();
}
