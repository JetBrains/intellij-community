package com.jetbrains.jsonSchema.ide;


import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

public interface JsonSchemaService {

  class Impl {

    @Nullable
    public static JsonSchemaService get(@NotNull Project project) {
      return ServiceManager.getService(project, JsonSchemaService.class);
    }

  }

  @Nullable
  Annotator getAnnotator(@Nullable VirtualFile file);

  @Nullable
  CompletionContributor getCompletionContributor(@Nullable VirtualFile file);

  boolean isSchemaFile(@NotNull File file, @NotNull Consumer<String> errorConsumer);

  @Nullable
  DocumentationProvider getDocumentationProvider(@Nullable VirtualFile file);

  @Nullable
  List<Pair<Boolean, String>> getMatchingSchemaDescriptors(@Nullable VirtualFile file);

  boolean hasSchema(@Nullable VirtualFile file);

  void reset();
}
