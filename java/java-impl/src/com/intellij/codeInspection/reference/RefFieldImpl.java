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
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 * Date: Oct 21, 2001
 */
public class RefFieldImpl extends RefJavaElementImpl implements RefField {
  private static final int USED_FOR_READING_MASK = 0x10000;
  private static final int USED_FOR_WRITING_MASK = 0x20000;
  private static final int ASSIGNED_ONLY_IN_INITIALIZER = 0x40000;

  RefFieldImpl(@NotNull RefClass ownerClass, PsiField field, RefManager manager) {
    super(field, manager);

    ((RefClassImpl)ownerClass).add(this);

    if (ownerClass.isInterface()) {
      setIsStatic(true);
      setIsFinal(true);
    }    
  }

  public PsiField getElement() {
    return (PsiField)super.getElement(); 
  }

  protected void markReferenced(RefElementImpl refFrom, PsiElement psiFrom, PsiElement psiWhat, boolean forWriting, boolean forReading, PsiReferenceExpression expressionFrom) {
    addInReference(refFrom);

    boolean referencedFromClassInitializer = false;

    if (forWriting && expressionFrom != null) {
      PsiClassInitializer initializer = PsiTreeUtil.getParentOfType(expressionFrom, PsiClassInitializer.class);
      if (initializer != null) {
        if (initializer.getParent() instanceof PsiClass && psiFrom == initializer.getParent()) {
          referencedFromClassInitializer = true;
        }
      }
    }

    if (forWriting) {
      setUsedForWriting(true);
    }

    if (forReading) {
      setUsedForReading(true);
    }
    getRefManager().fireNodeMarkedReferenced(this, refFrom, referencedFromClassInitializer, forReading, forWriting);
  }

  public boolean isUsedForReading() {
    return checkFlag(USED_FOR_READING_MASK);
  }

  private void setUsedForReading(boolean usedForReading) {
    setFlag(usedForReading, USED_FOR_READING_MASK);
  }

  public boolean isUsedForWriting() {
    return checkFlag(USED_FOR_WRITING_MASK);
  }

  private void setUsedForWriting(boolean usedForWriting) {
    setFlag(false, ASSIGNED_ONLY_IN_INITIALIZER);
    setFlag(usedForWriting, USED_FOR_WRITING_MASK);
  }

  public boolean isOnlyAssignedInInitializer() {
    return checkFlag(ASSIGNED_ONLY_IN_INITIALIZER);
  }

  public void accept(final RefVisitor visitor) {
    if (visitor instanceof RefJavaVisitor) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          ((RefJavaVisitor)visitor).visitField(RefFieldImpl.this);
        }
      });
    }  else {
      super.accept(visitor);
    }
  }

  public void buildReferences() {
    PsiField psiField = getElement();
    if (psiField != null) {
      final RefJavaUtil refUtil = RefJavaUtil.getInstance();
      refUtil.addReferences(psiField, this, psiField.getInitializer());
      refUtil.addReferences(psiField, this, psiField.getModifierList());
      if (psiField instanceof PsiEnumConstant) {
        refUtil.addReferences(psiField, this, psiField);
      }

      if (psiField.getInitializer() != null || psiField instanceof PsiEnumConstant) {
        if (!checkFlag(USED_FOR_WRITING_MASK)) {
          setFlag(true, ASSIGNED_ONLY_IN_INITIALIZER);
          setFlag(true, USED_FOR_WRITING_MASK);
        }
      }
      PsiType psiType = psiField.getType();
      RefClass ownerClass = refUtil.getOwnerClass(getRefManager(), psiField);

      if (ownerClass != null) {
        psiType = psiType.getDeepComponentType();
        if (psiType instanceof PsiClassType) {
          PsiClass psiClass = PsiUtil.resolveClassInType(psiType);
          if (psiClass != null && getRefManager().belongsToScope(psiClass)) {
              RefClassImpl refClass = (RefClassImpl)getRefManager().getReference(psiClass);
            if (refClass != null) {
              refClass.addTypeReference(ownerClass);
              refClass.addClassExporter(this);
            }
          }
        }

      }
      getRefManager().fireBuildReferences(this);
    }
  }

  public RefClass getOwnerClass() {
    return (RefClass) getOwner();
  }

  public String getExternalName() {
    final String[] result = new String[1];
    final Runnable runnable = new Runnable() {
      public void run() {
        PsiField psiField = getElement();
        LOG.assertTrue(psiField != null);
        result[0] = PsiFormatUtil.getExternalName(psiField);
      }
    };

    ApplicationManager.getApplication().runReadAction(runnable);

    return result[0];
  }

  @Nullable
  public static RefField fieldFromExternalName(RefManager manager, String externalName) {
    return (RefField)manager.getReference(findPsiField(PsiManager.getInstance(manager.getProject()), externalName));
  }

  @Nullable
  public static PsiField findPsiField(PsiManager manager, String externalName) {
    int classNameDelimeter = externalName.lastIndexOf(' ');
    if (classNameDelimeter > 0 && classNameDelimeter < externalName.length() - 1) {
      final String className = externalName.substring(0, classNameDelimeter);
      final String fieldName = externalName.substring(classNameDelimeter + 1);
      final PsiClass psiClass = ClassUtil.findPsiClass(manager, className);
      if (psiClass != null) {
        return psiClass.findFieldByName(fieldName, false);
      }
    }
    return null;
  }

  public boolean isSuspicious() {
    if (isEntry()) return false;
    if (super.isSuspicious()) return true;
    return isUsedForReading() != isUsedForWriting();
  }

  protected void initialize() {
    getRefManager().fireNodeInitialized(this);
  }
}
