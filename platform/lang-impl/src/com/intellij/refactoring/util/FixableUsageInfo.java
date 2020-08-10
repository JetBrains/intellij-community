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
package com.intellij.refactoring.util;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"AbstractClassExtendsConcreteClass"})
public abstract class FixableUsageInfo extends UsageInfo {
  public FixableUsageInfo(PsiElement element) {
    super(element);
  }

  public abstract void fixUsage() throws IncorrectOperationException;

  @Nullable
  @NlsContexts.DialogMessage
  public String getConflictMessage() {
    return null;
  }
}
