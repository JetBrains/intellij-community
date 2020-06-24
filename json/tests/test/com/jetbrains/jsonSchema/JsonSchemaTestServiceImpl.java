// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory;
import com.jetbrains.jsonSchema.impl.JsonSchemaServiceImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class JsonSchemaTestServiceImpl extends JsonSchemaServiceImpl {
  private static JsonSchemaFileProvider provider;

  public static void setProvider(JsonSchemaFileProvider newProvider) {
    provider = newProvider;
  }

  public JsonSchemaTestServiceImpl(@NotNull Project project) {
    super(project);
  }

  @NotNull
  @Override
  protected List<JsonSchemaProviderFactory> getProviderFactories() {
    return Collections.singletonList(new JsonSchemaProviderFactory() {
      @NotNull
      @Override
      public List<JsonSchemaFileProvider> getProviders(@NotNull final Project project) {
        return ContainerUtil.createMaybeSingletonList(provider);
      }
    });
  }

  @Override
  public int hashCode() {
    return Objects.hash(JsonSchemaTestServiceImpl.class, "test");
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof JsonSchemaTestServiceImpl) return true;
    return super.equals(obj);
  }
}
