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
package com.intellij.codeInspection.reference;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Pavel.Dolgov
 */
public class RefJavaModuleImpl extends RefElementImpl implements RefJavaModule {
  private static final Logger LOG = Logger.getInstance(RefJavaModuleImpl.class);

  public RefJavaModuleImpl(@NotNull PsiJavaModule javaModule, @NotNull RefManagerImpl manager) {
    super(javaModule.getModuleName(), javaModule, manager);
  }

  @Override
  protected void initialize() {

  }

  @Override
  public void accept(@NotNull RefVisitor visitor) {
    if (visitor instanceof RefJavaVisitor) {
      ApplicationManager.getApplication().runReadAction(() -> ((RefJavaVisitor)visitor).visitJavaModule(this));
    }
    else {
      super.accept(visitor);
    }
  }

  @Nullable
  @Override
  public PsiJavaModule getElement() {
    return (PsiJavaModule)super.getElement();
  }

  @Override
  public void buildReferences() {
    PsiJavaModule javaModule = getElement();
    if (javaModule != null) {
      LOG.warn("buildReferences " + javaModule.getModuleName());
      for (PsiRequiresStatement statement : javaModule.getRequires()) {
        PsiJavaModuleReferenceElement referenceElement = statement.getReferenceElement();
        if (referenceElement != null) {
          PsiPolyVariantReference moduleReference = referenceElement.getReference();
          addReference(moduleReference);
        }
      }
      for (PsiExportsStatement statement : javaModule.getExports()) {
        PsiJavaCodeReferenceElement packageReference = statement.getPackageReference();
        addReference(packageReference);
        for (PsiJavaModuleReferenceElement referenceElement : statement.getModuleReferences()) {
          if (referenceElement != null) {
            PsiPolyVariantReference moduleReference = referenceElement.getReference();
            addReference(moduleReference);
          }
        }
      }
      getRefManager().fireBuildReferences(this);
    }
  }

  private void addReference(PsiPolyVariantReference reference) {
    if (reference != null) {
      ResolveResult[] resolveResults = reference.multiResolve(false);
      for (ResolveResult resolveResult : resolveResults) {
        PsiElement element = resolveResult.getElement();
        if (element != null) {
          RefElement refElement = getRefManager().getReference(element);
          if (refElement != null) {
            LOG.warn("addReference " + this.getName() + " -> " + refElement.getName());
            addOutReference(refElement);
            ((RefElementImpl)refElement).addInReference(this);
          }
        }
      }
    }
  }
}
