// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

public class JavaLensSettings {
  private boolean showUsages;
  private boolean showImplementations;
  private boolean showRelatedProblems = true;

  public JavaLensSettings(boolean showUsages, boolean showImplementations, boolean showRelatedProblems) {
    this.showUsages = showUsages;
    this.showImplementations = showImplementations;
    this.showRelatedProblems = showRelatedProblems;
  }

  public JavaLensSettings() {
  }

  public boolean isShowUsages() {
    return showUsages;
  }

  public void setShowUsages(boolean showUsages) {
    this.showUsages = showUsages;
  }

  public boolean isShowImplementations() {
    return showImplementations;
  }

  public void setShowImplementations(boolean showImplementations) {
    this.showImplementations = showImplementations;
  }

  public boolean isShowRelatedProblems() {
    return showRelatedProblems;
  }

  public void setShowRelatedProblems(boolean showRelatedProblems) {
    this.showRelatedProblems = showRelatedProblems;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    JavaLensSettings settings = (JavaLensSettings)o;

    if (showUsages != settings.showUsages || showRelatedProblems != settings.showRelatedProblems) return false;
    return showImplementations == settings.showImplementations;
  }

  @Override
  public int hashCode() {
    int result = showUsages ? 1 : 0;
    result = 31 * result + (showImplementations ? 1 : 0);
    result = 31 * result + (showRelatedProblems ? 1 : 0);
    return result;
  }
}
