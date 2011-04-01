/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.psi.impl.smartPointers;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import org.jetbrains.annotations.NotNull;

/**
* User: cdr
*/
class InjectedSelfElementInfo extends SelfElementInfo {
  InjectedSelfElementInfo(@NotNull Project project,
                          @NotNull PsiElement anchor,
                          @NotNull PsiFile containingFile) {
    super(project, InjectedLanguageManager.getInstance(project).injectedToHost(anchor, anchor.getTextRange()), anchor.getClass(), InjectedLanguageUtil.getTopLevelFile(containingFile));
    assert containingFile.getContext() != null;
  }

  @Override
  protected PsiElement findAnchorAt(@NotNull PsiFile file, int syncStartOffset) {
    return InjectedLanguageUtil.findInjectedElementNoCommitWithOffset(file, syncStartOffset);
  }
}
