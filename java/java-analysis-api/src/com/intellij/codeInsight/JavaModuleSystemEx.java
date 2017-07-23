/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.JavaModuleSystem;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

@ApiStatus.Experimental
public interface JavaModuleSystemEx extends JavaModuleSystem {
  final class ErrorWithFixes {
    public final @NotNull @Nls String message;
    public final @NotNull List<IntentionAction> fixes;

    public ErrorWithFixes(@NotNull @Nls String message) {
      this(message, Collections.emptyList());
    }

    public ErrorWithFixes(@NotNull @Nls String message, @NotNull List<IntentionAction> fixes) {
      this.message = message;
      this.fixes = fixes;
    }
  }

  @Nullable ErrorWithFixes checkAccess(@NotNull PsiPackage target, @NotNull PsiElement place);
  @Nullable ErrorWithFixes checkAccess(@NotNull PsiClass target, @NotNull PsiElement place);
}