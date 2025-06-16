// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.legacy;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.openapi.vfs.impl.http.RemoteFileInfo;
import com.intellij.openapi.vfs.impl.http.RemoteFileState;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.FactoryMap;
import com.jetbrains.jsonSchema.fus.JsonSchemaFusCountedFeature;
import com.jetbrains.jsonSchema.fus.JsonSchemaHighlightingSessionStatisticsCollector;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.*;
import com.jetbrains.jsonSchema.remote.JsonFileResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.jetbrains.jsonSchema.JsonPointerUtil.*;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywordsKt.*;

public final class JsonSchemaObjectReadingUtils {
  private static final Logger LOG = Logger.getInstance(JsonSchemaObjectReadingUtils.class);
  public static final @NotNull JsonSchemaObject NULL_OBJ = new JsonSchemaObjectImpl("$_NULL_$");

  public static boolean hasProperties(@NotNull JsonSchemaObject schemaObject) {
    return schemaObject.getPropertyNames().hasNext();
  }


  /**
   * @deprecated Use {@link  com.jetbrains.jsonSchema.impl.light.JsonSchemaRefResolverKt#resolveRefSchema}
   */
  @Deprecated()
  public static @Nullable JsonSchemaObject resolveRefSchema(@NotNull JsonSchemaObject schemaNode, @NotNull JsonSchemaService service) {
    final String ref = schemaNode.getRef();
    assert !StringUtil.isEmptyOrSpaces(ref);

    if (schemaNode instanceof JsonSchemaObjectImpl schemaImpl) {
      var refsStorage = schemaImpl.getComputedRefsStorage(service.getProject());
      var schemaObject = refsStorage.getOrDefault(ref, NULL_OBJ);
      if (schemaObject != NULL_OBJ) return schemaObject;
    }

    var value = fetchSchemaFromRefDefinition(ref, schemaNode, service, schemaNode.isRefRecursive());
    if (!JsonFileResolver.isHttpPath(ref)) {
      service.registerReference(ref);
    }
    else if (value != null) {
      // our aliases - if http ref actually refers to a local file with specific ID
      VirtualFile virtualFile = service.resolveSchemaFile(value);
      if (virtualFile != null && !(virtualFile instanceof HttpVirtualFile)) {
        service.registerReference(virtualFile.getName());
      }
    }

    if (schemaNode instanceof JsonSchemaObjectImpl schemaImpl && value instanceof JsonSchemaObjectImpl valueImpl) {
      if (value != NULL_OBJ && !Objects.equals(value.getFileUrl(), schemaNode.getFileUrl())) {
        valueImpl.setBackReference(schemaImpl);
      }
      schemaImpl.getComputedRefsStorage(service.getProject()).put(ref, value);
    }
    return value;
  }

  private static final Map<String, JsonSchemaVariantsTreeBuilder.SchemaUrlSplitter> complexReferenceCache
    = FactoryMap.create((key) -> new JsonSchemaVariantsTreeBuilder.SchemaUrlSplitter(key));

  public static @Nullable JsonSchemaObject fetchSchemaFromRefDefinition(@NotNull String ref,
                                                                        final @NotNull JsonSchemaObject schema,
                                                                        @NotNull JsonSchemaService service,
                                                                        boolean recursive) {

    final VirtualFile schemaFile = service.resolveSchemaFile(schema);
    if (schemaFile == null) return null;
    final JsonSchemaVariantsTreeBuilder.SchemaUrlSplitter splitter;
    if (Registry.is("json.schema.object.v2")) {
      splitter = complexReferenceCache.get(ref);
    }
    else {
      splitter = new JsonSchemaVariantsTreeBuilder.SchemaUrlSplitter(ref);
    }
    String schemaId = splitter.getSchemaId();
    if (schemaId != null) {
      var refSchema = resolveSchemaByReference(service, schemaFile, schemaId);
      if (refSchema == null || refSchema == NULL_OBJ) return null;
      return findRelativeDefinition(refSchema, splitter, service);
    }
    var rootSchema = service.getSchemaObjectForSchemaFile(schemaFile);
    if (rootSchema == null) {
      LOG.debug(String.format("Schema object not found for %s", schemaFile.getPath()));
      return null;
    }
    if (recursive && ref.startsWith("#")) {
      while (rootSchema.isRecursiveAnchor()) {
        var backRef = rootSchema.getBackReference();
        if (backRef == null) break;
        VirtualFile file = ObjectUtils.coalesce(backRef.getRawFile(),
                                                backRef.getFileUrl() == null ? null : JsonFileResolver.urlToFile(backRef.getFileUrl()));
        if (file == null) break;
        try {
          rootSchema = JsonSchemaReader.readFromFile(service.getProject(), file);
        }
        catch (Exception e) {
          break;
        }
      }
    }
    return findRelativeDefinition(rootSchema, splitter, service);
  }

