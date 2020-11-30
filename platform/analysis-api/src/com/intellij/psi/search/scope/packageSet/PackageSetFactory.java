// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.scope.packageSet;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NonNls;

public abstract class PackageSetFactory {
  public abstract PackageSet compile(@NonNls String text) throws ParsingException;

  public static PackageSetFactory getInstance() {
    return ApplicationManager.getApplication().getService(PackageSetFactory.class);
  }
}