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
package com.intellij.codeInspection.reference;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class RefFileImpl extends RefElementImpl implements RefFile {
  RefFileImpl(PsiFile elem, RefManager manager) {
    super(elem, manager);
    final VirtualFile vFile = elem.getVirtualFile();
    if (vFile == null) return;
    final VirtualFile parentDirectory = vFile.getParent();
    if (parentDirectory == null) return;
    final PsiDirectory psiDirectory = elem.getManager().findDirectory(parentDirectory);
    if (psiDirectory != null) {
      final RefElement element = getRefManager().getReference(psiDirectory);
      if (element != null) {
        ((RefElementImpl)element).add(this);
      }
    }
  }

  @Override
  public PsiFile getElement() {
    return (PsiFile)super.getElement();
  }

  @Override
  public void accept(@NotNull final RefVisitor visitor) {
    ApplicationManager.getApplication().runReadAction(() -> visitor.visitFile(this));
  }

  @Override
  public String getExternalName() {
    final PsiFile psiFile = getElement();
    final VirtualFile virtualFile = psiFile != null ? psiFile.getVirtualFile() : null;
    return virtualFile != null ? virtualFile.getUrl() : getName();
  }

  @Override
  protected void initialize() {
  }

  @Nullable
  public static RefElement fileFromExternalName(final RefManager manager, final String fqName) {
    final VirtualFile virtualFile = VirtualFileManager.getInstance().findFileByUrl(PathMacroManager.getInstance(manager.getProject()).expandPath(fqName));
    if (virtualFile != null) {
      final PsiFile psiFile = PsiManager.getInstance(manager.getProject()).findFile(virtualFile);
      if (psiFile != null) {
        return manager.getReference(psiFile);
      }
    }
    return null;
  }
}
