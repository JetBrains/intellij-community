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
package com.intellij.spi.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.spi.SPILanguage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.spi.SPIFileType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SPIFile extends PsiFileBase {
  public SPIFile(@NotNull FileViewProvider viewProvider) {
    super(viewProvider, SPILanguage.INSTANCE);
  }

  @NotNull
  @Override
  public PsiReference getReference() {
    return new SPIFileName2ClassReference(this, ReadAction
      .compute(() -> ClassUtil.findPsiClass(getManager(), getName(), null, true, getResolveScope())));
  }

  @NotNull
  @Override
  public PsiReference[] getReferences() {
    return ReadAction.compute(() -> {

      final List<PsiReference> refs = new ArrayList<>();
      int idx = 0;
      int d;
      final String fileName = getName();
      while ((d = fileName.indexOf(".", idx)) > -1) {
        final PsiPackage aPackage = JavaPsiFacade.getInstance(getProject()).findPackage(fileName.substring(0, d));
        if (aPackage != null) {
          refs.add(new SPIFileName2PackageReference(SPIFile.this, aPackage));
        }
        idx = d + 1;
      }
      final PsiReference reference = getReference();
      PsiElement resolve = reference.resolve();
      while (resolve instanceof PsiClass) {
        resolve = ((PsiClass)resolve).getContainingClass();
        if (resolve != null) {
          final String jvmClassName = ClassUtil.getJVMClassName((PsiClass)resolve);
          if (jvmClassName != null) {
            refs.add(new SPIFileName2PackageReference(SPIFile.this, resolve));
          }
        }
      }
      refs.add(reference);
      return refs.toArray(PsiReference.EMPTY_ARRAY);
    });
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return SPIFileType.INSTANCE;
  }
  
  private static class SPIFileName2ClassReference extends PsiReferenceBase<PsiFile> {
    private final PsiClass myClass;

    public SPIFileName2ClassReference(PsiFile file, PsiClass aClass) {
      super(file, new TextRange(0, 0), false);
      myClass = aClass;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
      return myClass;
    }

    @Override
    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      if (myClass != null) {
        final String className = ClassUtil.getJVMClassName(myClass);
        if (className != null) {
          final String newFileName = className.substring(0, className.lastIndexOf(myClass.getName())) + newElementName;
          return getElement().setName(newFileName);
        }
      }
      return getElement();
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
      if (element instanceof PsiClass) {
        final String className = ClassUtil.getJVMClassName((PsiClass)element);
        if (className != null) {
          return getElement().setName(className);
        }
      }
      return getElement();
    }

    @NotNull
    @Override
    public Object[] getVariants() {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
  }

  private static class SPIFileName2PackageReference extends PsiReferenceBase<PsiFile> {
    private final PsiElement myPackageOrContainingClass;

    public SPIFileName2PackageReference(PsiFile file, @NotNull PsiElement psiPackage) {
      super(file, new TextRange(0, 0), false);
      myPackageOrContainingClass = psiPackage;
    }

    @NotNull
    @Override
    public String getCanonicalText() {
      return myPackageOrContainingClass instanceof PsiPackage 
             ? ((PsiPackage)myPackageOrContainingClass).getQualifiedName() : ClassUtil.getJVMClassName((PsiClass)myPackageOrContainingClass);
    }

    @Nullable
    @Override
    public PsiElement resolve() {
      return myPackageOrContainingClass;
    }

    @Override
    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      String newPackageQName = StringUtil.getQualifiedName(StringUtil.getPackageName(getCanonicalText()), newElementName);
      return getElement().setName(newPackageQName + getElement().getName().substring(getCanonicalText().length()));
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
      if (element instanceof PsiPackage) {
        return handleElementRename(((PsiPackage)element).getQualifiedName());
      } else if (element instanceof PsiClass) {
        final String className = ClassUtil.getJVMClassName((PsiClass)element);
        if (className != null) {
          return handleElementRename(className);
        }
      }
      return getElement();
    }

    @NotNull
    @Override
    public Object[] getVariants() {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
  }
}
