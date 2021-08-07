// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.patterns.compiler;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;

public final class PatternClassBean {
  @Attribute("className")
  public String className;
  @Attribute("alias")
  public String alias;
  @Tag("description")
  public String description;

  public String getDescription() {
    return description;
  }

  public String getAlias() {
    return alias;
  }
}
