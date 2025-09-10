// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.json.JsonBundle;
import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.jsonSchema.extension.JsonErrorPriority;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.JsonValidationHost;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.jetbrains.jsonSchema.impl.light.SchemaKeywordsKt.INSTANCE_OF;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywordsKt.TYPE_OF;

public final class JsonSchemaAnnotatorChecker implements JsonValidationHost {
  private static final Set<JsonSchemaType> PRIMITIVE_TYPES =
    Set.of(JsonSchemaType._integer, JsonSchemaType._number, JsonSchemaType._boolean, JsonSchemaType._string, JsonSchemaType._null);
  private final Map<PsiElement, JsonValidationError> myErrors;
  private final @NotNull Project myProject;
  private final @NotNull JsonComplianceCheckerOptions myOptions;
  private boolean myHadTypeError;

  public JsonSchemaAnnotatorChecker(@NotNull Project project, @NotNull JsonComplianceCheckerOptions options) {
    myProject = project;
    myOptions = options;
    myErrors = new HashMap<>();
  }

  public JsonSchemaAnnotatorChecker(@NotNull JsonSchemaAnnotatorChecker oldChecker, Map<PsiElement, JsonValidationError> errors) {
    myProject = oldChecker.myProject;
    myOptions = oldChecker.myOptions;
    myErrors = errors;
  }

  @Override
  public @NotNull Map<PsiElement, JsonValidationError> getErrors() {
    return myErrors;
  }

  public boolean isHadTypeError() {
    return myHadTypeError;
  }

  public static JsonSchemaAnnotatorChecker checkByMatchResult(@NotNull Project project,
                                                              @NotNull JsonValueAdapter elementToCheck,
                                                              final @NotNull MatchResult result,
                                                              @NotNull JsonComplianceCheckerOptions options) {
    final List<JsonSchemaAnnotatorChecker> checkers = new ArrayList<>();
    if (result.myExcludingSchemas.isEmpty() && result.mySchemas.size() == 1) {
      final JsonSchemaAnnotatorChecker checker = new JsonSchemaAnnotatorChecker(project, options);
      checker.checkByScheme(elementToCheck, result.mySchemas.iterator().next());
      checkers.add(checker);
    }
    else {
      if (!result.mySchemas.isEmpty()) {
        checkers.add(processSchemasVariants(project, result.mySchemas, elementToCheck, false, options).getSecond());
      }
      if (!result.myExcludingSchemas.isEmpty()) {
        // we can have several oneOf groups, each about, for instance, a part of properties
        // - then we should allow properties from neighbour schemas (even if additionalProperties=false)
        final List<JsonSchemaAnnotatorChecker> list =
          ContainerUtil.map(result.myExcludingSchemas, group -> {
            ProgressManager.checkCanceled();
            return processSchemasVariants(project, group, elementToCheck, true, options).getSecond();
          });
        checkers.add(mergeErrors(project, list, options, result.myExcludingSchemas));
      }
    }
    if (checkers.isEmpty()) return null;
    if (checkers.size() == 1) return checkers.get(0);

    return checkers.stream()
      .filter(checker -> !checker.isHadTypeError())
      .findFirst()
      .orElse(checkers.get(0));
  }

  private static JsonSchemaAnnotatorChecker mergeErrors(@NotNull Project project,
                                                        @NotNull List<JsonSchemaAnnotatorChecker> list,
                                                        @NotNull JsonComplianceCheckerOptions options,
                                                        @NotNull List<Collection<? extends JsonSchemaObject>> excludingSchemas) {
    final JsonSchemaAnnotatorChecker checker = new JsonSchemaAnnotatorChecker(project, options);

    for (JsonSchemaAnnotatorChecker ch: list) {
      for (Map.Entry<PsiElement, JsonValidationError> element: ch.myErrors.entrySet()) {
        JsonValidationError error = element.getValue();
        if (error.getFixableIssueKind() == JsonValidationError.FixableIssueKind.ProhibitedProperty) {
          String propertyName = ((JsonValidationError.ProhibitedPropertyIssueData)error.getIssueData()).propertyName;
          boolean skip = false;
          for (Collection<? extends JsonSchemaObject> objects : excludingSchemas) {
            var propExists = objects.stream()
              .filter(o -> !o.hasOwnExtraPropertyProhibition())
              .anyMatch(obj -> obj.getPropertyByName(propertyName) != null);
            if (propExists) skip = true;
          }
          if (skip) continue;
        }
        checker.myErrors.put(element.getKey(), error);
      }
    }
    return checker;
  }

