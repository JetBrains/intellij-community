// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

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

  public static class MissingPropertyIssueData implements IssueData {
    public final String propertyName;
    public final JsonSchemaType propertyType;

    public MissingPropertyIssueData(String propertyName, JsonSchemaType propertyType) {
      this.propertyName = propertyName;
      this.propertyType = propertyType;
    }
  }

  public static class ProhibitedPropertyIssueData implements IssueData {
    public final String propertyName;

    public ProhibitedPropertyIssueData(String propertyName) {
      this.propertyName = propertyName;
    }
  }

  public static class NonEnumValueIssueData implements IssueData {
    public final String[] expectedValues;

    public NonEnumValueIssueData(String[] expectedValues) {
      this.expectedValues = expectedValues;
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
  public LocalQuickFix createFix(PsiElement key) {
    return null;
  }
}
