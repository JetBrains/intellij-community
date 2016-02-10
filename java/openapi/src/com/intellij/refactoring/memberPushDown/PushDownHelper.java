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
package com.intellij.refactoring.memberPushDown;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class PushDownHelper {
  private static final ExtensionPointName<PushDownHelper> EXTENSION_POINT =
    new ExtensionPointName<PushDownHelper>("com.intellij.refactoring.pushDownHelper");

  public static PushDownHelper[] getAll() {
    return EXTENSION_POINT.getExtensions();
  }

  public abstract void findUsages(@NotNull PushDownContext context, @NotNull List<UsageInfo> result);

  public abstract void findConflicts(@NotNull PushDownContext context,
                                     @NotNull List<? extends UsageInfo> usages,
                                     @NotNull MultiMap<PsiElement, String> result);
}
