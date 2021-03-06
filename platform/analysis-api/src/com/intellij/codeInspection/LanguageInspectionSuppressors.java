// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.lang.LanguageExtension;

public final class LanguageInspectionSuppressors extends LanguageExtension<InspectionSuppressor> {
  public static final LanguageInspectionSuppressors INSTANCE = new LanguageInspectionSuppressors();

  private LanguageInspectionSuppressors() {
    super("com.intellij.lang.inspectionSuppressor");
  }

}