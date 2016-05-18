package com.jetbrains.jsonSchema.ide;


import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.Consumer;
import com.intellij.util.containers.Convertor;
import com.jetbrains.jsonSchema.impl.JsonSchemaServiceEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface JsonSchemaService {

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

  boolean isSchemaFile(@NotNull VirtualFile file, @NotNull Consumer<String> errorConsumer);

  boolean isRegisteredSchemaFile(Project project, @NotNull VirtualFile file);

  @Nullable
  DocumentationProvider getDocumentationProvider(@Nullable VirtualFile file);

  @Nullable
  Convertor<String, PsiElement> getToPropertyResolver(@Nullable VirtualFile file);

  @Nullable
  List<Pair<Boolean, String>> getMatchingSchemaDescriptors(@Nullable VirtualFile file);

  boolean hasSchema(@Nullable VirtualFile file);

  void reset();
}
