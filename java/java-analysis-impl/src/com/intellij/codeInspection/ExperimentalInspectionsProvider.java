// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

public class ExperimentalInspectionsProvider implements InspectionToolProvider {
  @NotNull
  @Override
  public Class[] getInspectionClasses() {
    if (Registry.is(LanguageLevel.EXPERIMENTAL_KEY, false)) {
      return new Class[] {RedundantExplicitVariableTypeInspection.class, VariableTypeCanBeExplicitInspection.class};
    }
    return ArrayUtil.EMPTY_CLASS_ARRAY;
  }
}
