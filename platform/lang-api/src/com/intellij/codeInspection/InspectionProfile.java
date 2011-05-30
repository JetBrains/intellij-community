/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.openapi.project.Project;
import com.intellij.profile.Profile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 * Date: Dec 7, 2004
 */
public interface InspectionProfile extends Profile {

  HighlightDisplayLevel getErrorLevel(@NotNull HighlightDisplayKey inspectionToolKey, PsiElement element);
  

  InspectionProfileEntry getInspectionTool(@NotNull String shortName, @NotNull PsiElement element);

  @Deprecated
  InspectionProfileEntry getInspectionTool(@NotNull String shortName);

  @NotNull
  InspectionProfileEntry[] getInspectionTools(@Nullable PsiElement element);

  void cleanup(Project project);

  @NotNull
  ModifiableModel getModifiableModel();

  boolean isToolEnabled(HighlightDisplayKey key, PsiElement element);

  boolean isToolEnabled(HighlightDisplayKey key);

  boolean isExecutable();

  boolean isEditable();  

  @NotNull
  String getDisplayName();
}
