package com.jetbrains.jsonSchema.impl;


import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.concurrent.ConcurrentMap;

public class JsonSchemaServiceImpl implements JsonSchemaService {
  private static final Logger LOGGER = Logger.getInstance(JsonSchemaServiceImpl.class);
  @Nullable
  private final Project myProject;
  private final ConcurrentMap<JsonSchemaFileProvider, JsonSchemaObjectCodeInsightWrapper> myWrappers = ContainerUtil.newConcurrentMap();

  public JsonSchemaServiceImpl(@Nullable Project project) {
    myProject = project;
  }

  @NotNull
  protected JsonSchemaProviderFactory[] getProviderFactories() {
    return JsonSchemaProviderFactory.EP_NAME.getExtensions();
  }


  @Nullable
  public Annotator getAnnotator(@Nullable VirtualFile file) {
    JsonSchemaObjectCodeInsightWrapper wrapper = getWrapper(file);
    return wrapper != null ? wrapper.getAnnotator() : null;
  }

  @Nullable
  public CompletionContributor getCompletionContributor(@Nullable VirtualFile file) {
    JsonSchemaObjectCodeInsightWrapper wrapper = getWrapper(file);
    return wrapper != null ? wrapper.getContributor() : null;
  }

  public boolean hasSchema(@Nullable VirtualFile file) {
    JsonSchemaObjectCodeInsightWrapper wrapper = getWrapper(file);
    return wrapper != null;
  }

  @Nullable
  @Override
  public DocumentationProvider getDocumentationProvider(@Nullable VirtualFile file) {
    JsonSchemaObjectCodeInsightWrapper wrapper = getWrapper(file);
    return wrapper != null ? wrapper.getDocumentationProvider() : null;
  }

  @Nullable
  private static JsonSchemaObjectCodeInsightWrapper createWrapper(@NotNull JsonSchemaFileProvider provide) {
    Reader reader = provide.getSchemaReader();
    try {
      if (reader != null) {
        JsonSchemaObject resultObject = new JsonSchemaReader().read(reader);
        return new JsonSchemaObjectCodeInsightWrapper(resultObject);
      }
    }
    catch (Exception e) {
      LOGGER.error("Error while processing json schema file: " + e.getMessage(), e);//todo logging level depending on internal/external schema
    }
    return null;
  }

  @Override
  public void reset() {
    myWrappers.clear();
  }

  @Nullable
  private JsonSchemaObjectCodeInsightWrapper getWrapper(@Nullable VirtualFile file) {
    if (file == null) return null;
    JsonSchemaProviderFactory[] factories = getProviderFactories();
    for (JsonSchemaProviderFactory factory : factories) {
      for (JsonSchemaFileProvider provider : factory.getProviders(myProject)) {
        if (provider.isAvailable(file)) {
          JsonSchemaObjectCodeInsightWrapper wrapper = myWrappers.get(provider);
          if (wrapper == null) {
            JsonSchemaObjectCodeInsightWrapper newWrapper = createWrapper(provider);
            if (newWrapper == null) return null;
            myWrappers.putIfAbsent(provider, newWrapper);
            wrapper = myWrappers.get(provider);
          }

          if (wrapper != null) {
            return wrapper;
          }
        }
      }
    }
    return null;
  }
}
