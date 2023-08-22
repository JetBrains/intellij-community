// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import org.jetbrains.annotations.Nls;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

public class NlsActions {
  @NlsContext(prefix = "action", suffix = "text")
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
  public @Nls(capitalization = Nls.Capitalization.Title) @interface ActionText {
  }

  @NlsContext(prefix = "action", suffix = "description")
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
  public @Nls(capitalization = Nls.Capitalization.Sentence) @interface ActionDescription {
  }
}