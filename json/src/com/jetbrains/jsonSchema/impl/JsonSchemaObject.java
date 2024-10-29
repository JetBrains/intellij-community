// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class JsonSchemaObject {
  @Override
  public boolean equals(@Nullable Object o) {
    if (o == null) return false;
    if (this == o) return true;
    if (this.getClass() != o.getClass()) return false;
    JsonSchemaObject object = (JsonSchemaObject)o;
    return Objects.equals(getFileUrl(), object.getFileUrl()) && Objects.equals(getPointer(), object.getPointer());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getFileUrl(), getPointer());
  }

  public abstract @Nullable Boolean getConstantSchema();

  @ApiStatus.Experimental
  public abstract boolean hasChildFieldsExcept(@NotNull String @NotNull ... namesToSkip);

  public abstract @NotNull Iterable<JsonSchemaValidation> getValidations(@Nullable JsonSchemaType type, @NotNull JsonValueAdapter value);

  public abstract @NotNull JsonSchemaObject getRootSchemaObject();

  public abstract boolean isValidByExclusion();

  public abstract @NotNull String getPointer();

  public abstract @Nullable String getFileUrl();

  public abstract @Nullable VirtualFile getRawFile();

  public abstract boolean hasPatternProperties();

  public abstract @Nullable JsonSchemaType getType();

  public abstract @Nullable Number getMultipleOf();

  public abstract @Nullable Number getMaximum();

  public abstract boolean isExclusiveMaximum();

  public abstract @Nullable Number getExclusiveMaximumNumber();

  public abstract @Nullable Number getExclusiveMinimumNumber();

  public abstract @Nullable Number getMinimum();

  public abstract boolean isExclusiveMinimum();

  public abstract @Nullable Integer getMaxLength();

  public abstract @Nullable Integer getMinLength();

  public abstract @Nullable String getPattern();

  public abstract boolean getAdditionalPropertiesAllowed();

  // for the sake of merging validation results, we need to know if this schema prohibits additional properties itself,
  // or if it inherits this prohibition flag from the merge result, as the behavior differs in these cases
  public abstract boolean hasOwnExtraPropertyProhibition();

  public abstract @Nullable JsonSchemaObject getPropertyNamesSchema();

  @ApiStatus.Experimental
  public abstract @Nullable JsonSchemaObject getAdditionalPropertiesSchema();

  @ApiStatus.Experimental
  public abstract @Nullable JsonSchemaObject getUnevaluatedPropertiesSchema();

  public abstract @Nullable Boolean getAdditionalItemsAllowed();

  public abstract @Nullable String getDeprecationMessage();

  public abstract @Nullable JsonSchemaObject getAdditionalItemsSchema();

  public abstract @Nullable JsonSchemaObject getItemsSchema();

  public abstract @Nullable JsonSchemaObject getUnevaluatedItemsSchema();

  public abstract @Nullable JsonSchemaObject getContainsSchema();

  public abstract @Nullable List<? extends JsonSchemaObject> getItemsSchemaList();

  public abstract @Nullable Integer getMaxItems();

  public abstract @Nullable Integer getMinItems();

  public abstract boolean isUniqueItems();

  public abstract @Nullable Integer getMaxProperties();

  public abstract @Nullable Integer getMinProperties();

  public abstract @Nullable Set<String> getRequired();

  public abstract @Nullable List<Object> getEnum();

  public abstract @Nullable JsonSchemaObject getNot();

  public abstract @Nullable List<IfThenElse> getIfThenElse();

  public abstract @Nullable Set<JsonSchemaType> getTypeVariants();

  public abstract @Nullable String getRef();

  public abstract boolean isRefRecursive();

  public abstract boolean isRecursiveAnchor();

  public abstract @Nullable Object getDefault();

  public abstract @Nullable JsonSchemaObject getExampleByName(@NotNull String name);

  public abstract @Nullable String getFormat();

  public abstract @Nullable String getId();

  public abstract @Nullable String getSchema();

  public abstract @Nullable String getDescription();

  public abstract @Nullable String getTitle();

  public abstract @Nullable JsonSchemaObject getMatchingPatternPropertySchema(@NotNull String name);

  public abstract boolean checkByPattern(@NotNull String value);

  public abstract @Nullable String getPatternError();

  public abstract @Nullable JsonSchemaObject findRelativeDefinition(@NotNull String ref);

  public abstract @Nullable Map<String, Map<String, String>> getEnumMetadata();

  public abstract @Nullable Map<String, List<String>> getPropertyDependencies();


  // Recently introduced methods that replace old inconvenient ones
  public abstract @Nullable JsonSchemaObject getDefinitionByName(@NotNull String name);

  public abstract @NotNull Iterator<String> getDefinitionNames();

  public abstract @Nullable String readChildNodeValue(@NotNull String @NotNull ... childNodeName);

  public abstract boolean hasChildNode(@NotNull String @NotNull ... childNodeName);

  public abstract @NotNull Iterator<String> getPropertyNames();

  public abstract @Nullable JsonSchemaObject getPropertyByName(@NotNull String name);

  public abstract @NotNull Iterator<String> getSchemaDependencyNames();

  public abstract @Nullable JsonSchemaObject getSchemaDependencyByName(@NotNull String name);

  // custom metadata provided by schemas, can be used in IDE features
  // the format in the schema is a key with either a single string value or an array of string values
  public abstract @Nullable List<JsonSchemaMetadataEntry> getMetadata();

  // also remove?
  public abstract @Nullable List<? extends JsonSchemaObject> getAllOf();

  public abstract @Nullable List<? extends JsonSchemaObject> getAnyOf();

  public abstract @Nullable List<? extends JsonSchemaObject> getOneOf();

  /**
   * @deprecated use {@link JsonSchemaObject#getSchemaDependencyNames} and {@link JsonSchemaObject#getSchemaDependencyByName}
   */
  @Deprecated
  public abstract @Nullable Map<String, ? extends JsonSchemaObject> getSchemaDependencies();

  // Previously static methods that were moved to utility class to avoid pollution of the JsonSchemaObject API class

  /**
   * @deprecated use {@link JsonSchemaObjectReadingUtils#getTypeDescription)}
   */
  @Deprecated
  public @Nullable String getTypeDescription(boolean shortDesc) {
    return JsonSchemaObjectReadingUtils.getTypeDescription(this, shortDesc);
  }

  /**
   * @deprecated use {@link JsonSchemaObjectReadingUtils#guessType}
   */
  @Deprecated(forRemoval = true)
  public @Nullable JsonSchemaType guessType() {
    return JsonSchemaObjectReadingUtils.guessType(this);
  }

  /**
   * @deprecated use {@link JsonSchemaObjectReadingUtils#hasStringChecks}
   */
  @Deprecated(forRemoval = true)
  public boolean hasStringChecks() {
    return JsonSchemaObjectReadingUtils.hasStringChecks(this);
  }

  /**
   * @deprecated use {@link JsonSchemaObjectReadingUtils#hasNumericChecks}
   */
  @Deprecated(forRemoval = true)
  public boolean hasNumericChecks() {
    return JsonSchemaObjectReadingUtils.hasNumericChecks(this);
  }

  /**
   * @deprecated use {@link JsonSchemaObjectReadingUtils#hasArrayChecks}
   */
  @Deprecated(forRemoval = true)
  public boolean hasArrayChecks() {
    return JsonSchemaObjectReadingUtils.hasArrayChecks(this);
  }

  /**
   * @deprecated use {@link JsonSchemaObjectReadingUtils#hasObjectChecks}
   */
  @Deprecated(forRemoval = true)
  public boolean hasObjectChecks() {
    return JsonSchemaObjectReadingUtils.hasObjectChecks(this);
  }

  // Candidates for removal

  /**
   * @deprecated use {@link JsonSchemaObject#readChildNodeValue)} with the corresponding sub-node path as a parameter
   */
  @Deprecated
  public abstract @Nullable String getHtmlDescription();

  /**
   * @deprecated use {@link JsonSchemaObject#getPropertyNames} and {@link JsonSchemaObject#getPropertyByName}
   */
  @ApiStatus.Internal
  @Deprecated
  public abstract @NotNull Map<String, ? extends JsonSchemaObject> getProperties();

  /**
   * @deprecated Do not use
   */
  @Deprecated
  public abstract @Nullable JsonSchemaObject getBackReference();

  /**
   * @deprecated use {@link JsonSchemaObject#getExampleByName}
   */
  @Deprecated
  public abstract @Nullable Map<String, Object> getExample();

  /**
   * @deprecated use {@link JsonSchemaObject#readChildNodeValue)} with the corresponding parameter
   */
  @Deprecated
  public abstract boolean isForceCaseInsensitive();

  /**
   * @deprecated use {@link JsonSchemaObject#readChildNodeValue)} with the corresponding parameter
   */
  @Deprecated
  public abstract @Nullable String getLanguageInjection();

  /**
   * @deprecated use {@link JsonSchemaObject#readChildNodeValue)} with the corresponding parameter
   */
  @Deprecated
  public abstract @Nullable String getLanguageInjectionPrefix();

  /**
   * @deprecated use {@link JsonSchemaObject#readChildNodeValue)} with the corresponding parameter
   */
  @Deprecated
  public abstract @Nullable String getLanguageInjectionPostfix();

  /**
   * @deprecated use {@link JsonSchemaObject#hasChildNode)} with the corresponding parameter
   */
  @Deprecated
  public abstract boolean isShouldValidateAgainstJSType();

  /**
   * @deprecated use {@link JsonSchemaObject#getDefinitionNames} and {@link JsonSchemaObject#getDefinitionByName}
   */
  @ApiStatus.Internal
  @Deprecated
  public abstract @Nullable Map<String, ? extends JsonSchemaObject> getDefinitionsMap();

  /**
   * @deprecated use {@link JsonSchemaObjectReadingUtils#resolveRefSchema}
   */
  @Deprecated
  public abstract @Nullable JsonSchemaObject resolveRefSchema(@NotNull JsonSchemaService service);

  /**
   * @deprecated Do not use
   */
  @ApiStatus.Internal
  @Deprecated
  public abstract @Nullable JsonSchemaType mergeTypes(@Nullable JsonSchemaType selfType,
                                                      @Nullable JsonSchemaType otherType,
                                                      @Nullable Set<JsonSchemaType> otherTypeVariants);

  /**
   * @deprecated Do not use
   */
  @ApiStatus.Internal
  @Deprecated
  public abstract Set<JsonSchemaType> mergeTypeVariantSets(@Nullable Set<JsonSchemaType> self, @Nullable Set<JsonSchemaType> other);

  /**
   * @deprecated Do not use
   */
  @ApiStatus.Internal
  @Deprecated
  public abstract void mergeValues(@NotNull JsonSchemaObject other);
}