// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.util.DifferenceFilter;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

/**
 * Keeps track of already stored options to prevent the Project.xml from changing when a default value changes in another release.
 */
class StoredOptionsContainer {

  private final Set<String> myOptionSet = new HashSet<>();

  void processOptions(@NotNull Element element) {
    element.getChildren().forEach(
      child ->
      {
        Attribute childAttribute = child.getAttribute("name");
        if (childAttribute != null) {
          myOptionSet.add(childAttribute.getValue());
        }
      }
    );
  }

  StoredOptionsDifferenceFilter createDiffFilter(@NotNull CodeStyleSettings currSettings, @NotNull CodeStyleSettings defaultSettings) {
    return new StoredOptionsDifferenceFilter(currSettings, defaultSettings);
  }

  private class StoredOptionsDifferenceFilter extends DifferenceFilter<CodeStyleSettings> {
    private StoredOptionsDifferenceFilter(CodeStyleSettings object, CodeStyleSettings parentObject) {
      super(object, parentObject);
    }

    @Override
    public boolean test(@NotNull Field field) {
      return myOptionSet.contains(field.getName()) || super.test(field);
    }
  }
}