  @Override
  public void error(@InspectionMessage String error, final PsiElement holder,
                    JsonErrorPriority priority) {
    error(error, holder, JsonValidationError.FixableIssueKind.None, null, priority);
  }

  @Override
  public void error(final PsiElement newHolder, JsonValidationError error) {
    error(error.getMessage(), newHolder, error.getFixableIssueKind(), error.getIssueData(), error.getPriority());
  }

  @Override
  public void error(@InspectionMessage String error, final PsiElement holder,
                    JsonValidationError.FixableIssueKind fixableIssueKind,
                    JsonValidationError.IssueData data,
                    JsonErrorPriority priority) {
    if (myErrors.containsKey(holder)) return;
    myErrors.put(holder, new JsonValidationError(error, fixableIssueKind, data, priority));
  }

  @Override
  public void typeError(final @NotNull PsiElement value, @Nullable JsonSchemaType currentType, final JsonSchemaType @NotNull ... allowedTypes) {
    if (allowedTypes.length == 0) return;
    String currentTypeDesc = currentType == null ? "" : (" " + JsonBundle.message("schema.validation.actual") + currentType.getName() + ".");
    String prefix = JsonBundle.message("schema.validation.incompatible.types") + "\n";
    if (allowedTypes.length == 1) {
      error(prefix + " " + JsonBundle.message("schema.validation.required.one", allowedTypes[0].getName(), currentTypeDesc), value,
            JsonValidationError.FixableIssueKind.ProhibitedType,
            new JsonValidationError.TypeMismatchIssueData(allowedTypes),
            JsonErrorPriority.TYPE_MISMATCH);
    } else {
      final String typesText = Arrays.stream(allowedTypes)
                                     .map(JsonSchemaType::getName)
                                     .distinct()
                                     .sorted(Comparator.naturalOrder())
                                     .collect(Collectors.joining(", "));
      error(prefix + " " + JsonBundle.message("schema.validation.required.one.of", typesText, currentTypeDesc), value,
            JsonValidationError.FixableIssueKind.ProhibitedType,
            new JsonValidationError.TypeMismatchIssueData(allowedTypes),
            JsonErrorPriority.TYPE_MISMATCH);
    }
    myHadTypeError = true;
  }

  @Override
  public MatchResult resolve(JsonSchemaObject schemaObject, @Nullable JsonValueAdapter inspectedElementAdapter) {
    return new JsonSchemaResolver(myProject, schemaObject, new JsonPointerPosition(), inspectedElementAdapter).detailedResolve();
  }

  @Override
  public @Nullable JsonValidationHost checkByMatchResult(JsonValueAdapter adapter,
                                                         MatchResult result,
                                                         JsonComplianceCheckerOptions options) {
    return checkByMatchResult(myProject, adapter, result, options);
  }

  @Override
  public boolean isValid() {
    return myErrors.isEmpty() && !myHadTypeError;
  }

  public boolean checkByScheme(@NotNull JsonValueAdapter value, @NotNull JsonSchemaObject schema) {
    final JsonSchemaType instanceFieldType = JsonSchemaType.getType(value);

    var isValid = true;
    for (JsonSchemaValidation validation : schema.getValidations(instanceFieldType, value)) {
      isValid &= validation.validate(value, schema, instanceFieldType, this, myOptions);
      if (!isValid && myOptions.shouldStopValidationAfterAnyErrorFound()) return false;
    }
    return isValid;
  }

  @Override
  public void checkObjectBySchemaRecordErrors(@NotNull JsonSchemaObject schema, @NotNull JsonValueAdapter object) {
    checkObjectBySchemaRecordErrors(schema, object, new JsonPointerPosition());
  }

