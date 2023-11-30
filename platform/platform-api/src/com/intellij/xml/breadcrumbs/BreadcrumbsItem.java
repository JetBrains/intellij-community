// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.breadcrumbs;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nullable;

public abstract class BreadcrumbsItem {

  public abstract String getDisplayText();

  public @NlsContexts.Tooltip String getTooltip() {
    return "";
  }

  public @Nullable CrumbPresentation getPresentation() {
    return null;
  }
}
