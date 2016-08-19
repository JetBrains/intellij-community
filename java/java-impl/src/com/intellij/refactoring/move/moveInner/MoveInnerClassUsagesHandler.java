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
package com.intellij.refactoring.move.moveInner;

import com.intellij.lang.LanguageExtension;
import com.intellij.psi.PsiClass;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

public interface MoveInnerClassUsagesHandler {
  LanguageExtension<MoveInnerClassUsagesHandler> EP_NAME =
    new LanguageExtension<>("com.intellij.refactoring.moveInnerClassUsagesHandler");

  void correctInnerClassUsage(@NotNull UsageInfo usage, @NotNull PsiClass outerClass);
}