  public void checkObjectBySchemaRecordErrors(@NotNull JsonSchemaObject schema, @NotNull JsonValueAdapter object, @NotNull JsonPointerPosition position) {
    final JsonSchemaAnnotatorChecker checker = checkByMatchResult(myProject,
                                                                  object,
                                                                  new JsonSchemaResolver(myProject, schema, position, object).detailedResolve(),
                                                                  myOptions);
    if (checker != null) {
      myHadTypeError = checker.isHadTypeError();
      myErrors.putAll(checker.getErrors());
    }
  }

  @Override
  public void addErrorsFrom(JsonValidationHost otherHost) {
    this.myErrors.putAll(((JsonSchemaAnnotatorChecker)otherHost).myErrors);
  }

  @Override
  public boolean hasRecordedErrorsFor(@NotNull JsonValueAdapter inspectedValueAdapter) {
    return myErrors.containsKey(inspectedValueAdapter.getDelegate());
  }

  private static @NotNull Pair<JsonSchemaObject, JsonSchemaAnnotatorChecker> processSchemasVariants(
    @NotNull Project project, final @NotNull Collection<? extends JsonSchemaObject> collection,
    final @NotNull JsonValueAdapter value, boolean isOneOf, JsonComplianceCheckerOptions options) {

    final JsonSchemaAnnotatorChecker checker = new JsonSchemaAnnotatorChecker(project, options);
    final JsonSchemaType type = JsonSchemaType.getType(value);
    JsonSchemaObject selected = null;
    if (type == null) {
      if (!value.isShouldBeIgnored()) checker.typeError(value.getDelegate(), null, getExpectedTypes(collection));
    }
    else {
      final List<JsonSchemaObject> filtered = new ArrayList<>(collection.size());
      JsonSchemaType altType = value.getAlternateType(type);
      for (JsonSchemaObject schema: collection) {
        if (!areSchemaTypesCompatible(schema, type)
            && !areSchemaTypesCompatible(schema, altType)) continue;
        filtered.add(schema);
      }
      if (filtered.isEmpty()) {
        checker.typeError(value.getDelegate(), altType, getExpectedTypes(collection));
      }
      else if (filtered.size() == 1) {
        selected = filtered.get(0);
        checker.checkByScheme(value, selected);
      }
      else {
        if (isOneOf) {
          selected = checker.processOneOf(value, filtered);
        }
        else {
          selected = checker.processAnyOf(value, filtered);
        }
      }
    }
    return Pair.create(selected, checker);
  }

  private static final JsonSchemaType[] NO_TYPES = new JsonSchemaType[0];
  public static JsonSchemaType[] getExpectedTypes(final Collection<? extends JsonSchemaObject> schemas) {
    final List<JsonSchemaType> list = new ArrayList<>();
    for (JsonSchemaObject schema : schemas) {
      final JsonSchemaType type = schema.getType();
      if (type != null) {
        list.add(type);
      } else {
        final Set<JsonSchemaType> variants = schema.getTypeVariants();
        if (variants != null) {
          list.addAll(variants);
        }
      }
    }
    return list.isEmpty() ? NO_TYPES : list.toArray(NO_TYPES);
  }

  public static boolean areSchemaTypesCompatible(final @NotNull JsonSchemaObject schema, final @NotNull JsonSchemaType type) {
    final JsonSchemaType matchingSchemaType = getMatchingSchemaType(schema, type);
    if (matchingSchemaType != null) return matchingSchemaType.equals(type);
    if (schema.getEnum() != null) {
      return PRIMITIVE_TYPES.contains(type);
    }
    return true;
  }

  public static @Nullable JsonSchemaType getMatchingSchemaType(@NotNull JsonSchemaObject schema, @Nullable JsonSchemaType input) {
    final JsonSchemaType matchType = schema.getType();
    if (matchType != null) {
      if (JsonSchemaType._integer.equals(input) && JsonSchemaType._number.equals(matchType)) {
        return input;
      }
      if (JsonSchemaType._string_number.equals(input) && (JsonSchemaType._number.equals(matchType)
                                                          || JsonSchemaType._integer.equals(matchType)
                                                          || JsonSchemaType._string.equals(matchType))) {
        return input;
      }
      return matchType;
    }
    if (schema.getTypeVariants() != null) {
      Set<JsonSchemaType> matchTypes = schema.getTypeVariants();
      if (matchTypes.contains(input)) {
        return input;
      }
      if (JsonSchemaType._integer.equals(input) && matchTypes.contains(JsonSchemaType._number)) {
        return input;
      }
      if (JsonSchemaType._string_number.equals(input) &&
          (matchTypes.contains(JsonSchemaType._number)
           || matchTypes.contains(JsonSchemaType._integer)
           || matchTypes.contains(JsonSchemaType._string))) {
        return input;
      }
      //nothing matches, lets return one of the list so that other heuristics does not match
      return matchTypes.iterator().next();
    }
    if (JsonSchemaObjectReadingUtils.hasProperties(schema) && JsonSchemaType._object.equals(input)) return JsonSchemaType._object;
    return null;
  }

