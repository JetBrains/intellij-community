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

import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiJavaFile;

public class RefJavaFileImpl extends RefFileImpl {
  private final RefModule myRefModule;

  RefJavaFileImpl(PsiJavaFile elem, RefManager manager) {
    super(elem, manager, false);
    myRefModule = manager.getRefModule(ModuleUtilCore.findModuleForPsiElement(elem));
    String packageName = elem.getPackageName();
    if (!packageName.isEmpty()) {
      ((RefPackageImpl)getRefManager().getExtension(RefJavaManager.MANAGER).getPackage(packageName)).add(this);
    } else if (myRefModule != null) {
      ((RefModuleImpl)myRefModule).add(this);
    } else {
      ((RefProjectImpl)manager.getRefProject()).add(this);
    }
  }

  @Override
  public void buildReferences() {
    getRefManager().fireBuildReferences(this);
  }

  @Override
  public RefModule getModule() {
    return myRefModule;
  }
}
