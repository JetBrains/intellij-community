/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInspection.nullable;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import org.jetbrains.annotations.NotNull;

class ChangeNullableDefaultsFix implements LocalQuickFix {
  private final NullableNotNullManager myManager;
  private final String myNotNullName;
  private final String myNullableName;

  ChangeNullableDefaultsFix(PsiAnnotation notNull, PsiAnnotation nullable, NullableNotNullManager manager) {
    myNotNullName = notNull != null ? notNull.getQualifiedName() : null;
    myNullableName = nullable != null ? nullable.getQualifiedName() : null;
    myManager = manager;
  }

  ChangeNullableDefaultsFix(String notNull, String nullable, NullableNotNullManager manager) {
    myManager = manager;
    myNotNullName = notNull;
    myNullableName = nullable;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Make \"" + (myNotNullName != null ? myNotNullName : myNullableName) + "\" default annotation";
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    if (myNotNullName != null) {
      myManager.setDefaultNotNull(myNotNullName);
    }
    else {
      myManager.setDefaultNullable(myNullableName);
    }
  }
}