  public static @Nullable String getValue(PsiElement propValue, JsonSchemaObject schema) {
    final JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(propValue, schema);
    assert walker != null;
    JsonValueAdapter adapter = walker.createValueAdapter(propValue);
    if (adapter != null && !adapter.shouldCheckAsValue()) return null;
    return walker.getNodeTextForValidation(propValue);
  }

  // returns the schema, selected for annotation
  private JsonSchemaObject processOneOf(@NotNull JsonValueAdapter value, List<JsonSchemaObject> oneOf) {
    final List<JsonSchemaAnnotatorChecker> candidateErroneousCheckers = new ArrayList<>();
    final List<JsonSchemaObject> candidateErroneousSchemas = new ArrayList<>();
    final List<JsonSchemaObject> correct = new SmartList<>();
    for (JsonSchemaObject object : oneOf) {
      // skip it if something JS awaited, we do not process it currently
      if (object.hasChildNode(INSTANCE_OF) || object.hasChildNode(TYPE_OF) ||object.isShouldValidateAgainstJSType()) continue;

      final JsonSchemaAnnotatorChecker checker = new JsonSchemaAnnotatorChecker(myProject, myOptions);
      checker.checkByScheme(value, object);

      if (checker.isCorrect()) {
        candidateErroneousCheckers.clear();
        candidateErroneousSchemas.clear();
        correct.add(object);
      }
      else {
        candidateErroneousCheckers.add(checker);
        candidateErroneousSchemas.add(object);
      }
    }
    if (correct.size() == 1) return correct.get(0);
    if (!correct.isEmpty()) {
      final JsonSchemaType type = JsonSchemaType.getType(value);
      if (type != null) {
        // also check maybe some currently not checked properties like format are different with schemes
        // todo note that JsonSchemaObject#equals is broken by design, so normally it shouldn't be used until rewritten
        //  but for now we use it here to avoid similar schemas being marked as duplicates
        if (new HashSet<>(correct).size() > 1 && !schemesDifferWithNotCheckedProperties(correct)) {
          error(JsonBundle.message("schema.validation.to.more.than.one"), value.getDelegate(), JsonErrorPriority.MEDIUM_PRIORITY);
        }
      }
      return ContainerUtil.getLastItem(correct);
    }

    return showErrorsAndGetLeastErroneous(candidateErroneousCheckers, candidateErroneousSchemas, true);
  }

  private static boolean schemesDifferWithNotCheckedProperties(final @NotNull List<JsonSchemaObject> list) {
    return list.stream().anyMatch(s -> !StringUtil.isEmptyOrSpaces(s.getFormat()));
  }

  private enum AverageFailureAmount {
    Light,
    MissingItems,
    Medium,
    Hard,
    NotSchema
  }

  private static @NotNull AverageFailureAmount getAverageFailureAmount(@NotNull JsonSchemaAnnotatorChecker checker) {
    int lowPriorityCount = 0;
    boolean hasMedium = false;
    boolean hasMissing = false;
    boolean hasHard = false;
    Collection<JsonValidationError> values = checker.getErrors().values();
    for (JsonValidationError value: values) {
      switch (value.getPriority()) {
        case LOW_PRIORITY -> lowPriorityCount++;
        case MISSING_PROPS -> hasMissing = true;
        case MEDIUM_PRIORITY -> hasMedium = true;
        case TYPE_MISMATCH -> hasHard = true;
        case NOT_SCHEMA -> {
          return AverageFailureAmount.NotSchema;
        }
      }
    }

    if (hasHard) {
      return AverageFailureAmount.Hard;
    }

    // missing props should win against other conditions
    if (hasMissing) {
      return AverageFailureAmount.MissingItems;
    }

    if (hasMedium) {
      return AverageFailureAmount.Medium;
    }

    return lowPriorityCount <= 3 ? AverageFailureAmount.Light : AverageFailureAmount.Medium;
  }

