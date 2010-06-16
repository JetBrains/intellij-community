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
package com.intellij.psi;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Gregory.Shrago
 */
public abstract class PsiReferenceService {

  public static final Key<Hints> HINTS = Key.create("HINTS");

  public static PsiReferenceService getService() {
    return ServiceManager.getService(PsiReferenceService.class);
  }

  public abstract List<PsiReference> getReferences(@NotNull final PsiElement element, @NotNull final Hints hints);

  public PsiReference[] getContributedReferences(@NotNull final PsiElement element) {
    final List<PsiReference> list = getReferences(element, Hints.NO_HINTS);
    return list.toArray(new PsiReference[list.size()]);
  }


  public static class Hints {
    public static final Hints NO_HINTS = new Hints();

    @Nullable public final PsiElement target;
    @Nullable public final Integer offsetInElement;

    public Hints() {
      target = null;
      offsetInElement = null;
    }

    public Hints(@Nullable PsiElement target, @Nullable Integer offsetInElement) {
      this.target = target;
      this.offsetInElement = offsetInElement;
    }
  }
}