  private static @Nullable JsonSchemaObject resolveSchemaByReference(@NotNull JsonSchemaService service,
                                                                     @NotNull VirtualFile schemaFile,
                                                                     @NotNull String schemaId) {
    final VirtualFile refFile = service.findSchemaFileByReference(schemaId, schemaFile);
    if (refFile == null) {
      LOG.debug(String.format("Schema file not found by reference: '%s' from %s", schemaId, schemaFile.getPath()));
      return null;
    }
    var refSchema = downloadAndParseRemoteSchema(service, refFile);
    if (refSchema == null) {
      LOG.debug(String.format("Schema object not found by reference: '%s' from %s", schemaId, schemaFile.getPath()));
    }
    return refSchema;
  }

  public static @Nullable JsonSchemaObject downloadAndParseRemoteSchema(@NotNull JsonSchemaService service, @NotNull VirtualFile refFile) {
    if (refFile instanceof HttpVirtualFile) {
      RemoteFileInfo info = ((HttpVirtualFile)refFile).getFileInfo();
      if (info != null) {
        RemoteFileState state = info.getState();
        if (state == RemoteFileState.DOWNLOADING_NOT_STARTED) {
          JsonSchemaHighlightingSessionStatisticsCollector.getInstance().reportSchemaUsageFeature(JsonSchemaFusCountedFeature.ExecutedHttpVirtualFileDownloadRequest);
          JsonFileResolver.startFetchingHttpFileIfNeeded(refFile, service.getProject());
          return NULL_OBJ;
        }
        else if (state == RemoteFileState.DOWNLOADING_IN_PROGRESS) {
          return NULL_OBJ;
        }
      }
    }
    return service.getSchemaObjectForSchemaFile(refFile);
  }

  public static JsonSchemaObject findRelativeDefinition(final @NotNull JsonSchemaObject schema,
                                                        final @NotNull JsonSchemaVariantsTreeBuilder.SchemaUrlSplitter splitter,
                                                        @NotNull JsonSchemaService service) {
    final String path = splitter.getRelativePath();
    if (StringUtil.isEmptyOrSpaces(path)) {
      final String id = splitter.getSchemaId();
      if (isSelfReference(id)) {
        return schema;
      }
      if (id != null && id.startsWith("#")) {
        JsonSchemaObject rootSchemaObject = schema.getRootSchemaObject();
        if (rootSchemaObject instanceof RootJsonSchemaObject<?, ?> explicitRootSchemaObject) {
          final String resolvedId = explicitRootSchemaObject.resolveId(id);
          if (resolvedId == null || id.equals("#" + resolvedId)) return null;
          return findRelativeDefinition(schema, new JsonSchemaVariantsTreeBuilder.SchemaUrlSplitter("#" + resolvedId), service);
        }
      }
      return schema;
    }
    final JsonSchemaObject definition = findRelativeDefinition(schema, path);
    if (definition == null) {
      if (LOG.isDebugEnabled()) {
        VirtualFile schemaFile = service.resolveSchemaFile(schema);
        String debugMessage = String.format("Definition not found by reference: '%s' in file %s",
                                            path, schemaFile == null ? "(no file)" : schemaFile.getPath());
        LOG.debug(debugMessage);
      }
    }
    return definition;
  }

