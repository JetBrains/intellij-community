// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.unnecessaryModuleDependency;

import com.intellij.codeInspection.reference.RefClass;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefField;
import com.intellij.codeInspection.reference.RefGraphAnnotator;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefMethod;
import com.intellij.codeInspection.reference.RefModule;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UField;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.jetbrains.uast.UTypeReferenceExpression;
import org.jetbrains.uast.UastContextKt;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class UnnecessaryModuleDependencyAnnotator extends RefGraphAnnotator {
  public static final Key<Set<Module>> DEPENDENCIES = Key.create("inspection.dependencies");

  private final RefManager myManager;

  public UnnecessaryModuleDependencyAnnotator(RefManager manager) {
    myManager = manager;
  }

  @Override
  public void onMarkReferenced(PsiElement what, PsiElement from, boolean referencedFromClassInitializer) {
    if (what != null && from != null) {
      //from should be always in sources
      final Module fromModule = ModuleUtilCore.findModuleForFile(from.getContainingFile());
      final Set<Module> onModules = getAllPossibleWhatModules(what, fromModule);
      if (onModules != null && fromModule != null) {
        final RefModule refModule = myManager.getRefModule(fromModule);
        if (refModule != null) {
          HashSet<Module> modules = new HashSet<>(onModules);
          collectRequiredModulesInHierarchy(myManager.getReference(what), fromModule, modules);
          modules.remove(fromModule);
          getModules(refModule).addAll(modules);
        }
      }
    }
  }

  @Override
  public void onMarkReferenced(RefElement refWhat, RefElement refFrom, boolean referencedFromClassInitializer) {
    //case when both from and what are located in the scope, no library dependency expected
    RefModule fromModule = refFrom.getModule();
    RefModule whatModule = refWhat.getModule();
    if (fromModule != null && whatModule != null) {
      Set<Module> currentFromModules = getModules(fromModule);
      currentFromModules.add(whatModule.getModule());
      Set<Module> modules = refWhat.getUserData(DEPENDENCIES);
      if (modules != null) {
        currentFromModules.addAll(modules);
      }
    }
  }

  @Override
  public void onInitialize(RefElement refElement) {
    RefModule refModule = refElement.getModule();
    if (refModule != null) {
      Set<Module> modules = Collections.synchronizedSet(new HashSet<>());
      collectRequiredModulesInHierarchy(refElement, refModule.getModule(), modules);
      modules.remove(refModule.getModule());
      if (!modules.isEmpty()) {
        refElement.putUserData(DEPENDENCIES, modules);
        getModules(refModule).addAll(modules);
      }
    }
  }

  private static void collectRequiredModulesInHierarchy(RefElement refElement, Module currentModule, Set<? super Module> modules) {
    if (refElement instanceof RefClass refClass) {
      processClassHierarchy(null, refClass, currentModule, modules);
    }
    else if (refElement instanceof RefMethod refMethod) {
      UMethod uMethod = refMethod.getUastElement();
      if (uMethod != null) {
        Set<PsiClass> classes = new HashSet<>();
        processTypeHierarchy(classes, uMethod.getReturnType(), currentModule, modules);
        for (UParameter parameter : uMethod.getUastParameters()) {
          processTypeHierarchy(classes, parameter.getType(), currentModule, modules);
        }
        //todo thrown types
      }
    }
    else if (refElement instanceof RefField field) {
      UField element = field.getUastElement();
      UClass aClass = UastContextKt.toUElement(PsiUtil.resolveClassInType(element.getType()), UClass.class);
      if (aClass != null) {
        processClassHierarchy(aClass, null, currentModule, modules);
      }
    }
  }

  private static void processTypeHierarchy(Set<? super PsiClass> classes, PsiType returnType, Module currentModule, Set<? super Module> modules) {
    UClass aClass = UastContextKt.toUElement(PsiUtil.resolveClassInType(returnType), UClass.class);
    if (aClass != null && classes.add(aClass)) {
      processClassHierarchy(aClass, null, currentModule, modules);
    }
  }

  private static void processClassHierarchy(UClass uClass, RefClass refClass, Module currentModule, Set<? super Module> modules) {
    LinkedHashSet<UClass> superClasses = new LinkedHashSet<>();
    if (refClass == null) {
      processSupers(uClass, superClasses);
    }
    else {
      for (RefClass aClass : refClass.getBaseClasses()) {
        UClass superClass = aClass.getUastElement();
        if (superClass != null) {
          superClasses.add(superClass);
        }
      }
    }
    for (PsiClass superClass : superClasses) {
      Set<Module> onModules = getAllPossibleWhatModules(superClass, currentModule);
      if (onModules != null) modules.addAll(onModules);
    }
  }

  private static void processSupers(UClass uClass, LinkedHashSet<UClass> superClasses) {
    for (UTypeReferenceExpression uastSuperType : uClass.getUastSuperTypes()) {
      PsiClass superClass = PsiUtil.resolveClassInType(uastSuperType.getType());
      if (superClass == null || !superClass.getManager().isInProject(superClass)) continue;

      UClass aClass = UastContextKt.toUElement(superClass, UClass.class);
      if (aClass != null && superClasses.add(aClass)) {
        processSupers(aClass, superClasses);
      }
    }
  }

  /**
   * Returns all owner modules for a library or single module set for a source outside the inspecting scope
   */
  private static Set<Module> getAllPossibleWhatModules(@NotNull PsiElement what, Module currentModule) {
    VirtualFile vFile = PsiUtilCore.getVirtualFile(what);
    if (vFile == null) return null;
    Project project = what.getProject();
    final ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
    if (fileIndex.isInLibrary(vFile)) {
      final List<OrderEntry> orderEntries = fileIndex.getOrderEntriesForFile(vFile);
      if (orderEntries.isEmpty()) {
        return null;
      }
      Set<Module> modules = new HashSet<>(orderEntries.size());
      for (OrderEntry orderEntry : orderEntries) {
        if (orderEntry instanceof JdkOrderEntry) return null;
        if (orderEntry instanceof LibraryOrderEntry lib) {
          Module module = lib.getOwnerModule();
          if (module == currentModule) {
            return Collections.singleton(module);
          }
          if (lib.isExported()) {
            modules.add(module);
          }
        }
      }
      return modules;
    }
    Module module = ModuleUtilCore.findModuleForFile(vFile, project);
    return module != null ? Collections.singleton(module) : null;
  }

  private static synchronized Set<Module> getModules(RefModule refModule) {
    Set<Module> modules = refModule.getUserData(DEPENDENCIES);
    if (modules == null) {
      modules = Collections.synchronizedSet(new HashSet<>());
      refModule.putUserData(DEPENDENCIES, modules);
    }
    return modules;
  }
}
