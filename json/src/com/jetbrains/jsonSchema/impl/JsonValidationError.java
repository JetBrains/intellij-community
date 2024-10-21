// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.json.JsonBundle;
import com.intellij.openapi.util.NlsSafe;
import com.jetbrains.jsonSchema.extension.JsonErrorPriority;
import com.jetbrains.jsonSchema.extension.JsonLikeSyntaxAdapter;
import com.jetbrains.jsonSchema.impl.fixes.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public final class JsonValidationError {

  public IssueData getIssueData() {
    return myIssueData;
  }

  public JsonErrorPriority getPriority() {
    return myPriority;
  }

  public enum FixableIssueKind {
    MissingProperty,
    MissingOptionalProperty,
    MissingOneOfProperty,
    MissingAnyOfProperty,
    ProhibitedProperty,
    NonEnumValue,
    ProhibitedType,
    TypeMismatch,
    DuplicateArrayItem,
    None
  }

  public interface IssueData {

  }

  public static final class DuplicateArrayItemIssueData implements IssueData {
    public final int[] duplicateIndices;
    public DuplicateArrayItemIssueData(int[] indices) { duplicateIndices = indices; }
  }

  public static final class MissingOneOfPropsIssueData implements IssueData {
    public final Collection<MissingMultiplePropsIssueData> myExclusiveOptions;

    public MissingOneOfPropsIssueData(Collection<MissingMultiplePropsIssueData> options) {
      myExclusiveOptions = options;
    }
  }

  public static final class MissingMultiplePropsIssueData implements IssueData {
    public final Collection<MissingPropertyIssueData> myMissingPropertyIssues;

    public MissingMultiplePropsIssueData(Collection<MissingPropertyIssueData> missingPropertyIssues) {
      myMissingPropertyIssues = missingPropertyIssues;
    }

    private static String getPropertyNameWithComment(MissingPropertyIssueData prop) {
      String comment = "";
      if (prop.enumItemsCount == 1) {
        comment = " = " + prop.defaultValue.toString();
      }
      return "'" + prop.propertyName + "'" + comment;
    }

    public String getMessage(boolean trimIfNeeded) {
      if (myMissingPropertyIssues.size() == 1) {
        MissingPropertyIssueData prop = myMissingPropertyIssues.iterator().next();
        return JsonBundle.message("schema.validation.property", getPropertyNameWithComment(prop));
      }

      Collection<MissingPropertyIssueData> namesToDisplay = myMissingPropertyIssues;
      boolean trimmed = false;
      if (trimIfNeeded && namesToDisplay.size() > 3) {
        namesToDisplay = new ArrayList<>();
        Iterator<MissingPropertyIssueData> iterator = myMissingPropertyIssues.iterator();
        for (int i = 0; i < 3; i++) {
          namesToDisplay.add(iterator.next());
        }
        trimmed = true;
      }
      String allNames = myMissingPropertyIssues.stream().map(
        MissingMultiplePropsIssueData::getPropertyNameWithComment).sorted((s1, s2) -> {
        boolean firstHasEq = s1.contains("=");
        boolean secondHasEq = s2.contains("=");
        if (firstHasEq == secondHasEq) {
          return s1.compareTo(s2);
        }
        return firstHasEq ? -1 : 1;
      }).collect(Collectors.joining(", "));
      if (trimmed) allNames += ", ...";
      return JsonBundle.message("schema.validation.properties", allNames);
    }
  }

  public static final class MissingPropertyIssueData implements IssueData {
    public final String propertyName;
    public final JsonSchemaType propertyType;
    public final Object defaultValue;
    public final int enumItemsCount;

    public MissingPropertyIssueData(String propertyName, JsonSchemaType propertyType, Object defaultValue, int enumItemsCount) {
      this.propertyName = propertyName;
      this.propertyType = propertyType;
      this.defaultValue = defaultValue;
      this.enumItemsCount = enumItemsCount;
    }
  }

  public static final class ProhibitedPropertyIssueData implements IssueData {
    public final @NlsSafe String propertyName;
    public final List<@NlsSafe String> typoCandidates;

    public ProhibitedPropertyIssueData(@NlsSafe String propertyName, List<@NlsSafe String> typoCandidates) {
      this.propertyName = propertyName;
      this.typoCandidates = typoCandidates;
    }
  }

  public static final class TypeMismatchIssueData implements IssueData {
    public final JsonSchemaType[] expectedTypes;

    public TypeMismatchIssueData(JsonSchemaType[] expectedTypes) {
      this.expectedTypes = expectedTypes;
    }
  }

  private final @InspectionMessage String myMessage;
  private final FixableIssueKind myFixableIssueKind;
  private final IssueData myIssueData;
  private final JsonErrorPriority myPriority;

  public JsonValidationError(@InspectionMessage String message, FixableIssueKind fixableIssueKind, IssueData issueData,
                             JsonErrorPriority priority) {
    myMessage = message;
    myFixableIssueKind = fixableIssueKind;
    myIssueData = issueData;
    myPriority = priority;
  }

  public @InspectionMessage String getMessage() {
    return myMessage;
  }

  public FixableIssueKind getFixableIssueKind() {
    return myFixableIssueKind;
  }

  public LocalQuickFix @NotNull [] createFixes(@Nullable JsonLikeSyntaxAdapter quickFixAdapter) {
    if (quickFixAdapter == null) return LocalQuickFix.EMPTY_ARRAY;
    return switch (myFixableIssueKind) {
      case MissingProperty ->
        new AddMissingPropertyFix[]{new AddMissingPropertyFix((MissingMultiplePropsIssueData)myIssueData, quickFixAdapter)};
      case MissingOneOfProperty, MissingAnyOfProperty ->
        ((MissingOneOfPropsIssueData)myIssueData).myExclusiveOptions.stream().map(d -> new AddMissingPropertyFix(d, quickFixAdapter))
          .toArray(LocalQuickFix[]::new);
      case ProhibitedProperty -> getProhibitedPropertyFixes(quickFixAdapter);
      case NonEnumValue -> new SuggestEnumValuesFix[]{new SuggestEnumValuesFix(quickFixAdapter)};
      case DuplicateArrayItem -> new RemoveDuplicateArrayItemsFix[]{ new RemoveDuplicateArrayItemsFix(
        ((DuplicateArrayItemIssueData)myIssueData).duplicateIndices
      ) };
      default -> LocalQuickFix.EMPTY_ARRAY;
    };
  }

  private LocalQuickFix @NotNull [] getProhibitedPropertyFixes(@NotNull JsonLikeSyntaxAdapter quickFixAdapter) {
    ProhibitedPropertyIssueData data = (ProhibitedPropertyIssueData)myIssueData;
    if (data.typoCandidates.isEmpty()) {
      return new RemoveProhibitedPropertyFix[]{new RemoveProhibitedPropertyFix(data, quickFixAdapter)};
    }
    ArrayList<LocalQuickFix> allFixes = new ArrayList<>();
    for (@NlsSafe String candidate : data.typoCandidates) {
      allFixes.add(new FixPropertyNameTypoFix(candidate, quickFixAdapter));
    }
    allFixes.add(new RemoveProhibitedPropertyFix(data, quickFixAdapter));
    return allFixes.toArray(LocalQuickFix[]::new);
  }
}
