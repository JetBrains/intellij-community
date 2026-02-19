// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events;

import com.intellij.openapi.util.NlsContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@ApiStatus.Experimental
public class BuildEventsNls {
  @NlsContext(prefix = "build.event.message")
  @Target({ElementType.TYPE_USE, ElementType.METHOD, ElementType.PARAMETER})
  public @Nls @interface Message {
  }

  @NlsContext(prefix = "build.event.message")
  @Target(ElementType.TYPE_USE)
  public @Nls(capitalization = Nls.Capitalization.Sentence) @interface Hint {
  }

  @NlsContext(prefix = "build.event.description")
  @Target({ElementType.TYPE_USE, ElementType.METHOD})
  public @Nls(capitalization = Nls.Capitalization.Sentence) @interface Description {
  }

  @NlsContext(prefix = "build.event.title")
  @Target({ElementType.TYPE_USE, ElementType.METHOD, ElementType.PARAMETER})
  public @Nls(capitalization = Nls.Capitalization.Title) @interface Title {
  }

}
