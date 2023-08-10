// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarRepository;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.Nls;

public final class RepositoryLibraryDefinition {
  public static final ExtensionPointName<RepositoryLibraryDefinition> EP_NAME = new ExtensionPointName<>("com.intellij.repositoryLibrary");

  @Attribute("name")
  public @Nls(capitalization = Nls.Capitalization.Title) String name;

  @Tag("groupId")
  public String groupId;

  @Tag("artifactId")
  public String artifactId;
}
