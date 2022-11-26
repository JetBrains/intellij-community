// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.json.JsonBundle;
import com.intellij.openapi.util.NlsSafe;
import com.jetbrains.jsonSchema.extension.JsonErrorPriority;
import com.jetbrains.jsonSchema.extension.JsonLikeSyntaxAdapter;
import com.jetbrains.jsonSchema.impl.fixes.AddMissingPropertyFix;
import com.jetbrains.jsonSchema.impl.fixes.RemoveProhibitedPropertyFix;
import com.jetbrains.jsonSchema.impl.fixes.SuggestEnumValuesFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.stream.Collectors;

public class JsonValidationError {

  public IssueData getIssueData() {
    return myIssueData;
  }

  public JsonErrorPriority getPriority() {
    return myPriority;
  }

  public enum FixableIssueKind {
    MissingProperty,
    MissingOneOfProperty,
    MissingAnyOfProperty,
    ProhibitedProperty,
    NonEnumValue,
    ProhibitedType,
    TypeMismatch,
    None
  }

  public interface IssueData {

  }

  public static class MissingOneOfPropsIssueData implements IssueData {
    public final Collection<MissingMultiplePropsIssueData> myExclusiveOptions;

    public MissingOneOfPropsIssueData(Collection<MissingMultiplePropsIssueData> options) {
      myExclusiveOptions = options;
    }
  }

  public static class MissingMultiplePropsIssueData implements IssueData {
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

  public static class MissingPropertyIssueData implements IssueData {
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

  public static class ProhibitedPropertyIssueData implements IssueData {
    public final @NlsSafe String propertyName;

    public ProhibitedPropertyIssueData(@NlsSafe String propertyName) {
      this.propertyName = propertyName;
    }
  }

  public static class TypeMismatchIssueData implements IssueData {
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
      case ProhibitedProperty ->
        new RemoveProhibitedPropertyFix[]{new RemoveProhibitedPropertyFix((ProhibitedPropertyIssueData)myIssueData, quickFixAdapter)};
      case NonEnumValue -> new SuggestEnumValuesFix[]{new SuggestEnumValuesFix(quickFixAdapter)};
      default -> LocalQuickFix.EMPTY_ARRAY;
    };
  }
}
