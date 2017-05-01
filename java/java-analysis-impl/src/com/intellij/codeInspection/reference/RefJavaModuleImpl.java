/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.util.containers.ContainerUtil;
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
  private List<RequiredModule> myRequiredModules;

  RefJavaModuleImpl(@NotNull PsiJavaModule javaModule, @NotNull RefManagerImpl manager) {
    super(javaModule.getName(), javaModule, manager);
    myRefModule = manager.getRefModule(ModuleUtilCore.findModuleForPsiElement(javaModule));
  }

  @Override
  protected void initialize() {
    ((RefModuleImpl)myRefModule).add(this);
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

  @Override
  @NotNull
  public List<RequiredModule> getRequiredModules() {
    return myRequiredModules != null ? myRequiredModules : Collections.emptyList();
  }

  @Override
  public void buildReferences() {
    PsiJavaModule javaModule = getElement();
    if (javaModule != null) {
      for (PsiRequiresStatement statement : javaModule.getRequires()) {
        PsiJavaModuleReferenceElement referenceElement = statement.getReferenceElement();
        if (referenceElement != null) {
          PsiElement element = addReference(referenceElement.getReference());
          if (element instanceof PsiJavaModule) {
            PsiJavaModule requiredModule = (PsiJavaModule)element;
            Map<String, List<String>> packagesExportedByModule = getPackagesExportedByModule(requiredModule);
            if (myRequiredModules == null) myRequiredModules = new ArrayList<>(1);
            myRequiredModules.add(new RequiredModule(requiredModule.getName(), packagesExportedByModule, statement.hasModifierProperty(PsiModifier.TRANSITIVE)));
          }
        }
      }
      List<String> emptyList = Collections.emptyList();
      for (PsiPackageAccessibilityStatement statement : javaModule.getExports()) {
        PsiElement element = addReference(statement.getPackageReference());
        String packageName = null;
        if (element instanceof PsiPackage) {
          packageName = ((PsiPackage)element).getQualifiedName();
          if (myExportedPackageNames == null) myExportedPackageNames = new THashMap<>(1);
          myExportedPackageNames.put(packageName, emptyList);
        }
        for (PsiJavaModuleReferenceElement referenceElement : statement.getModuleReferences()) {
          if (referenceElement != null) {
            PsiElement moduleElement = addReference(referenceElement.getReference());
            if (packageName != null && moduleElement instanceof PsiJavaModule) {
              List<String> toModuleNames = myExportedPackageNames.get(packageName);
              if (toModuleNames == emptyList) myExportedPackageNames.put(packageName, toModuleNames = new ArrayList<>(1));
              toModuleNames.add(((PsiJavaModule)moduleElement).getName());
            }
          }
        }
      }
      for (PsiProvidesStatement statement : javaModule.getProvides()) {
        final PsiJavaCodeReferenceElement interfaceReference = statement.getInterfaceReference();
        final PsiReferenceList implementationList = statement.getImplementationList();
        if (interfaceReference != null && implementationList != null) {
          final PsiElement providerInterface = interfaceReference.resolve();
          if (providerInterface instanceof PsiClass) {
            final RefElement refInterface = getRefManager().getReference(providerInterface);
            if (refInterface instanceof RefJavaElementImpl) {
              for (PsiJavaCodeReferenceElement implementationReference : implementationList.getReferenceElements()) {
                final PsiElement implementationClass = implementationReference.resolve();
                if (implementationClass instanceof PsiClass) {
                  RefElement refTargetElement = null;
                  PsiElement targetElement = getProviderMethod((PsiClass)implementationClass);

                  if (targetElement == null) {
                    final RefElement refClass = getRefManager().getReference(implementationClass);
                    if (refClass instanceof RefClassImpl) {
                      final RefMethod refConstructor = ((RefClassImpl)refClass).getDefaultConstructor();
                      if (refConstructor != null) {
                        final PsiModifierListOwner constructorElement = refConstructor.getElement();
                        if (constructorElement != null && constructorElement.hasModifierProperty(PsiModifier.PUBLIC)) {
                          refTargetElement = refConstructor;
                          targetElement = constructorElement;
                        }
                      }
                    }
                  }
                  if (targetElement == null) {
                    targetElement = implementationClass;
                  }
                  if (refTargetElement == null) {
                    refTargetElement = getRefManager().getReference(targetElement);
                  }
                  if (refTargetElement != null) {
                    ((RefJavaElementImpl)refInterface)
                      .addReference(refTargetElement, targetElement, providerInterface, false, true, null);
                  }
                }
              }
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

  @NotNull
  private static Map<String, List<String>> getPackagesExportedByModule(@NotNull PsiJavaModule javaModule) {
    Map<String, List<String>> exportedPackages = new THashMap<>();
    for (PsiPackageAccessibilityStatement statement : javaModule.getExports()) {
      String packageName = statement.getPackageName();
      if (packageName != null) {
        exportedPackages.put(packageName, statement.getModuleNames());
      }
    }
    return !exportedPackages.isEmpty() ? exportedPackages : Collections.emptyMap();
  }

  @Nullable
  private static PsiMethod getProviderMethod(@NotNull PsiClass psiClass) {
    final PsiMethod[] methods = psiClass.findMethodsByName("provider", false);
    return ContainerUtil.find(methods, m -> m.hasModifierProperty(PsiModifier.PUBLIC) &&
                                            m.hasModifierProperty(PsiModifier.STATIC) &&
                                            m.getParameterList().getParametersCount() == 0);
  }
}