  public static boolean hasArrayChecks(@NotNull JsonSchemaObject schemaObject) {
    return schemaObject.isUniqueItems()
           || schemaObject.getContainsSchema() != null
           || schemaObject.getItemsSchema() != null
           || schemaObject.getItemsSchemaList() != null
           || schemaObject.getMinItems() != null
           || schemaObject.getMaxItems() != null;
  }

  public static boolean hasObjectChecks(@NotNull JsonSchemaObject schemaObject) {
    return hasProperties(schemaObject)
           || schemaObject.getPropertyNamesSchema() != null
           || schemaObject.getPropertyDependencies() != null
           || schemaObject.hasPatternProperties()
           || schemaObject.getRequired() != null
           || schemaObject.getMinProperties() != null
           || schemaObject.getMaxProperties() != null;
  }

  public static boolean hasNumericChecks(@NotNull JsonSchemaObject schemaObject) {
    return schemaObject.getMultipleOf() != null
           || schemaObject.getExclusiveMinimumNumber() != null
           || schemaObject.getExclusiveMaximumNumber() != null
           || schemaObject.getMaximum() != null
           || schemaObject.getMinimum() != null;
  }

  public static boolean hasStringChecks(@NotNull JsonSchemaObject schemaObject) {
    return schemaObject.getPattern() != null || schemaObject.getFormat() != null;
  }

  public static @Nullable JsonSchemaType guessType(@NotNull JsonSchemaObject schemaObject) {
    // if we have an explicit type, here we are
    JsonSchemaType type = schemaObject.getType();
    if (type != null) return type;

    // process type variants before heuristic type detection
    final Set<JsonSchemaType> typeVariants = schemaObject.getTypeVariants();
    if (typeVariants != null) {
      final int size = typeVariants.size();
      if (size == 1) {
        return typeVariants.iterator().next();
      }
      else if (size >= 2) {
        return null;
      }
    }

    // heuristic type detection based on the set of applied constraints
    boolean hasObjectChecks = hasObjectChecks(schemaObject);
    boolean hasNumericChecks = hasNumericChecks(schemaObject);
    boolean hasStringChecks = hasStringChecks(schemaObject);
    boolean hasArrayChecks = hasArrayChecks(schemaObject);

    if (hasObjectChecks && !hasNumericChecks && !hasStringChecks && !hasArrayChecks) {
      return JsonSchemaType._object;
    }
    if (!hasObjectChecks && hasNumericChecks && !hasStringChecks && !hasArrayChecks) {
      return JsonSchemaType._number;
    }
    if (!hasObjectChecks && !hasNumericChecks && hasStringChecks && !hasArrayChecks) {
      return JsonSchemaType._string;
    }
    if (!hasObjectChecks && !hasNumericChecks && !hasStringChecks && hasArrayChecks) {
      return JsonSchemaType._array;
    }
    return null;
  }

  public static @Nullable String getTypesDescription(boolean shortDesc, @Nullable Collection<JsonSchemaType> possibleTypes) {
    if (possibleTypes == null || possibleTypes.isEmpty()) return null;
    if (possibleTypes.size() == 1) return possibleTypes.iterator().next().getDescription();
    if (possibleTypes.contains(JsonSchemaType._any)) return JsonSchemaType._any.getDescription();

    Stream<String> typeDescriptions = possibleTypes.stream().map(t -> t.getDescription()).distinct().sorted();
    boolean isShort = false;
    if (shortDesc) {
      typeDescriptions = typeDescriptions.limit(3);
      if (possibleTypes.size() > 3) isShort = true;
    }
    return typeDescriptions.collect(Collectors.joining(" | ", "", isShort ? "| ..." : ""));
  }

