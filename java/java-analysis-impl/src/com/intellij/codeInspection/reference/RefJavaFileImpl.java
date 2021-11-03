// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.reference;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import org.jetbrains.uast.UFile;
import org.jetbrains.uast.UastContextKt;

public class RefJavaFileImpl extends RefFileImpl {
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
    UFile file = UastContextKt.toUElement(psiFile, UFile.class);
    String packageName = file != null ? file.getPackageName() : null;
    if (!StringUtil.isEmpty(packageName)) {
      ((RefPackageImpl)getRefManager().getExtension(RefJavaManager.MANAGER).getPackage(packageName)).add(this);
    } else {
      final RefModule module = getModule();
      if (module != null) {
        ((WritableRefEntity)module).add(this);
      } else {
        ((RefProjectImpl)getRefManager().getRefProject()).add(this);
      }
    }
  }
}
