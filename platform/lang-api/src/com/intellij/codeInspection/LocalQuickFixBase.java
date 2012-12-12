/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public abstract class LocalQuickFixBase implements LocalQuickFix {
  private final String myName;
  private final String myFamilyName;

  public LocalQuickFixBase(String name) {
    this(name, name);
  }

  public LocalQuickFixBase(String name, String familyName) {
    myName = name;
    myFamilyName = familyName;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return myFamilyName;
  }

  @Override
  public abstract void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor);
}
