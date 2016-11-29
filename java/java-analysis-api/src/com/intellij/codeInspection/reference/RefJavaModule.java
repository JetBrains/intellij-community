/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.reference;

import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiRequiresStatement;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * @author Pavel.Dolgov
 */
public interface RefJavaModule extends RefElement {
  @Override
  PsiJavaModule getElement();

  @NotNull
  Map<String, List<String>> getExportedPackageNames();

  @NotNull
  Map<String, Dependency> getRequiredModules();

  class Dependency {
    @NotNull public final Map<String, List<String>> packageNames;
    public final boolean isPublic;

    public Dependency(@NotNull Map<String, List<String>> packageNames, boolean isPublic) {
      this.packageNames = packageNames;
      this.isPublic = isPublic;
    }
  }
}
