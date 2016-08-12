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
 * User: anna
 * Date: 20-Dec-2007
 */
package com.intellij.codeInspection.reference;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class RefDirectoryImpl extends RefElementImpl implements RefDirectory{
  protected RefDirectoryImpl(PsiDirectory psiElement, RefManager refManager) {
    super(psiElement.getName(), psiElement, refManager);
    final PsiDirectory parentDirectory = psiElement.getParentDirectory();
    if (parentDirectory != null && parentDirectory.getManager().isInProject(parentDirectory)) {
      final RefElementImpl refElement = (RefElementImpl)refManager.getReference(parentDirectory);
      if (refElement != null) {
        refElement.add(this);
        return;
      }
    }
    final Module module = ModuleUtilCore.findModuleForPsiElement(psiElement);
    if (module != null) {
      final RefModuleImpl refModule = (RefModuleImpl)refManager.getRefModule(module);
      if (refModule != null) {
        refModule.add(this);
        return;
      }
    }
    ((RefProjectImpl)refManager.getRefProject()).add(this);
  }

  @Override
  public void accept(@NotNull final RefVisitor visitor) {
    ApplicationManager.getApplication().runReadAction(() -> visitor.visitDirectory(this));
  }

  @Override
  protected void initialize() {
  }

  @NotNull
  @Override
  public String getQualifiedName() {
    return getName(); //todo relative name
  }

  @Override
  public String getExternalName() {
    final PsiElement element = getElement();
    assert element != null;
    return ((PsiDirectory)element).getVirtualFile().getPath();
  }
}
