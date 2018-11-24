/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AddNotNullAnnotationIntention extends AddAnnotationIntention implements LowPriorityAction {
  @NotNull
  @Override
  public Pair<String, String[]> getAnnotations(@NotNull Project project) {
    return Pair.create(NullableNotNullManager.getInstance(project).getDefaultNotNull(), getNullables(project));
  }

  @NotNull
  private static String[] getNullables(@NotNull Project project) {
    final List<String> nullables = NullableNotNullManager.getInstance(project).getNullables();
    return ArrayUtil.toStringArray(nullables);
  }

  @Override
  protected boolean canAnnotate(@NotNull PsiModifierListOwner owner) {
    return AddNullableNotNullAnnotationFix.canAnnotate(owner);
  }
}
