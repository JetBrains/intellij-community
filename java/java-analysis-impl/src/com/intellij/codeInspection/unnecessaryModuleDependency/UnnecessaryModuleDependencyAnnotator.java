package com.intellij.codeInspection.unnecessaryModuleDependency;

import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefGraphAnnotator;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefModule;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class UnnecessaryModuleDependencyAnnotator extends RefGraphAnnotator {
  public static final Key<Set<Module>> DEPENDENCIES = Key.create("inspection.dependencies");

  private final RefManager myManager;

  public UnnecessaryModuleDependencyAnnotator(final RefManager manager) {
    myManager = manager;
  }

  @Override
  public void onMarkReferenced(PsiElement what, PsiElement from, boolean referencedFromClassInitializer) {
    if (what != null && from != null){
      final Module onModule = ModuleUtilCore.findModuleForPsiElement(what);
      final Module fromModule = ModuleUtilCore.findModuleForPsiElement(from);
      if (onModule != null && fromModule != null){
        final RefModule refModule = myManager.getRefModule(fromModule);
        if (refModule != null) {
          HashSet<Module> modules = new HashSet<>();
          modules.add(onModule);
          collectRequiredModulesInHierarchy(what, modules);
          modules.remove(fromModule);
          getModules(refModule).addAll(modules);
        }
      }
    }
  }

  @Override
  public void onMarkReferenced(RefElement refWhat, RefElement refFrom, boolean referencedFromClassInitializer) {
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
    PsiElement element = refElement.getElement();
    RefModule refModule = refElement.getModule();
    if (refModule != null) {
      HashSet<Module> modules = new HashSet<>();
      collectRequiredModulesInHierarchy(element, modules);
      modules.remove(refModule.getModule());
      if (!modules.isEmpty()) {
        refElement.putUserData(DEPENDENCIES, modules);
        getModules(refModule).addAll(modules);
      }
    }
  }

  private static void collectRequiredModulesInHierarchy(PsiElement element, Set<Module> modules) {
    if (element instanceof PsiClass) {
      processClassHierarchy((PsiClass)element, modules);
    }
    else if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element;
      Set<PsiClass> classes = new HashSet<>();
      processTypeHierarchy(classes, method.getReturnType(), modules);
      for (PsiParameter parameter : method.getParameterList().getParameters()) {
        processTypeHierarchy(classes, parameter.getType(), modules);
      }
    }
    else if (element instanceof PsiField) {
      PsiClass aClass = PsiUtil.resolveClassInType(((PsiField)element).getType());
      if (aClass != null) {
        processClassHierarchy(aClass, modules);
      }
    }
  }

  private static void processTypeHierarchy(Set<PsiClass> classes, PsiType returnType, Set<Module> modules) {
    PsiClass aClass = PsiUtil.resolveClassInType(returnType);
    if (aClass != null && classes.add(aClass)) {
      processClassHierarchy(aClass, modules);
    }
  }

  private static void processClassHierarchy(PsiClass currentClass, Set<Module> modules) {
    LinkedHashSet<PsiClass> superClasses = new LinkedHashSet<>();
    InheritanceUtil.getSuperClasses(currentClass, superClasses, false);
    for (PsiClass superClass : superClasses) {
      ContainerUtil.addIfNotNull(modules, ModuleUtilCore.findModuleForPsiElement(superClass));
    }
  }

  private static Set<Module> getModules(RefModule refModule) {
    Set<Module> modules = refModule.getUserData(DEPENDENCIES);
    if (modules == null){
      modules = new HashSet<>();
      refModule.putUserData(DEPENDENCIES, modules);
    }
    return modules;
  }
}
