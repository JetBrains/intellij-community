// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.reference;

import com.intellij.codeInsight.daemon.impl.JavaServiceUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UDeclaration;
import org.jetbrains.uast.UastContextKt;

import javax.swing.*;
import java.util.*;

public final class RefJavaModuleImpl extends RefElementImpl implements RefJavaModule {
  private final RefModule myRefModule;

  private Map<String, List<String>> myExportedPackageNames;
  private Set<RefClass> myServiceInterfaces;
  private Set<RefClass> myServiceImplementations;
  private Set<RefClass> myUsedServices;
  private List<RequiredModule> myRequiredModules;

  RefJavaModuleImpl(@NotNull PsiJavaModule javaModule, @NotNull RefManagerImpl manager) {
    super(javaModule.getName(), javaModule, manager);
    myRefModule = manager.getRefModule(ModuleUtilCore.findModuleForPsiElement(javaModule));
    JAVA_MODULE.set(myRefModule, this);
  }

  @Override
  protected synchronized void initialize() {
    ((WritableRefEntity)myRefModule).add(this);
  }

  @Override
  public void accept(@NotNull RefVisitor visitor) {
    if (visitor instanceof RefJavaVisitor javaVisitor) {
      ApplicationManager.getApplication().runReadAction(() -> javaVisitor.visitJavaModule(this));
    }
    else {
      super.accept(visitor);
    }
  }

