// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.facet.frameworks.beans;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.ApiStatus;

@Tag("required-class")
@ApiStatus.Internal
public class RequiredClass {

  @Attribute("fqn")
  public String myFqn;

  public String getFqn() {
    return myFqn;
  }
}