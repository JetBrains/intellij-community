// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;

@Tag("product-descriptor")
public class ProductDescriptor {
  @Attribute("code")
  public String code;

  @Attribute("release-date")
  public String releaseDate;

  @Attribute("release-version")
  public int releaseVersion;
}