  @Nullable
  @Override
  public PsiJavaModule getPsiElement() {
    return (PsiJavaModule)super.getPsiElement();
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
  public Set<RefClass> getServiceInterfaces() {
    return myServiceInterfaces != null ? myServiceInterfaces : Collections.emptySet();
  }

  @NotNull
  @Override
  public Set<RefClass> getServiceImplementations() {
    return myServiceImplementations != null ? myServiceImplementations : Collections.emptySet();
  }

  @NotNull
  @Override
  public Set<RefClass> getUsedServices() {
    return myUsedServices != null ? myUsedServices : Collections.emptySet();
  }

  @Override
  @NotNull
  public List<RequiredModule> getRequiredModules() {
    return myRequiredModules != null ? myRequiredModules : Collections.emptyList();
  }

  @Override
  public @NotNull Icon getIcon(boolean expanded) {
    return AllIcons.Nodes.JavaModule;
  }

  private void buildRequiresReferences(@NotNull PsiJavaModule javaModule) {
    for (PsiRequiresStatement statement : javaModule.getRequires()) {
      PsiElement element = addReference(statement.getModuleReference());
      if (element instanceof PsiJavaModule requiredModule) {
        Map<String, List<String>> packagesExportedByModule = getPackagesExportedByModule(requiredModule);
        if (myRequiredModules == null) myRequiredModules = new ArrayList<>(1);
        myRequiredModules.add(new RequiredModule(requiredModule.getName(), packagesExportedByModule, statement.hasModifierProperty(PsiModifier.TRANSITIVE)));
      }
    }
  }

  private void buildExportsReferences(@NotNull PsiJavaModule javaModule) {
    List<String> emptyList = Collections.emptyList();
    for (PsiPackageAccessibilityStatement statement : javaModule.getExports()) {
      PsiElement element = addReference(statement.getPackageReference());
      String packageName = null;
      if (element instanceof PsiPackage psiPackage) {
        packageName = psiPackage.getQualifiedName();
        if (myExportedPackageNames == null) myExportedPackageNames = new HashMap<>(1);
        myExportedPackageNames.put(packageName, emptyList);
      }
      for (PsiJavaModuleReferenceElement referenceElement : statement.getModuleReferences()) {
        if (referenceElement != null) {
          PsiElement moduleElement = addReference(referenceElement.getReference());
          if (packageName != null && moduleElement instanceof PsiJavaModule psiJavaModule) {
            List<String> toModuleNames = myExportedPackageNames.get(packageName);
            if (toModuleNames == emptyList) myExportedPackageNames.put(packageName, toModuleNames = new ArrayList<>(1));
            toModuleNames.add(psiJavaModule.getName());
          }
        }
      }
    }
  }

  private void buildProvidesReferences(@NotNull PsiJavaModule javaModule) {
    for (PsiProvidesStatement statement : javaModule.getProvides()) {
      final PsiJavaCodeReferenceElement interfaceReference = statement.getInterfaceReference();
      final PsiReferenceList implementationList = statement.getImplementationList();
      if (interfaceReference != null && implementationList != null) {
        final PsiElement providerInterface = interfaceReference.resolve();
        if (providerInterface instanceof PsiClass) {
          final RefElement refInterface = getRefManager().getReference(providerInterface);
          if (refInterface instanceof RefClassImpl) {
            if (myServiceInterfaces == null) myServiceInterfaces = new HashSet<>();
            myServiceInterfaces.add((RefClass)refInterface);

            for (PsiJavaCodeReferenceElement implementationReference : implementationList.getReferenceElements()) {
              final PsiElement implementationClass = implementationReference.resolve();
              if (implementationClass instanceof PsiClass psiClass) {
                RefElement refTargetElement = null;
                PsiElement targetElement = JavaServiceUtil.findProvider(psiClass);

                if (targetElement == null) {
                  final RefElement refElement = getRefManager().getReference(psiClass);
                  if (refElement instanceof RefClassImpl refClass) {
                    refClass.initializeIfNeeded();
                    if (myServiceImplementations == null) myServiceImplementations = new HashSet<>();
                    myServiceImplementations.add(refClass);

                    final RefMethod refConstructor = refClass.getDefaultConstructor();
                    if (refConstructor != null) {
                      final PsiElement constructorElement = refConstructor.getPsiElement();
                      if (constructorElement instanceof PsiModifierListOwner && ((PsiModifierListOwner)constructorElement).hasModifierProperty(PsiModifier.PUBLIC)) {
                        refTargetElement = refConstructor;
                        targetElement = constructorElement;
                      }
                    }
                  }
                }
                if (targetElement == null) {
                  targetElement = psiClass;
                }
                if (refTargetElement == null) {
                  refTargetElement = getRefManager().getReference(targetElement);
                }
                if (refTargetElement != null) {
                  final UDeclaration element = UastContextKt.toUElement(providerInterface, UDeclaration.class);
                  if (element != null) {
                    ((RefClassImpl)refInterface).addReference(refTargetElement, targetElement, element, false, true, null);
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  private void buildUsesReferences(@NotNull PsiJavaModule javaModule) {
    for (PsiUsesStatement statement : javaModule.getUses()) {
      final PsiJavaCodeReferenceElement reference = statement.getClassReference();
      if (reference != null) {
        final PsiElement usedInterface = reference.resolve();
        if (usedInterface instanceof PsiClass) {
          final RefElement refClass = getRefManager().getReference(usedInterface);
          if (refClass instanceof RefClass) {
            if (myUsedServices == null) {
              myUsedServices = new HashSet<>();
            }
            myUsedServices.add((RefClass)refClass);
          }
        }
      }
    }
  }

  @Override
  public void buildReferences() {
    PsiJavaModule javaModule = getPsiElement();
    if (javaModule != null) {
      buildRequiresReferences(javaModule);
      buildExportsReferences(javaModule);
      buildProvidesReferences(javaModule);
      buildUsesReferences(javaModule);
    }
  }

  /**
   * For building references between modules
   */
  @Nullable
  private PsiElement addReference(@Nullable PsiPolyVariantReference reference) {
    if (reference == null) return null;
    List<PsiElement> resolvedElements = new ArrayList<>();
    ResolveResult[] resolveResults = reference.multiResolve(false);
    for (ResolveResult resolveResult : resolveResults) {
      PsiElement element = resolveResult.getElement();
      if (element != null) {
        resolvedElements.add(element);
        RefElement refElement = getRefManager().getReference(element);
        if (refElement != null) {
          addOutReference(refElement);
          ((WritableRefElement)refElement).addInReference(this);
        }
      }
    }
    return resolvedElements.size() == 1 ? resolvedElements.get(0) : null;
  }

  @Nullable
  public static RefJavaModule moduleFromExternalName(@NotNull RefManagerImpl manager, @NotNull String fqName) {
    Project project = manager.getProject();
    PsiJavaModule javaModule = JavaPsiFacade.getInstance(project).findModule(fqName, GlobalSearchScope.projectScope(project));
    return javaModule == null ? null : (RefJavaModule)manager.getReference(javaModule);
  }

  @NotNull
  private static Map<String, List<String>> getPackagesExportedByModule(@NotNull PsiJavaModule javaModule) {
    Map<String, List<String>> exportedPackages = new HashMap<>();
    for (PsiPackageAccessibilityStatement statement : javaModule.getExports()) {
      String packageName = statement.getPackageName();
      if (packageName != null) {
        exportedPackages.put(packageName, statement.getModuleNames());
      }
    }
    return !exportedPackages.isEmpty() ? exportedPackages : Collections.emptyMap();
  }
}
