// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build.events;

import com.intellij.openapi.util.NlsContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@ApiStatus.Experimental
public class BuildEventsNls {
  @NlsContext(prefix = "build.event.message")
  @Nls
  @Target({ElementType.TYPE_USE, ElementType.METHOD, ElementType.PARAMETER})
  public @interface Message {
  }

  @NlsContext(prefix = "build.event.message")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target(ElementType.TYPE_USE)
  public @interface Hint {
  }

  @NlsContext(prefix = "build.event.description")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target({ElementType.TYPE_USE, ElementType.METHOD})
  public @interface Description {
  }

  @NlsContext(prefix = "build.event.title")
  @Nls(capitalization = Nls.Capitalization.Title)
  @Target({ElementType.TYPE_USE, ElementType.METHOD, ElementType.PARAMETER})
  public @interface Title {
  }

}
