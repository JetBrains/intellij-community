// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.jetcache;

import com.google.common.hash.Hashing;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;

public interface ProjectStateHashGenerator {
  ExtensionPointName<ProjectStateHashGenerator> EP_NAME = ExtensionPointName.create("com.intellij.projectStateHashGenerator");

  @NotNull
  byte[] generateHash(@NotNull Project project);

  class SimpleProjectStateHashGenerator implements ProjectStateHashGenerator {
    @NotNull
    @Override
    public byte[] generateHash(@NotNull Project project) {
      String name = project.getName();
      return Hashing.murmur3_32().hashString(name, StandardCharsets.UTF_8).asBytes();
    }
  }

  @NotNull
  static byte[] generateHashFor(@NotNull Project project) {
    for (ProjectStateHashGenerator extension : EP_NAME.getExtensions()) {
      return extension.generateHash(project);
    }
    throw new IllegalStateException();
  }
}
