// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.reference;

import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.*;
import org.jetbrains.uast.UFile;
import org.jetbrains.uast.UastContextKt;

import java.util.Objects;

public class RefJavaFileImpl extends RefFileImpl {
  private volatile RefModule myRefModule;

  RefJavaFileImpl(PsiFile elem, RefManager manager) {
    super(elem, manager);
  }

  @Override
  public void buildReferences() {
    PsiFile file = getPsiElement();
    if (file != null && PsiPackage.PACKAGE_INFO_FILE.equals(file.getName())) {
        PsiPackageStatement packageStatement = ((PsiJavaFile)file).getPackageStatement();
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
                ((RefJavaElementImpl)refElement).markReferenced(RefJavaFileImpl.this, false, true, null);
              }
            }
          });
        }
      }
    getRefManager().fireBuildReferences(this);
  }

  @Override
  protected void initialize() {
    PsiFile psiFile = getPsiElement();
    if (psiFile == null) return;
    myRefModule = getRefManager().getRefModule(ModuleUtilCore.findModuleForFile(psiFile));
    UFile file = Objects.requireNonNull(UastContextKt.toUElement(psiFile, UFile.class));
    String packageName = file.getPackageName();
    if (!packageName.isEmpty()) {
      ((RefPackageImpl)getRefManager().getExtension(RefJavaManager.MANAGER).getPackage(packageName)).add(this);
    } else if (myRefModule != null) {
      ((WritableRefEntity)myRefModule).add(this);
    } else {
      ((RefProjectImpl)getRefManager().getRefProject()).add(this);
    }
  }

  @Override
  public RefModule getModule() {
    return myRefModule;
  }
}
