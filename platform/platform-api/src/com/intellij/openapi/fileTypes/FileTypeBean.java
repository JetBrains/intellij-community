// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class FileTypeBean extends AbstractExtensionPointBean {
  private final List<FileNameMatcher> myExtraMatchers = new ArrayList<>();

  @Attribute("implementationClass")
  public String implementationClass;

  /**
   * Name of the public static field in the implementationClass class containing the file type instance.
   */
  @Attribute("fieldName")
  public String fieldName;

  @Attribute("name")
  @NotNull
  public String name;

  @Attribute("extensions")
  public String extensions;

  @Attribute("language")
  public String language;

  public void addMatchers(List<FileNameMatcher> matchers) {
    myExtraMatchers.addAll(matchers);
  }

  public List<FileNameMatcher> getExtraMatchers() {
    return myExtraMatchers;
  }
}