  public static @Nullable String getTypeDescription(@NotNull JsonSchemaObject schemaObject, boolean shortDesc) {
    JsonSchemaType type = schemaObject.getType();
    if (type != null) return type.getDescription();

    Set<JsonSchemaType> possibleTypes = schemaObject.getTypeVariants();

    String description = getTypesDescription(shortDesc, possibleTypes);
    if (description != null) return description;

    List<Object> anEnum = schemaObject.getEnum();
    if (anEnum != null) {
      return shortDesc ? "enum" : anEnum.stream().map(o -> o.toString()).collect(Collectors.joining(" | "));
    }

    JsonSchemaType guessedType = guessType(schemaObject);
    if (guessedType != null) {
      return guessedType.getDescription();
    }

    return null;
  }

  public static @Nullable JsonSchemaObject findRelativeDefinition(@NotNull JsonSchemaObject schemaObject, @NotNull String ref) {
    if (isSelfReference(ref)) {
      return schemaObject;
    }
    if (!ref.startsWith("#/")) {
      return null;
    }
    if (Registry.is("json.schema.object.v2") && !(schemaObject instanceof JsonSchemaObjectImpl)) {
      return schemaObject.findRelativeDefinition(ref);
    }
    ref = ref.substring(2);
    final List<String> parts = split(ref);
    JsonSchemaObject current = schemaObject;
    for (int i = 0; i < parts.size(); i++) {
      if (current == null) return null;
      final String part = parts.get(i);
      if (JSON_DEFINITIONS.equals(part) || DEFS.equals(part)) {
        if (i == (parts.size() - 1)) return null;
        //noinspection AssignmentToForLoopParameter
        final String nextPart = parts.get(++i);
        current = current.getDefinitionByName(unescapeJsonPointerPart(nextPart));
        continue;
      }
      if (JSON_PROPERTIES.equals(part)) {
        if (i == (parts.size() - 1)) return null;
        //noinspection AssignmentToForLoopParameter
        current = current.getPropertyByName(unescapeJsonPointerPart(parts.get(++i)));
        continue;
      }
      if (ITEMS.equals(part)) {
        if (i == (parts.size() - 1)) {
          current = current.getItemsSchema();
        }
        else {
          //noinspection AssignmentToForLoopParameter
          Integer next = tryParseInt(parts.get(++i));
          var itemsSchemaList = current.getItemsSchemaList();
          if (itemsSchemaList != null && next != null && next < itemsSchemaList.size()) {
            current = itemsSchemaList.get(next);
          }
        }
        continue;
      }
      if (ADDITIONAL_ITEMS.equals(part)) {
        if (i == (parts.size() - 1)) {
          current = current.getAdditionalItemsSchema();
        }
        continue;
      }

      current = current.getDefinitionByName(part);
    }
    return current;
  }

  private static @Nullable Integer tryParseInt(String s) {
    try {
      return Integer.parseInt(s);
    }
    catch (Exception __) {
      return null;
    }
  }

  public static boolean matchPattern(final @NotNull Pattern pattern, final @NotNull String s) {
    try {
      return pattern.matcher(StringUtil.newBombedCharSequence(s, 300)).matches();
    }
    catch (ProcessCanceledException e) {
      // something wrong with the pattern, infinite cycle?
      Logger.getInstance(JsonSchemaObjectReadingUtils.class).info("Pattern matching canceled");
      return false;
    }
    catch (Exception e) {
      // catch exceptions around to prevent things like:
      // https://bugs.openjdk.org/browse/JDK-6984178
      Logger.getInstance(JsonSchemaObjectReadingUtils.class).info(e);
      return false;
    }
  }

  public static Pair<Pattern, String> compilePattern(final @NotNull String pattern) {
    try {
      return Pair.create(Pattern.compile(adaptSchemaPattern(pattern)), null);
    }
    catch (PatternSyntaxException e) {
      return Pair.create(null, e.getMessage());
    }
  }

  private static @NotNull String adaptSchemaPattern(String pattern) {
    pattern = pattern.startsWith("^") || pattern.startsWith("*") || pattern.startsWith(".") ? pattern : (".*" + pattern);
    pattern = pattern.endsWith("+") || pattern.endsWith("*") || pattern.endsWith("$") ? pattern : (pattern + ".*");
    pattern = pattern.replace("\\\\", "\\");
    return pattern;
  }
}
