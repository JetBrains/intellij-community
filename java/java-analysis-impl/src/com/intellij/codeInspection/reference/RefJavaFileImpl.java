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
import com.intellij.psi.*;

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
    PsiJavaFile file = (PsiJavaFile)getElement();
    if (file != null && PsiPackage.PACKAGE_INFO_FILE.equals(file.getName())) {
        PsiPackageStatement packageStatement = file.getPackageStatement();
        if (packageStatement != null) {
          packageStatement.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
              super.visitReferenceElement(reference);
              processReference(reference.resolve());
            }

            @Override
            public void visitNameValuePair(PsiNameValuePair pair) {
              super.visitNameValuePair(pair);
              PsiReference reference = pair.getReference();
              if (reference != null) {
                processReference(reference.resolve());
              }
            }

            private void processReference(PsiElement element) {
              RefElement refElement = getRefManager().getReference(element);
              if (refElement instanceof RefJavaElementImpl) {
                addOutReference(refElement);
                ((RefJavaElementImpl)refElement).markReferenced(RefJavaFileImpl.this, file, element, false, true, null);
              }
            }
          });
        }
      }
    getRefManager().fireBuildReferences(this);
  }

  @Override
  public RefModule getModule() {
    return myRefModule;
  }
}
