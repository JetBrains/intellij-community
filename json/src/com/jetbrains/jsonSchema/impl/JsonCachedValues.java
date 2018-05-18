// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.psi.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.remote.JsonFileResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class JsonCachedValues {
  private static final Key<CachedValue<JsonSchemaObject>> JSON_OBJECT_CACHE_KEY = Key.create("JsonSchemaObjectCache");
  @Nullable
  public static JsonSchemaObject getSchemaObject(@NotNull VirtualFile schemaFile, @NotNull Project project) {
    JsonFileResolver.startFetchingHttpFileIfNeeded(schemaFile, project);
    final PsiFile psiFile = resolveFile(schemaFile, project);
    if (!(psiFile instanceof JsonFile)) return null;

    return CachedValueProviderOnPsiFile.getOrCompute(psiFile, JsonCachedValues::computeSchemaObject, JSON_OBJECT_CACHE_KEY);
  }

  @Nullable
  private static JsonSchemaObject computeSchemaObject(@NotNull PsiFile f) {
    final JsonObject topLevelValue = ObjectUtils.tryCast(((JsonFile)f).getTopLevelValue(), JsonObject.class);
    if (topLevelValue != null) {
      return new JsonSchemaReader().read(topLevelValue);
    }
    return null;
  }

  private static final Key<CachedValue<String>> SCHEMA_URL_KEY = Key.create("JsonSchemaUrlCache");
  @Nullable
  public static String getSchemaUrlFromSchemaProperty(@NotNull VirtualFile file,
                                                       @NotNull Project project) {
    PsiFile psiFile = resolveFile(file, project);
    return !(psiFile instanceof JsonFile) ? null : CachedValueProviderOnPsiFile
      .getOrCompute(psiFile, JsonCachedValues::fetchSchemaUrl, SCHEMA_URL_KEY);
  }

  private static PsiFile resolveFile(@NotNull VirtualFile file,
                                     @NotNull Project project) {
    if (project.isDisposed() || !file.isValid()) return null;
    return PsiManager.getInstance(project).findFile(file);
  }

  @Nullable
  private static String fetchSchemaUrl(@Nullable PsiFile f) {
    if (!(f instanceof JsonFile)) return null;

    JsonValue topLevelValue = ((JsonFile)f).getTopLevelValue();
    if (!(topLevelValue instanceof JsonObject)) return null;
    JsonProperty schema = ((JsonObject)topLevelValue).findProperty("$schema");
    if (schema == null) return null;

    JsonValue value = schema.getValue();
    return value instanceof JsonStringLiteral ? ((JsonStringLiteral)value).getValue() : null;
  }

  private static final Key<CachedValue<String>> SCHEMA_ID_CACHE_KEY = Key.create("JsonSchemaIdCache");
  @Nullable
  public static String getSchemaId(@NotNull final VirtualFile schemaFile,
                                   @NotNull final Project project) {
    if (!schemaFile.isValid()) return null;
    final PsiFile psiFile = resolveFile(schemaFile, project);
    if (!(psiFile instanceof JsonFile)) return null;
    return CachedValueProviderOnPsiFile.getOrCompute(psiFile, JsonCachedValues::getSchemaId, SCHEMA_ID_CACHE_KEY);
  }

  @Nullable
  private static String getSchemaId(@NotNull PsiFile psiFile) {
    final JsonObject topLevelValue = ObjectUtils.tryCast(((JsonFile)psiFile).getTopLevelValue(), JsonObject.class);
    return topLevelValue == null ? null : readId(topLevelValue);
  }

  @Nullable
  private static String readId(@NotNull final JsonObject object) {
    String idPropertyV6 = readIdProperty(object, "$id");
    if (idPropertyV6 != null) return idPropertyV6;
    return readIdProperty(object, "id");
  }

  @Nullable
  private static String readIdProperty(@NotNull JsonObject object, @NotNull String id) {
    final JsonProperty property = object.findProperty(id);
    if (property != null && property.getValue() instanceof JsonStringLiteral) {
      return JsonSchemaService.normalizeId(StringUtil.unquoteString(property.getValue().getText()));
    }
    return null;
  }

  private static final Key<CachedValue<List<Pair<Collection<String>, String>>>> SCHEMA_CATALOG_CACHE_KEY = Key.create("JsonSchemaCatalogCache");
  @Nullable
  public static List<Pair<Collection<String>, String>> getSchemaCatalog(@NotNull final VirtualFile catalog,
                                   @NotNull final Project project) {
    if (!catalog.isValid()) return null;
    final PsiFile psiFile = resolveFile(catalog, project);
    if (!(psiFile instanceof JsonFile)) return null;
    return CachedValueProviderOnPsiFile.getOrCompute(psiFile, JsonCachedValues::computeSchemaCatalog, SCHEMA_CATALOG_CACHE_KEY);
  }

  private static List<Pair<Collection<String>, String>> computeSchemaCatalog(PsiFile catalog) {
    JsonValue value = ((JsonFile)catalog).getTopLevelValue();
    if (!(value instanceof JsonObject)) return null;

    JsonProperty schemas = ((JsonObject)value).findProperty("schemas");
    if (schemas == null) return null;

    JsonValue schemasValue = schemas.getValue();
    if (!(schemasValue instanceof JsonArray)) return null;
    List<Pair<Collection<String>, String>> catalogMap = ContainerUtil.newArrayList();
    fillMap((JsonArray)schemasValue, catalogMap);
    return catalogMap;
  }

  private static void fillMap(@NotNull JsonArray array, @NotNull List<Pair<Collection<String>, String>> catalogMap) {
    for (JsonValue value: array.getValueList()) {
      if (!(value instanceof JsonObject)) continue;
      JsonProperty fileMatch = ((JsonObject)value).findProperty("fileMatch");
      if (fileMatch == null) continue;
      Collection<String> masks = resolveMasks(fileMatch.getValue());

        JsonProperty url = ((JsonObject)value).findProperty("url");
        if (url != null) {
          JsonValue urlValue = url.getValue();
          if (urlValue instanceof JsonStringLiteral) {
            String urlStringValue = ((JsonStringLiteral)urlValue).getValue();
            if (!StringUtil.isEmpty(urlStringValue)) {
              catalogMap.add(Pair.create(masks, urlStringValue));
            }
          }
        }
    }
  }

  @NotNull
  private static Collection<String> resolveMasks(@Nullable JsonValue value) {
    if (value instanceof JsonStringLiteral) {
      return ContainerUtil.createMaybeSingletonList(((JsonStringLiteral)value).getValue());
    }

    if (value instanceof JsonArray) {
      List<String> strings = ContainerUtil.newArrayList();
      for (JsonValue val: ((JsonArray)value).getValueList()) {
        if (val instanceof JsonStringLiteral) {
          strings.add(((JsonStringLiteral)val).getValue());
        }
      }
      return strings;
    }

    return ContainerUtil.emptyList();
  }
}
