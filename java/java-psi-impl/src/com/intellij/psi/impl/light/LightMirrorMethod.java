/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.impl.light;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMirrorElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author Max Medvedev
 */
public class LightMirrorMethod extends LightMethod implements PsiMethod, PsiMirrorElement {
  private PsiMethod myPrototype;

  public LightMirrorMethod(@NotNull PsiMethod delegate, @NotNull PsiMethod prototype) {
    super(prototype.getManager(), delegate, delegate.getContainingClass());
    myPrototype = prototype;
  }

  @NotNull
  @Override
  public PsiMethod getPrototype() {
    return myPrototype;
  }
}
