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
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RefDirectoryImpl extends RefElementImpl implements RefDirectory{
  private final RefModule myRefModule;
  protected RefDirectoryImpl(PsiDirectory psiElement, RefManager refManager) {
    super(psiElement.getName(), psiElement, refManager);
    final PsiDirectory parentDirectory = psiElement.getParentDirectory();
    if (parentDirectory != null && parentDirectory.getManager().isInProject(parentDirectory)) {
      final RefElementImpl refElement = (RefElementImpl)refManager.getReference(parentDirectory);
      if (refElement != null) {
        refElement.add(this);
        myRefModule = null;
        return;
      }
    }
    myRefModule = refManager.getRefModule(ModuleUtilCore.findModuleForPsiElement(psiElement));
  }


  @Override
  protected void initialize() {
    if (myRefModule != null) {
      ((RefModuleImpl)myRefModule).add(this);
      return;
    }
    ((RefProjectImpl)myManager.getRefProject()).add(this);
  }

  @Override
  public void accept(@NotNull final RefVisitor visitor) {
    ApplicationManager.getApplication().runReadAction(() -> visitor.visitDirectory(this));
  }

  @Override
  public boolean isValid() {
    if (isDeleted()) return false;
    return ReadAction.compute(() -> {
      if (getRefManager().getProject().isDisposed()) return false;

      VirtualFile directory = getVirtualFile();
      return directory != null && directory.isValid();
    });
  }

  @Nullable
  @Override
  public RefModule getModule() {
    return myRefModule;
  }


  @NotNull
  @Override
  public String getQualifiedName() {
    return getName(); //todo relative name
  }

  @Override
  public String getExternalName() {
    final PsiElement element = getPsiElement();
    assert element != null;
    return ((PsiDirectory)element).getVirtualFile().getPath();
  }
}
