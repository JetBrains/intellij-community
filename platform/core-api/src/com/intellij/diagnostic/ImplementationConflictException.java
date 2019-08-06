// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class ImplementationConflictException extends RuntimeException {
  @NotNull
  private final Collection<Class<?>> myConflictingClasses;

  public ImplementationConflictException(String message, Throwable cause, @NotNull Object ...implementationObjects) {
    super(message, cause);
    final List<Class<?>> classes = new ArrayList<>(implementationObjects.length);
    for (Object object : implementationObjects) {
      classes.add(object.getClass());
    }

    myConflictingClasses = Collections.unmodifiableList(classes);
  }

  @NotNull
  public Collection<Class<?>> getConflictingClasses() {
    return myConflictingClasses;
  }
}
