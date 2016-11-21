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
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.*;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Pavel.Dolgov
 */
public class RefJavaModuleImpl extends RefElementImpl implements RefJavaModule {
  private final RefModule myRefModule;

  private Map<String, List<String>> myExportedPackageNames;
  private Map<String, Boolean> myRequiredModuleNames;

  public RefJavaModuleImpl(@NotNull PsiJavaModule javaModule, @NotNull RefManagerImpl manager) {
    super(javaModule.getModuleName(), javaModule, manager);
    myRefModule = manager.getRefModule(ModuleUtilCore.findModuleForPsiElement(javaModule));
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

  @Nullable
  @Override
  public RefModule getModule() {
    return myRefModule;
  }

  @NotNull
  @Override
  public Map<String, List<String>> getExportedPackageNames() {
    return myExportedPackageNames != null ? myExportedPackageNames : Collections.emptyMap();
  }

  @NotNull
  @Override
  public Map<String, Boolean> getRequiredModuleNames() {
    return myRequiredModuleNames != null ? myRequiredModuleNames : Collections.emptyMap();
  }

  @Override
  public void buildReferences() {
    PsiJavaModule javaModule = getElement();
    if (javaModule != null) {
      for (PsiRequiresStatement statement : javaModule.getRequires()) {
        PsiJavaModuleReferenceElement referenceElement = statement.getReferenceElement();
        if (referenceElement != null) {
          PsiPolyVariantReference moduleReference = referenceElement.getReference();
          PsiElement element = addReference(moduleReference);
          if (element instanceof PsiJavaModule) {
            if (myRequiredModuleNames == null) myRequiredModuleNames = new THashMap<>(1);
            myRequiredModuleNames.put(((PsiJavaModule)element).getModuleName(), statement.isPublic());
          }
        }
      }
      List<String> emptyList = Collections.emptyList();
      for (PsiExportsStatement statement : javaModule.getExports()) {
        PsiJavaCodeReferenceElement packageReference = statement.getPackageReference();
        PsiElement element = addReference(packageReference);
        String packageName = null;
        if (element instanceof PsiPackage) {
          if (myExportedPackageNames == null) myExportedPackageNames = new THashMap<>(1);
          packageName = ((PsiPackage)element).getQualifiedName();
          myExportedPackageNames.put(packageName, emptyList);
        }
        for (PsiJavaModuleReferenceElement referenceElement : statement.getModuleReferences()) {
          if (referenceElement != null) {
            PsiPolyVariantReference moduleReference = referenceElement.getReference();
            PsiElement moduleElement = addReference(moduleReference);
            if (packageName != null && moduleElement instanceof PsiJavaModule) {
              List<String> toModuleNames = myExportedPackageNames.get(packageName);
              if (toModuleNames == emptyList) myExportedPackageNames.put(packageName, toModuleNames = new ArrayList<>(1));
              toModuleNames.add(((PsiJavaModule)moduleElement).getModuleName());
            }
          }
        }
      }
      getRefManager().fireBuildReferences(this);
    }
  }

  private PsiElement addReference(PsiPolyVariantReference reference) {
    List<PsiElement> resolvedElements = new ArrayList<>();
    if (reference != null) {
      ResolveResult[] resolveResults = reference.multiResolve(false);
      for (ResolveResult resolveResult : resolveResults) {
        PsiElement element = resolveResult.getElement();
        if (element != null) {
          resolvedElements.add(element);
          RefElement refElement = getRefManager().getReference(element);
          if (refElement != null) {
            addOutReference(refElement);
            ((RefElementImpl)refElement).addInReference(this);
          }
        }
      }
    }
    return resolvedElements.size() == 1 ? resolvedElements.get(0) : null;
  }
}
