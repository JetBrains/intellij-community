// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.impl.fixes.AddMissingPropertyFix;
import com.jetbrains.jsonSchema.impl.fixes.RemoveProhibitedPropertyFix;
import com.jetbrains.jsonSchema.impl.fixes.SuggestEnumValuesFix;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Iterator;
import java.util.stream.Collectors;

public class JsonValidationError {

  public IssueData getIssueData() {
    return myIssueData;
  }

  public enum FixableIssueKind {
    MissingProperty,
    ProhibitedProperty,
    NonEnumValue,
    ProhibitedType,
    TypeMismatch,
    None
  }

  public interface IssueData {

  }

  public static class MissingMultiplePropsIssueData implements IssueData {
    public final Collection<MissingPropertyIssueData> myMissingPropertyIssues;

    public MissingMultiplePropsIssueData(Collection<MissingPropertyIssueData> missingPropertyIssues) {
      myMissingPropertyIssues = missingPropertyIssues;
    }

    public String getMessage(boolean trimIfNeeded) {
      if (myMissingPropertyIssues.size() == 1) {
        return "property '" + myMissingPropertyIssues.iterator().next().propertyName + "'";
      }

      Collection<MissingPropertyIssueData> namesToDisplay = myMissingPropertyIssues;
      boolean trimmed = false;
      if (trimIfNeeded && namesToDisplay.size() > 3) {
        namesToDisplay = ContainerUtil.newArrayList();
        Iterator<MissingPropertyIssueData> iterator = myMissingPropertyIssues.iterator();
        for (int i = 0; i < 3; i++) {
          namesToDisplay.add(iterator.next());
        }
        trimmed = true;
      }
      String allNames = myMissingPropertyIssues.stream().map(p -> "'" + p.propertyName + "'").collect(Collectors.joining(", "));
      if (trimmed) allNames += ", ...";
      return "properties " + allNames;
    }
  }

  public static class MissingPropertyIssueData implements IssueData {
    public final String propertyName;
    public final JsonSchemaType propertyType;
    public final Object defaultValue;
    public final boolean hasEnumItems;

    public MissingPropertyIssueData(String propertyName, JsonSchemaType propertyType, Object defaultValue, boolean hasEnumItems) {
      this.propertyName = propertyName;
      this.propertyType = propertyType;
      this.defaultValue = defaultValue;
      this.hasEnumItems = hasEnumItems;
    }
  }

  public static class ProhibitedPropertyIssueData implements IssueData {
    public final String propertyName;

    public ProhibitedPropertyIssueData(String propertyName) {
      this.propertyName = propertyName;
    }
  }

  public static class TypeMismatchIssueData implements IssueData {
    public final JsonSchemaType[] expectedTypes;

    public TypeMismatchIssueData(JsonSchemaType[] expectedTypes) {
      this.expectedTypes = expectedTypes;
    }
  }

  private final String myMessage;
  private final FixableIssueKind myFixableIssueKind;
  private final IssueData myIssueData;

  public JsonValidationError(String message, FixableIssueKind fixableIssueKind, IssueData issueData) {
    myMessage = message;
    myFixableIssueKind = fixableIssueKind;
    myIssueData = issueData;
  }

  public String getMessage() {
    return myMessage;
  }

  public FixableIssueKind getFixableIssueKind() {
    return myFixableIssueKind;
  }

  @Nullable
  public LocalQuickFix createFix() {
    switch (myFixableIssueKind) {
      case MissingProperty:
        return new AddMissingPropertyFix((MissingMultiplePropsIssueData)myIssueData);
      case ProhibitedProperty:
        return new RemoveProhibitedPropertyFix((ProhibitedPropertyIssueData)myIssueData);
      case NonEnumValue:
        return new SuggestEnumValuesFix();
      default:
        return null;
    }
  }
}
