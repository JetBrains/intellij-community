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

package com.intellij.psi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.WalkingState;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public abstract class PsiWalkingState extends WalkingState<PsiElement> {
  private static final Logger LOG = Logger.getInstance(PsiWalkingState.class);
  private final PsiElementVisitor myVisitor;

  private static class PsiTreeGuide implements TreeGuide<PsiElement> {
    @Override
    public PsiElement getNextSibling(@NotNull PsiElement element) {
      return checkSanity(element, element.getNextSibling());
    }

    private static PsiElement checkSanity(PsiElement element, PsiElement sibling) {
      if (sibling == PsiUtilCore.NULL_PSI_ELEMENT) throw new PsiInvalidElementAccessException(element, "Sibling of "+element+" is NULL_PSI_ELEMENT");
      return sibling;
    }

    @Override
    public PsiElement getPrevSibling(@NotNull PsiElement element) {
      return checkSanity(element, element.getPrevSibling());
    }

    @Override
    public PsiElement getFirstChild(@NotNull PsiElement element) {
      return element.getFirstChild();
    }

    @Override
    public PsiElement getParent(@NotNull PsiElement element) {
      return element.getParent();
    }

    private static final PsiTreeGuide instance = new PsiTreeGuide();
  }

  protected PsiWalkingState(@NotNull PsiElementVisitor delegate) {
    this(delegate, PsiTreeGuide.instance);
  }
  protected PsiWalkingState(@NotNull PsiElementVisitor delegate, @NotNull TreeGuide<PsiElement> guide) {
    super(guide);
    myVisitor = delegate;
  }

  @Override
  public void visit(@NotNull PsiElement element) {
    element.accept(myVisitor);
  }

  @Override
  public void elementStarted(@NotNull PsiElement element) {
    if (!startedWalking && element instanceof PsiCompiledElement) {
      LOG.error(element+"; Do not use walking visitor inside compiled PSI since getNextSibling() is too slow there");
    }

    super.elementStarted(element);
  }
}
