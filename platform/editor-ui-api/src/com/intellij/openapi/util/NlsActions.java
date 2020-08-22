// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.Nls;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

public class NlsActions {
  @NlsContext(prefix = "action", suffix = "text")
  @Nls(capitalization = Nls.Capitalization.Title)
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
  public @interface ActionText {
  }

  @NlsContext(prefix = "action", suffix = "description")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
  public @interface ActionDescription {
  }
}