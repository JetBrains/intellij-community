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
package com.intellij.psi.impl.source;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import org.jetbrains.annotations.NotNull;

public class PsiJavaModuleReferenceImpl extends CompositePsiElement implements PsiJavaModuleReference {
  public PsiJavaModuleReferenceImpl() {
    super(JavaElementType.MODULE_REFERENCE);
  }

  @NotNull
  @Override
  public String getReferenceText() {
    StringBuilder sb = new StringBuilder();
    for (PsiElement e = getFirstChild(); e != null; e = e.getNextSibling()) {
      if (!(e instanceof PsiWhiteSpace) && !(e instanceof PsiComment)) {
        sb.append(e.getText());
      }
    }
    return sb.toString();
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitModuleReference(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiJavaModuleReference";
  }
}