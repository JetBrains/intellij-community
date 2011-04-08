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
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
* User: cdr
*/
class InjectedSelfElementInfo extends SelfElementInfo {
  private final SmartPsiFileRange myPsiFileRangeInHostElement;
  private final Class<? extends PsiElement> myClass;

  InjectedSelfElementInfo(@NotNull Project project,
                          @NotNull PsiElement anchor,
                          @NotNull PsiElement context) {
    super(project, context);
    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(project);
    myPsiFileRangeInHostElement = smartPointerManager.createSmartPsiFileRangePointer(context.getContainingFile(), InjectedLanguageManager.getInstance(
      project).injectedToHost(anchor, anchor.getTextRange()));
    myClass = anchor.getClass();
  }

  @Override
  public VirtualFile getVirtualFile() {
    PsiElement element = restoreElement();
    if (element == null) return null;
    return element.getContainingFile().getVirtualFile();
  }

  @Override
  public PsiElement restoreElement() {
    PsiElement host = super.restoreElement();
    if (host == null) return null;

    Segment segment = myPsiFileRangeInHostElement.getRange();
    if (segment == null) return null;
    final TextRange rangeInHostElement = TextRange.create(segment);
    final Ref<PsiElement> result = new Ref<PsiElement>();

    InjectedLanguageUtil.enumerate(host, host.getContainingFile(), new PsiLanguageInjectionHost.InjectedPsiVisitor() {
        @Override
        public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
          if (result.get() != null) return;
          TextRange hostRange =
            InjectedLanguageManager.getInstance(getProject()).injectedToHost(injectedPsi, new TextRange(0, injectedPsi.getTextLength()));
          if (hostRange.contains(rangeInHostElement)) {
            TextRange rangeInside = rangeInHostElement.shiftRight(-hostRange.getStartOffset());
            PsiElement element = findElementInside(injectedPsi, rangeInside.getStartOffset(), rangeInside.getEndOffset(), myClass);
            result.set(element);
          }
        }
      }, false);

    return result.get();
  }
}
