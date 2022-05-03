// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

public class OverrideOrImplementOptions {
  private boolean copyJavadoc = false;
  private boolean generateJavadoc = false;
  private boolean insertOverrideWherePossible = false;

  public boolean isInsertOverrideWherePossible() {
    return insertOverrideWherePossible;
  }

  public boolean isGenerateJavaDoc() {
    return generateJavadoc;
  }

  public boolean isCopyJavaDoc() {
    return copyJavadoc;
  }

  public OverrideOrImplementOptions copyJavadoc(boolean value){
    copyJavadoc = value;
    return this;
  }

  public OverrideOrImplementOptions generateJavadoc(boolean value){
    generateJavadoc = value;
    return this;
  }

  public OverrideOrImplementOptions insertOverrideWherePossible(boolean value){
    insertOverrideWherePossible = value;
    return this;
  }
}