  // returns the schema, selected for annotation
  private JsonSchemaObject processAnyOf(@NotNull JsonValueAdapter value, List<JsonSchemaObject> anyOf) {
    final List<JsonSchemaAnnotatorChecker> candidateErroneousCheckers = new ArrayList<>();
    final List<JsonSchemaObject> candidateErroneousSchemas = new ArrayList<>();

    for (JsonSchemaObject object : anyOf) {
      final JsonSchemaAnnotatorChecker checker = new JsonSchemaAnnotatorChecker(myProject, myOptions);
      checker.checkByScheme(value, object);
      if (checker.isCorrect()) {
        return object;
      }
      // maybe we still find the correct schema - continue to iterate
      candidateErroneousCheckers.add(checker);
      candidateErroneousSchemas.add(object);
    }

    return showErrorsAndGetLeastErroneous(candidateErroneousCheckers, candidateErroneousSchemas, false);
  }

  /**
   * Filters schema validation results to get the result with the "minimal" amount of errors.
   * This is needed in case of oneOf or anyOf conditions, when there exist no match.
   * I.e., when we have multiple schema candidates, but none is applicable.
   * In this case we need to show the most "suitable" error messages
   *   - by detecting the most "likely" schema corresponding to the current entity
   */
  private @Nullable JsonSchemaObject showErrorsAndGetLeastErroneous(@NotNull List<JsonSchemaAnnotatorChecker> candidateErroneousCheckers,
                                                          @NotNull List<JsonSchemaObject> candidateErroneousSchemas,
                                                          boolean isOneOf) {
    JsonSchemaObject current = null;
    JsonSchemaObject currentWithMinAverage = null;
    Optional<AverageFailureAmount> minAverage = candidateErroneousCheckers.stream()
                                                                          .map(c -> getAverageFailureAmount(c))
                                                                          .min(Comparator.comparingInt(c -> c.ordinal()));
    int min = minAverage.orElse(AverageFailureAmount.Hard).ordinal();

    int minErrorCount = candidateErroneousCheckers.stream().map(c -> c.getErrors().size()).min(Integer::compareTo).orElse(Integer.MAX_VALUE);

    MultiMap<PsiElement, JsonValidationError> errorsWithMinAverage = new MultiMap<>();
    MultiMap<PsiElement, JsonValidationError> allErrors = new MultiMap<>();
    for (int i = 0; i < candidateErroneousCheckers.size(); i++) {
      JsonSchemaAnnotatorChecker checker = candidateErroneousCheckers.get(i);
      final boolean isMoreThanMinErrors = checker.getErrors().size() > minErrorCount;
      final boolean isMoreThanAverage = getAverageFailureAmount(checker).ordinal() > min;
      if (!isMoreThanMinErrors) {
        if (isMoreThanAverage) {
          currentWithMinAverage = candidateErroneousSchemas.get(i);
        }
        else {
          current = candidateErroneousSchemas.get(i);
        }

        for (Map.Entry<PsiElement, JsonValidationError> entry: checker.getErrors().entrySet()) {
          (isMoreThanAverage ? errorsWithMinAverage : allErrors).putValue(entry.getKey(), entry.getValue());
        }
      }
    }

    if (allErrors.isEmpty()) allErrors = errorsWithMinAverage;

    for (Map.Entry<PsiElement, Collection<JsonValidationError>> entry : allErrors.entrySet()) {
      Collection<JsonValidationError> value = entry.getValue();
      if (value.isEmpty()) continue;
      if (value.size() == 1) {
        error(entry.getKey(), value.iterator().next());
        continue;
      }
      JsonValidationError error = tryMergeErrors(value, isOneOf);
      if (error != null) {
        error(entry.getKey(), error);
      }
      else {
        for (JsonValidationError validationError : value) {
          error(entry.getKey(), validationError);
        }
      }
    }

    if (current == null) {
      current = currentWithMinAverage;
    }
    if (current == null) {
      current = ContainerUtil.getLastItem(candidateErroneousSchemas);
    }

    return current;
  }

