/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.quickFix.ExternalLibraryDescriptor;
import com.intellij.codeInsight.daemon.quickFix.ExternalLibraryResolver;
import com.intellij.openapi.module.Module;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author nik
 */
public class JetBrainsAnnotationsExternalLibraryResolver extends ExternalLibraryResolver {
  private static final ExternalLibraryDescriptor ANNOTATIONS = new ExternalLibraryDescriptor("com.intellij", "annotations", null) {
    @NotNull
    @Override
    public List<String> locateLibraryClassesRoots(@NotNull Module contextModule) {
      return ContainerUtil.createMaybeSingletonList(OrderEntryFix.locateAnnotationsJar(contextModule));
    }
  };

  @Nullable
  @Override
  public ExternalClassResolveResult resolveClass(@NotNull String shortClassName, @NotNull ThreeState isAnnotation) {
    if (AnnotationUtil.isJetbrainsAnnotation(shortClassName)) {
      return new ExternalClassResolveResult("org.jetbrains.annotations." + shortClassName, ANNOTATIONS);
    }
    return null;
  }
}
