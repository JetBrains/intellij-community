/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.HashSet;

import java.util.Collection;
import java.util.Set;

/**
 * @author peter
 */
public class UniqueNameGenerator implements Condition<String> {
  private final Set<String> myExistingNames = new HashSet<String>();

  public UniqueNameGenerator(final Collection elements) {
    for (final Object t : elements) {
      myExistingNames.add(ElementPresentationManager.getElementName(t));
    }
  }

  public final boolean value(final String candidate) {
    return !myExistingNames.contains(candidate);
  }

  public final boolean isUnique(final String name, String prefix, String suffix) {
    return value(prefix + name + suffix);
  }

  public static String generateUniqueName(final String defaultName, final String prefix, final String suffix, final Condition<String> validator) {
    final String defaultFullName = prefix + defaultName + suffix;
    if (validator.value(defaultFullName)) {
      return defaultFullName;
    }

    for (int i = 2; ; i++) {
      final String fullName = prefix + (defaultName + i) + suffix;
      if (validator.value(fullName)) {
        return fullName;
      }
    }
  }

  public String generateUniqueName(final String defaultName, final String prefix, final String suffix) {
    return generateUniqueName(defaultName, prefix, suffix, this);
  }

  public String generateUniqueName(final String defaultName) {
    return generateUniqueName(defaultName, "", "", this);
  }

}
