// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.navigation.JsonQualifiedNameKind;
import com.intellij.json.navigation.JsonQualifiedNameProvider;
import com.intellij.json.psi.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.AstLoadingFilter;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.JsonPointerUtil;
import com.jetbrains.jsonSchema.JsonSchemaCatalogEntry;
import com.jetbrains.jsonSchema.remote.JsonFileResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class JsonCachedValues {
  private static final Key<CachedValue<JsonSchemaObject>> JSON_OBJECT_CACHE_KEY = Key.create("JsonSchemaObjectCache");

  @Nullable
  public static JsonSchemaObject getSchemaObject(@NotNull VirtualFile schemaFile, @NotNull Project project) {
    JsonFileResolver.startFetchingHttpFileIfNeeded(schemaFile, project);
    return computeForFile(schemaFile, project, JsonCachedValues::computeSchemaObject, JSON_OBJECT_CACHE_KEY);
  }

  @Nullable
  private static JsonSchemaObject computeSchemaObject(@NotNull PsiFile f) {
    final JsonObject topLevelValue = AstLoadingFilter.forceAllowTreeLoading(
      f,
      () -> ObjectUtils.tryCast(((JsonFile)f).getTopLevelValue(), JsonObject.class));
    if (topLevelValue != null) {
      return new JsonSchemaReader().read(topLevelValue);
    }
    return null;
  }

  static final String URL_CACHE_KEY = "JsonSchemaUrlCache";
  private static final Key<CachedValue<String>> SCHEMA_URL_KEY = Key.create(URL_CACHE_KEY);
  @Nullable
  public static String getSchemaUrlFromSchemaProperty(@NotNull VirtualFile file,
                                                       @NotNull Project project) {
    String value = JsonSchemaFileValuesIndex.getCachedValue(project, file, URL_CACHE_KEY);
    if (value != null) {
      return JsonSchemaFileValuesIndex.NULL.equals(value) ? null : value;
    }

    PsiFile psiFile = resolveFile(file, project);
    return !(psiFile instanceof JsonFile) ? null : getOrCompute(psiFile, JsonCachedValues::fetchSchemaUrl, SCHEMA_URL_KEY);
  }

  private static PsiFile resolveFile(@NotNull VirtualFile file,
                                     @NotNull Project project) {
    if (project.isDisposed() || !file.isValid()) return null;
    return PsiManager.getInstance(project).findFile(file);
  }

  @Nullable
  static String fetchSchemaUrl(@Nullable PsiFile psiFile) {
    if (!(psiFile instanceof JsonFile)) return null;
    final String url = JsonSchemaFileValuesIndex.readTopLevelProps(psiFile.getFileType(), psiFile.getText()).get(URL_CACHE_KEY);
    return url == null || JsonSchemaFileValuesIndex.NULL.equals(url) ? null : url;
  }

  static final String ID_CACHE_KEY = "JsonSchemaIdCache";
  static final String OBSOLETE_ID_CACHE_KEY = "JsonSchemaObsoleteIdCache";
  private static final Key<CachedValue<String>> SCHEMA_ID_CACHE_KEY = Key.create(ID_CACHE_KEY);
  @Nullable
  public static String getSchemaId(@NotNull final VirtualFile schemaFile,
                                   @NotNull final Project project) {
    String value = JsonSchemaFileValuesIndex.getCachedValue(project, schemaFile, ID_CACHE_KEY);
    if (value != null && !JsonSchemaFileValuesIndex.NULL.equals(value)) return JsonPointerUtil.normalizeId(value);
    String obsoleteValue = JsonSchemaFileValuesIndex.getCachedValue(project, schemaFile, OBSOLETE_ID_CACHE_KEY);
    if (obsoleteValue != null && !JsonSchemaFileValuesIndex.NULL.equals(obsoleteValue)) return JsonPointerUtil.normalizeId(obsoleteValue);
    if (JsonSchemaFileValuesIndex.NULL.equals(value) || JsonSchemaFileValuesIndex.NULL.equals(obsoleteValue)) return null;

    final String result = computeForFile(schemaFile, project, JsonCachedValues::fetchSchemaId, SCHEMA_ID_CACHE_KEY);
    return result == null ? null : JsonPointerUtil.normalizeId(result);
  }

  @Nullable
  private static <T> T computeForFile(@NotNull final VirtualFile schemaFile,
                                      @NotNull final Project project,
                                      @NotNull Function<? super PsiFile, ? extends T> eval,
                                      @NotNull Key<CachedValue<T>> cacheKey) {
    final PsiFile psiFile = resolveFile(schemaFile, project);
    if (!(psiFile instanceof JsonFile)) return null;
    return getOrCompute(psiFile, eval, cacheKey);
  }

  static final String ID_PATHS_CACHE_KEY = "JsonSchemaIdToPointerCache";
  private static final Key<CachedValue<Map<String, String>>> SCHEMA_ID_PATHS_CACHE_KEY = Key.create(ID_PATHS_CACHE_KEY);
  public static Collection<String> getAllIdsInFile(PsiFile psiFile) {
    Map<String, String> map = getOrCompute(psiFile, JsonCachedValues::computeIdsMap, SCHEMA_ID_PATHS_CACHE_KEY);
    return map == null ? ContainerUtil.emptyList() : map.keySet();
  }
  @Nullable
  public static String resolveId(PsiFile psiFile, String id) {
    Map<String, String> map = getOrCompute(psiFile, JsonCachedValues::computeIdsMap, SCHEMA_ID_PATHS_CACHE_KEY);
    return map == null ? null : map.get(id);
  }

  private static Map<String, String> computeIdsMap(PsiFile file) {
    return SyntaxTraverser.psiTraverser(file).filter(JsonProperty.class).filter(p -> "$id".equals(p.getName()))
      .filter(p -> p.getValue() instanceof JsonStringLiteral)
      .toMap(p -> ((JsonStringLiteral)Objects.requireNonNull(p.getValue())).getValue(),
             p -> JsonQualifiedNameProvider.generateQualifiedName(p.getParent(), JsonQualifiedNameKind.JsonPointer));
  }

  @Nullable
  static String fetchSchemaId(@NotNull PsiFile psiFile) {
    if (!(psiFile instanceof JsonFile)) return null;
    final Map<String, String> props = JsonSchemaFileValuesIndex.readTopLevelProps(psiFile.getFileType(), psiFile.getText());
    final String id = props.get(ID_CACHE_KEY);
    if (id != null && !JsonSchemaFileValuesIndex.NULL.equals(id)) return id;
    final String obsoleteId = props.get(OBSOLETE_ID_CACHE_KEY);
    return obsoleteId == null || JsonSchemaFileValuesIndex.NULL.equals(obsoleteId) ? null : obsoleteId;
  }


  private static final Key<CachedValue<List<JsonSchemaCatalogEntry>>> SCHEMA_CATALOG_CACHE_KEY = Key.create("JsonSchemaCatalogCache");
  @Nullable
  public static List<JsonSchemaCatalogEntry> getSchemaCatalog(@NotNull final VirtualFile catalog,
                                   @NotNull final Project project) {
    if (!catalog.isValid()) return null;
    return computeForFile(catalog, project, JsonCachedValues::computeSchemaCatalog, SCHEMA_CATALOG_CACHE_KEY);
  }

  private static List<JsonSchemaCatalogEntry> computeSchemaCatalog(PsiFile catalog) {
    if (!catalog.isValid()) return null;
    JsonValue value = AstLoadingFilter.forceAllowTreeLoading(catalog, () -> ((JsonFile)catalog).getTopLevelValue());
    if (!(value instanceof JsonObject)) return null;

    JsonProperty schemas = ((JsonObject)value).findProperty("schemas");
    if (schemas == null) return null;

    JsonValue schemasValue = schemas.getValue();
    if (!(schemasValue instanceof JsonArray)) return null;
    List<JsonSchemaCatalogEntry> catalogMap = ContainerUtil.newArrayList();
    fillMap((JsonArray)schemasValue, catalogMap);
    return catalogMap;
  }

  private static void fillMap(@NotNull JsonArray array, @NotNull List<JsonSchemaCatalogEntry> catalogMap) {
    for (JsonValue value: array.getValueList()) {
      JsonObject obj = ObjectUtils.tryCast(value, JsonObject.class);
      if (obj == null) continue;
      JsonProperty fileMatch = obj.findProperty("fileMatch");
      Collection<String> masks = fileMatch == null ? ContainerUtil.emptyList() : resolveMasks(fileMatch.getValue());
      final String urlString = readStringValue(obj.findProperty("url"));
      if (urlString == null) continue;
      catalogMap.add(new JsonSchemaCatalogEntry(masks, urlString,
                                                readStringValue(obj.findProperty("name")),
                                                readStringValue(obj.findProperty("description"))));
    }
  }

  @Nullable
  private static String readStringValue(@Nullable JsonProperty property) {
    if (property == null) return null;
    JsonValue urlValue = property.getValue();
    if (urlValue instanceof JsonStringLiteral) {
      String urlStringValue = ((JsonStringLiteral)urlValue).getValue();
      if (!StringUtil.isEmpty(urlStringValue)) {
        return urlStringValue;
      }
    }
    return null;
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

  @Nullable
  private static <T> T getOrCompute(@NotNull PsiFile psiFile,
                                    @NotNull Function<? super PsiFile, ? extends T> eval,
                                    @NotNull Key<CachedValue<T>> key) {
    return CachedValuesManager.getCachedValue(psiFile, key, () -> CachedValueProvider.Result.create(eval.fun(psiFile), psiFile));
  }
}
