/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Deprecated
public class IdentitySmartPointer<T extends PsiElement> implements SmartPsiElementPointer<T> {
  private final T myElement;
  private final PsiFile myFile;

  private IdentitySmartPointer(@NotNull T element, @NotNull PsiFile file) {
    myElement = element;
    myFile = file;
  }

  public IdentitySmartPointer(@NotNull T element) {
    this(element, element.getContainingFile());
  }

  @Override
  @NotNull
  public Project getProject() {
    return myFile.getProject();
  }

  @Override
  public VirtualFile getVirtualFile() {
    return myFile.getVirtualFile();
  }

  @Override
  public T getElement() {
    T element = myElement;
    return element.isValid() ? element : null;
  }

  @Override
  public int hashCode() {
    final T elt = getElement();
    return elt == null ? 0 : elt.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof SmartPsiElementPointer
           && SmartPointerManager.getInstance(getProject()).pointToTheSameElement(this, (SmartPsiElementPointer)obj);
  }

  @Override
  public PsiFile getContainingFile() {
    return myFile;
  }

  @Override
  public Segment getRange() {
    return getPsiRange();
  }

  @Nullable
  @Override
  public Segment getPsiRange() {
    return myElement.getTextRange();
  }
}