  private static @Nullable JsonValidationError tryMergeErrors(@NotNull Collection<JsonValidationError> errors, boolean isOneOf) {
    JsonValidationError.FixableIssueKind commonIssueKind = null;
    for (JsonValidationError error : errors) {
      JsonValidationError.FixableIssueKind currentIssueKind = error.getFixableIssueKind();
      if (currentIssueKind == JsonValidationError.FixableIssueKind.None) return null;
      else if (commonIssueKind == null) commonIssueKind = currentIssueKind;
      else if (currentIssueKind != commonIssueKind) return null;
    }

    if (commonIssueKind == JsonValidationError.FixableIssueKind.NonEnumValue) {
      String prefix = JsonBundle.message("schema.validation.enum.mismatch", "");
      @NlsSafe String text = errors.stream()
        // todo remove this ugly textual cutting
        .map(e -> StringUtil.trimEnd(StringUtil.trimStart(e.getMessage(), prefix), prefix) /*ltr and rtl*/)
        .map(e -> StringUtil.split(e, ", "))
        .flatMap(e -> e.stream())
        .distinct()
        .sorted()
        .collect(Collectors.joining(", "));
      return new JsonValidationError(prefix + text, commonIssueKind, null, errors.iterator().next().getPriority());
    }

    if (commonIssueKind == JsonValidationError.FixableIssueKind.MissingProperty) {
      String sets = errors.stream().map(e -> (JsonValidationError.MissingMultiplePropsIssueData)e.getIssueData())
        .map(d -> d.getMessage(false)).collect(NlsMessages.joiningOr());
      return new JsonValidationError(JsonBundle.message(
        isOneOf ? "schema.validation.one.of.property.sets.required" : "schema.validation.at.least.one.of.property.sets.required", sets),
                                     isOneOf ? JsonValidationError.FixableIssueKind.MissingOneOfProperty : JsonValidationError.FixableIssueKind.MissingAnyOfProperty,
                                     new JsonValidationError.MissingOneOfPropsIssueData(
                                       ContainerUtil.map(errors, e -> (JsonValidationError.MissingMultiplePropsIssueData)e.getIssueData())), errors.iterator().next().getPriority());
    }

    if (commonIssueKind == JsonValidationError.FixableIssueKind.ProhibitedType) {
      final Set<JsonSchemaType> allTypes = errors.stream().map(e -> (JsonValidationError.TypeMismatchIssueData)e.getIssueData())
        .flatMap(d -> Arrays.stream(d.expectedTypes)).collect(Collectors.toSet());

      if (allTypes.size() == 1) return errors.iterator().next();

      List<String> actualInfos = errors.stream().map(e -> e.getMessage()).map(JsonSchemaAnnotatorChecker::fetchActual).distinct().toList();
      String actualInfo = actualInfos.size() == 1 ? (" " + JsonBundle.message("schema.validation.actual") + actualInfos.get(0) + ".") : "";
      String commonTypeMessage = JsonBundle.message("schema.validation.incompatible.types") + "\n" +
                                 JsonBundle.message("schema.validation.required.one.of",
                                                    allTypes.stream().map(t -> t.getDescription()).sorted().collect(Collectors.joining(", ")),
                                                    actualInfo);
      return new JsonValidationError(commonTypeMessage, JsonValidationError.FixableIssueKind.TypeMismatch,
                                     new JsonValidationError.TypeMismatchIssueData(ContainerUtil.toArray(allTypes, JsonSchemaType[]::new)),
                                     errors.iterator().next().getPriority());
    }

    return null;
  }

  private static String fetchActual(String message) {
    String actualMessage = JsonBundle.message("schema.validation.actual");
    int actual = message.indexOf(actualMessage);
    if (actual == -1) return null;
    String substring = message.endsWith(actualMessage) ? message.substring(0, actual) : message.substring(actual + actualMessage.length());
    return StringUtil.trimEnd(substring, ".");
  }

  public boolean isCorrect() {
    return myErrors.isEmpty();
  }
}
